package aqua;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.lib.input.CombineTextInputFormat;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.LazyOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class CalcDriver {
    private static void autoTune(Configuration conf) {
        int cores = Runtime.getRuntime().availableProcessors();

        // JVM 可用最大内存（由 -Xmx 或容器限制决定），单位 MB
        long maxMemMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        // ===== 并发：本地模式并发上限 =====
        int mapMax = Math.max(1, cores);
        int reduceMax = Math.max(1, cores / 2);

        // ===== sort buffer：按内存分档 =====
        int sortMb;
        if (maxMemMb <= 3072)      sortMb = 96;   // <= 3GB
        else if (maxMemMb <= 6144) sortMb = 128;  // <= 6GB
        else if (maxMemMb <= 12288) sortMb = 256; // <= 12GB
        else                       sortMb = 384;  // 更大内存

        // ===== Combine split：按核数分档 =====
        long maxSplitMb = (cores >= 8) ? 128 : 64;
        long minSplitMb = maxSplitMb / 2;

        // ===== reducers：按核数和天数上限，你这里先默认 <= cores =====
        int defaultReducers = Math.max(1, Math.min(cores, conf.getInt("aqua.reducers", cores)));

        // 允许用户用 -D 显式覆盖（只在未设置时写入默认值）
        conf.setInt("mapreduce.local.map.tasks.maximum",
                conf.getInt("mapreduce.local.map.tasks.maximum", mapMax));
        conf.setInt("mapreduce.local.reduce.tasks.maximum",
                conf.getInt("mapreduce.local.reduce.tasks.maximum", reduceMax));

        conf.setInt("mapreduce.task.io.sort.mb",
                conf.getInt("mapreduce.task.io.sort.mb", sortMb));
        conf.setInt("mapreduce.task.io.sort.factor",
                conf.getInt("mapreduce.task.io.sort.factor", 100));

        conf.setLong("aqua.combine.max.mb",
                conf.getLong("aqua.combine.max.mb", maxSplitMb));
        conf.setLong("aqua.combine.min.mb",
                conf.getLong("aqua.combine.min.mb", minSplitMb));

        conf.setInt("aqua.reducers", defaultReducers);
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        autoTune(conf);
        conf.setBoolean("mapreduce.input.fileinputformat.input.dir.recursive", true);

//        // ====== 保持你原来的优化配置不变 ======
//        int cores = Runtime.getRuntime().availableProcessors();
//        conf.setInt("mapreduce.local.map.tasks.maximum", cores);
//        conf.setInt("mapreduce.local.reduce.tasks.maximum", Math.max(1, cores / 2));
//
//        conf.setBoolean("mapreduce.map.output.compress", true);
//        conf.set("mapreduce.map.output.compress.codec", "org.apache.hadoop.io.compress.SnappyCodec");
//        conf.setInt("mapreduce.task.io.sort.mb", 256);
//        conf.setInt("mapreduce.task.io.sort.factor", 100);

        Job job = Job.getInstance(conf, "Stock Factor Calculation");
        job.setJarByClass(CalcDriver.class);

        job.setMapperClass(FactorMapper.class);
        job.setReducerClass(FactorReducer.class);

        job.setPartitionerClass(TradingDayPartitioner.class);
        job.setNumReduceTasks(conf.getInt("aqua.reducers", 1));

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        job.setInputFormatClass(CombineTextInputFormat.class);
//        CombineTextInputFormat.setMaxInputSplitSize(job, 64L * 1024 * 1024);
//        CombineTextInputFormat.setMinInputSplitSize(job, 32L * 1024 * 1024);
        CombineTextInputFormat.setMaxInputSplitSize(job, conf.getLong("aqua.combine.max.mb", 64) * 1024L * 1024L);
        CombineTextInputFormat.setMinInputSplitSize(job, conf.getLong("aqua.combine.min.mb", 32) * 1024L * 1024L);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        Path outputPath = new Path(args[1]);
        FileOutputFormat.setOutputPath(job, outputPath);

        LazyOutputFormat.setOutputFormatClass(job, TextOutputFormat.class);

        // 1. 执行任务
        boolean result = job.waitForCompletion(true);

        // 2. ★ 新增逻辑：任务成功后立即在 Java 中处理文件名 ★
        if (result) {
            FileSystem fs = FileSystem.get(conf);
            // 遍历输出目录下的所有文件
            FileStatus[] statuses = fs.listStatus(outputPath);
            for (FileStatus status : statuses) {
                String fileName = status.getPath().getName();

                // 如果文件名包含 .csv-r- (MultipleOutputs 生成的带编号文件名)
                if (fileName.contains(".csv-r-")) {
                    // 按照 "-r-" 分割，只取前半部分 (即 0102.csv)
                    String newName = fileName.split("-r-")[0];
                    Path oldPath = status.getPath();
                    Path newPath = new Path(outputPath, newName);

                    // 重命名：将 0102.csv-r-00000 改为 0102.csv
                    if (!fs.exists(newPath)) {
                        fs.rename(oldPath, newPath);
                    }
                }
                // 可选：删除掉产生的 _SUCCESS 文件，让目录只剩下 .csv
                else if (fileName.startsWith("_SUCCESS")) {
                    fs.delete(status.getPath(), false);
                }
            }
        }

        System.exit(result ? 0 : 1);
    }
}