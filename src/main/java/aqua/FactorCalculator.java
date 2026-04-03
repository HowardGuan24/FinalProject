package aqua;
public class FactorCalculator {

    private static final int N = 5;
    private static final double EPSILON = 1e-7;

    public static String calculateAll(Snapshot curr, Snapshot prev) {
        double[] factors = new double[20];

        // --- 1. 将 long 转为 double 进行计算 ---
        double bp1 = (double) curr.bp[0];
        double ap1 = (double) curr.ap[0];
        double bv1 = (double) curr.bv[0];
        double av1 = (double) curr.av[0];
        // 因子3: 中间价
        double midPrice = (ap1 + bp1) / 2.0;

        // 因子 1: 最优价差
        factors[0] = ap1 - bp1;

        // 因子 2: 相对价差
        factors[1] = (ap1 - bp1) / (midPrice + EPSILON);

        // 因子 3: 买卖一档均价
        factors[2] = midPrice;

        // 因子 4: 买一不平衡
        factors[3] = (bv1 - av1) / (bv1 + av1 + EPSILON);

        // --- 准备累加变量 ---
        double sumBvN = 0;
        double sumAvN = 0;
        double sumBpBvN = 0;
        double sumApAvN = 0;

        for (int i = 0; i < N; i++) {
            // 注意：这里取出的 array 元素是 long，需强转
            double currBv = (double) curr.bv[i];
            double currAv = (double) curr.av[i];
            double currBp = (double) curr.bp[i];
            double currAp = (double) curr.ap[i];

            sumBvN += currBv;
            sumAvN += currAv;
            sumBpBvN += currBp * currBv;
            sumApAvN += currAp * currAv;
        }

        // 因子 5: 多档不平衡
        factors[4] = (sumBvN - sumAvN) / (sumBvN + sumAvN + EPSILON);
        // 因子 6: 买方深度
        factors[5] = sumBvN;
        // 因子 7: 卖方深度
        factors[6] = sumAvN;
        // 因子 8: 深度差
        factors[7] = sumBvN - sumAvN;
        // 因子 9: 深度比
        factors[8] = sumBvN / (sumAvN + EPSILON);

        // 因子 10: 全市场买卖量平衡 (使用 long 字段 tBidVol)
        double tBid = (double) curr.tBidVol;
        double tAsk = (double) curr.tAskVol;
        factors[9] = (tBid - tAsk) / (tBid + tAsk + EPSILON);

        // 因子 11 - 14
        factors[10] = sumBpBvN / (sumBvN + EPSILON);
        factors[11] = sumApAvN / (sumAvN + EPSILON);
        factors[12] = (sumBpBvN + sumApAvN) / (sumBvN + sumAvN + EPSILON);
        factors[13] = factors[11] - factors[10];

        // 因子 15: 买卖密度差
        factors[14] = (sumBvN / N) - (sumAvN / N);

        // 因子 16: 买卖不对称度
        double sumBvDecay = 0;
        double sumAvDecay = 0;
        for (int i = 0; i < N; i++) {
            sumBvDecay += (double)curr.bv[i] / (i + 1);
            sumAvDecay += (double)curr.av[i] / (i + 1);
        }
        factors[15] = (sumBvDecay - sumAvDecay) / (sumBvDecay + sumAvDecay + EPSILON);

        // --- 因子 17-19 (需要 prevSnapshot) ---
        if (prev != null) {
            double prevAp1 = (double) prev.ap[0];
            double prevBp1 = (double) prev.bp[0];
            double prevMid = (prevAp1 + prevBp1) / 2.0;

            // 因子 17: 最优报价变化幅度
            factors[16] = ap1 - prevAp1;

            // 因子 18: 中间价变化
            factors[17] = midPrice - prevMid;

            // 因子 19: 深度比变化率
            double prevSumBvN = 0;
            double prevSumAvN = 0;
            for(int i=0; i<N; i++){
                prevSumBvN += (double)prev.bv[i];
                prevSumAvN += (double)prev.av[i];
            }
            double currRatio = sumBvN / (sumAvN + EPSILON);
            double prevRatio = prevSumBvN / (prevSumAvN + EPSILON);
            factors[18] = currRatio - prevRatio;
        } else {
            // 如果没有前一时刻数据 (且不是通过Mapper过滤掉的情况)，填0
            factors[16] = 0; factors[17] = 0; factors[18] = 0;
        }

        // 因子 20
        factors[19] = (ap1 - bp1) / (sumBvN + sumAvN + EPSILON);

        // 拼接返回
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < factors.length; i++) {
            sb.append(factors[i]);
            if (i < factors.length - 1) sb.append(",");
        }
        return sb.toString();
    }
}