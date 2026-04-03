package aqua;

public class Snapshot {
    // 原始数据均使用 long (Int64) 存储，避免精度丢失
    public long tradeTime;
    public long tradingDay;
    public long[] bp = new long[5];
    public long[] bv = new long[5];
    public long[] ap = new long[5];
    public long[] av = new long[5];

    public long tBidVol;
    public long tAskVol;
    public long wBidPrc;
    public long wAskPrc;

    /**
     * 直接解析原始 CSV 行数据
     * 对应 Sample: 20240102,092500,...,tBidVol,tAskVol,wBidPrc,wAskPrc,openInterest,bp1,bv1,ap1,av1...
     */
    public Snapshot(String csvLine) {
        String[] fields = csvLine.split(",");

        // 1. 解析 tradeTime (Index 1)
        // 原始数据: 092500 -> 92500
        this.tradingDay = Long.parseLong(fields[0]);
        this.tradeTime = Long.parseLong(fields[1]);

        // 2. 解析全市场数据
        // Index 12: tBidVol
        this.tBidVol = Long.parseLong(fields[12]);
        // Index 13: tAskVol
        this.tAskVol = Long.parseLong(fields[13]);
        // Index 14: wBidPrc
        this.wBidPrc = Long.parseLong(fields[14]);
        // Index 15: wAskPrc
        this.wAskPrc = Long.parseLong(fields[15]);

        // 3. 解析 LOB 数据 (前5档)
        // 原始数据从 Index 17 开始: bp1, bv1, ap1, av1, bp2...
        // 规律: base = 17 + (i * 4)
        int startIdx = 17;
        for (int i = 0; i < 5; i++) {
            int base = startIdx + i * 4;
            this.bp[i] = Long.parseLong(fields[base]);     // bp
            this.bv[i] = Long.parseLong(fields[base + 1]); // bv
            this.ap[i] = Long.parseLong(fields[base + 2]); // ap
            this.av[i] = Long.parseLong(fields[base + 3]); // av
            }
        }
    }
