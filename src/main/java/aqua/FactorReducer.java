package aqua;

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs; // 引入 MultipleOutputs

import java.io.IOException;

public class FactorReducer extends Reducer<Text, Text, NullWritable, Text> {

//    private String lastDay = null;
    // 定义多路输出对象
    private MultipleOutputs<NullWritable, Text> mos;
    // 记录上一次处理的日期，用于判断是否需要写表头
    private String currentDay = "";

    // 定义表头内容 (不包含 tradingDay)
    private static final String HEADER;
    static {
        StringBuilder sb = new StringBuilder("tradeTime"); // 第一列改为 tradeTime
        for (int i = 1; i <= 20; i++) {
            sb.append(",alpha_").append(i);
        }
        HEADER = sb.toString();
    }

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        // 初始化多路输出
        mos = new MultipleOutputs<>(context);
        // 注意：这里不再直接 context.write 表头，因为我们要把表头写到具体的子文件夹里
    }

// ... 前面代码保持不变 ...

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        // Key 格式: "20240102,093000"
        String keyStr = key.toString();
        String[] keyParts = keyStr.split(",");
        String day = keyParts[0];      // 20240102
        String time = keyParts[1];     // 093000

        // 提取 MMDD 格式 (例如 0102)
        // 假设 day 总是 YYYYMMDD 格式
        String fileName = day.length() >= 8 ? day.substring(4) : day;
        String outputBase = fileName + ".csv"; // 目标文件名：0102.csv


        // --- 1. 动态写表头逻辑 ---
        if (!day.equals(currentDay)) {
            // 直接输出到 0102.csv-r-00000 这样的文件中
            mos.write(NullWritable.get(), new Text(HEADER), outputBase);
            currentDay = day;
        }

        // --- 2. 计算均值 (保持不变) ---
        double[] sumFactors = new double[20];
        int count = 0;
        for (Text val : values) {
            // 替代：String[] parts = val.toString().split(",");
            String s = val.toString();
            int idx = 0;
            int col = 0;
            int start = 0;

            while (col < 20) {
                idx = s.indexOf(',', start);
                int end = (idx == -1) ? s.length() : idx;
                if (start < end) {
                    try {
                        sumFactors[col] += Double.parseDouble(s.substring(start, end));
                    } catch (NumberFormatException ignore) {}
                }
                col++;
                if (idx == -1) break;
                start = idx + 1;
            }
            if (col == 20) count++;

        }

        // --- 3. 拼接结果 ---
        StringBuilder sb = new StringBuilder();
        sb.append(time);
        for (int i = 0; i < 20; i++) {
            double avg = (count > 0) ? (sumFactors[i] / count) : 0.0;
            sb.append(",").append(String.format("%.6f", avg));
        }

        // --- 4. 修改输出位置 ---
        // 移除 day + "/part"，改为使用 outputBase
        // 结果路径: baseOutput/0102.csv-r-00000
        mos.write(NullWritable.get(), new Text(sb.toString()), outputBase);
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        // 务必关闭 mos，否则数据可能丢失
        mos.close();
    }
}