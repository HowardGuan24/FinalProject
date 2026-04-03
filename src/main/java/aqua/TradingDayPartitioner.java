package aqua;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;

public class TradingDayPartitioner extends Partitioner<Text, Text> {

    @Override
    public int getPartition(Text key, Text value, int numPartitions) {
        if (numPartitions <= 1) return 0;

        String s = key.toString();
        int comma = s.indexOf(',');
        int end = (comma >= 0) ? comma : s.length();

        // 解析 YYYYMMDD（8 位数字）
        int day = 0;
        for (int i = 0; i < end; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            day = day * 10 + (c - '0');
        }

        // 直接取模分区（稳定，不依赖 hashCode）
        return (day & Integer.MAX_VALUE) % numPartitions;
    }
}