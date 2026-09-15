# 🌍 TerraDB

**TerraDB** is an embedded, disk-backed storage engine built entirely from first principles in Java.

It is designed to demonstrate the internal mechanics of modern database systems, including paged storage, buffer management, B+ tree indexing, Write-Ahead Logging (WAL), and ARIES-style crash recovery concepts.

## ✨ Features

- **Paged Storage Manager:** 4096-byte pages with deterministic binary layouts (no Java serialization), CRC32 checksums, and a free-list allocator.
- **LRU Buffer Pool:** Memory cache with pin/unpin semantics, dirty page tracking, and LRU eviction.
- **Page-Resident B+ Tree:** Supports insert, delete, point lookups, and linked-leaf range scans. Handles node splits, merges, and redistribution.
- **Write-Ahead Logging (WAL):** Physical redo logging with monotonic LSNs and group commit.
- **Crash Recovery:** Strict WAL-before-data ordering (fsync before page flush). Validated against 6 distinct process-kill crash injection points.
- **Checkpointing:** Periodic snapshots to minimize WAL scan times during recovery.

## 🏗️ Architecture

```text
              Application / CLI
                     │
                     ▼
              Storage API (TerraDB)
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
      B+ Tree               WAL Manager
          │                     │
          ▼                     ▼
      Buffer Pool          WAL File (.wal)


## 📊 Benchmark Results
*(Run on local machine. 4096-byte pages, Buffer Pool = 100 frames, Strict WAL fsync per operation)*

| Metric | Result |
| :--- | :--- |
| **Sequential Inserts** | ~3,014 ops/sec |
| **Random Lookups** | ~5,668 ops/sec |
| **Range Scans** | ~324 scans/sec (100 keys each) |
| **Crash Recovery Time** | ~1,724 ms (Replayed 1,000 WAL records) |

*Note: Insert throughput is bound by disk IOPS due to strict `fsync` durability guarantees on every commit. Group commit batching would significantly increase this in a production system.*
          │
          ▼
      Disk Manager
          │
          ▼
      Database File (.db)
