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
          │
          ▼
      Disk Manager
          │
          ▼
      Database File (.db)