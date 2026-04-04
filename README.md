# HFT Snapshot Quantitative Factor Calculation via MapReduce

This project is based on Hadoop MapReduce to calculate 20 quantitative factors from LOB (Limit Order Book) snapshot data across multiple trading days and stocks. It performs cross-sectional mean aggregation at the same timestamp for each trading day and outputs results as CSV files split by date.

**Corresponding Course Report:** `dfpsProject.pdf`

## 1. Project Objectives

The input consists of stock snapshot CSV files for multiple trading days. Each snapshot record corresponds to a specific timestamp and contains the trading day, timestamp, market-wide cumulative volume/price, and the top 5 levels of quote data (Level 2).

**Requirements:**
- Calculate 20 LOB factors (`alpha_1` ~ `alpha_20`) for each snapshot.
- Perform a cross-sectional mean of the factor vectors from different stocks at the same trading day and timestamp (e.g., `20240102,093000`).
- Output CSV files with the header `tradeTime,alpha_1,...,alpha_20`, with values rounded to 6 decimal places.

## 2. Data Scale and Output Organization

Experimental scale from the report:
- 5 trading days.
- 60+ stocks per day.
- One CSV per stock, approximately 4,900 rows × 30 columns.
- Execution Environment: Hadoop Local/Pseudo-distributed mode within Docker.

**Output Organization:**
- Use `MultipleOutputs` to split files by trading day.
- Output filenames follow the pattern `MMDD.csv` (e.g., `0102.csv`).
- Each file writes the CSV header only once.

## 3. Technical Solution (One-stage Aggregation)

**Data Flow:**
1. **Mapper:** Parses snapshots and calculates the 20 factors.
2. **Aggregation:** Uses `tradingDay,HHMMSS` as the key to group records at the same timestamp.
3. **Partitioner:** Partitions by trading day to ensure all data for a specific day reaches the same Reducer.
4. **Reducer:** Aggregates the mean values and writes out the CSV partitioned by date.

**Key Implementation Details:**
- **Time Filtering:** Records where `tradeTime < 092957` are discarded; `tradeTime == 092957` is used only to initialize the previous snapshot; output begins only when `tradeTime > 092957`.
- **Numerical Stability:** A small `epsilon=1e-7` is added to denominators to prevent `NaN/Inf` errors.
- **Multi-level Calculation:** Factors are calculated using the top `N=5` levels of the order book.

## 4. Identified Performance Bottlenecks & Optimizations

1. **Small File Problem:** Too many small files result in excessive Mapper overhead.
   - **Optimization:** Use `CombineTextInputFormat` to merge splits, reducing scheduling and startup latency.
2. **Shuffle/Sort IO:** Frequent spill/merge operations cause significant local IO amplification.
   - **Optimization:** Implement a Combiner (where applicable) to reduce the volume of data entering the Reduce phase. The report shows a significant decrease in `Reduce shuffle bytes` after optimization.
3. **Header Conflicts:** Writing headers in a multi-reducer environment.
   - **Optimization:** Combine `TradingDayPartitioner` with `MultipleOutputs` to maintain formatting requirements while preserving parallelism.
4. **GC/CPU Pressure:** High frequency of `split(",")` and object creation.
   - **Optimization:** Use lightweight parsing and buffer reuse to minimize object allocation.

## 5. Code Structure

- **`src/main/java/aqua/CalcDriver.java`**
  - Entry point. Handles configuration and `autoTune`.
  - Configures `CombineTextInputFormat`, `Partitioner`, and `Mapper/Reducer`.
  - Post-processing: Renames `0102.csv-r-00000` to `0102.csv` and cleans up `_SUCCESS` files.
- **`src/main/java/aqua/FactorMapper.java`**
  - Parses input lines and applies time filtering.
  - Calls `FactorCalculator` to compute factors.
  - Outputs `key = tradingDay,HHMMSS`, `value = 20-factor CSV string`.
- **`src/main/java/aqua/FactorCalculator.java`**
  - Core logic for factor calculation.
  - Constants: `N=5`, `EPSILON=1e-7`.
  - Calculates static and incremental factors using current and previous snapshots.
- **`src/main/java/aqua/Snapshot.java`**
  - Data model for snapshot fields (tradingDay, tradeTime, market fields, and 5-level `bp/bv/ap/av`).
- **`src/main/java/aqua/TradingDayPartitioner.java`**
  - Extracts `YYYYMMDD` from the key and partitions based on `day % numPartitions`.
- **`src/main/java/aqua/FactorReducer.java`**
  - Calculates the mean for factor vectors with the same key.
  - Writes results and headers to `MMDD.csv` using `MultipleOutputs`.

## 6. Build and Execution

### 6.1 Build

```bash
mvn -DskipTests clean package
```
The artifact will be generated at: `target/FinalProject-1.0-SNAPSHOT-job.jar`

### 6.2 Running the Job

```bash
hadoop jar target/FinalProject-1.0-SNAPSHOT-job.jar aqua.CalcDriver <input_path> <output_path>
```

**Optional Parameters (`-D` to override):**
- `aqua.reducers`
- `aqua.combine.max.mb`
- `aqua.combine.min.mb`
- `mapreduce.local.map.tasks.maximum`
- `mapreduce.local.reduce.tasks.maximum`
- `mapreduce.task.io.sort.mb`
- `mapreduce.task.io.sort.factor`

**Example:**
```bash
hadoop jar target/FinalProject-1.0-SNAPSHOT-job.jar aqua.CalcDriver \
  -Daqua.reducers=4 \
  -Daqua.combine.max.mb=64 \
  -Daqua.combine.min.mb=32 \
  /path/to/input /path/to/output
```

## 7. Output Format

One file per day (e.g., `0102.csv`). Example content:
```csv
tradeTime,alpha_1,alpha_2,...,alpha_20
093000,0.123456,-0.002341,...,1.234567
093001,0.124001,-0.002120,...,1.228800
```

## 8. Evaluation Suggestions (Aligned with Report)

It is recommended to monitor the following Hadoop Counters to identify bottlenecks:
- `Reduce shuffle bytes`
- `FILE: Number of bytes read/written`
- `Spilled Records`
- `Merged Map outputs`
- `Combine input/output records`

Use these metrics to determine if the bottleneck is Calculation, GC, IO, or Shuffle, and then apply targeted optimizations.