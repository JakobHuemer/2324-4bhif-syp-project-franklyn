---
title: Profiling Metrics Analysis - 100 Clients with Patrol Mode
weight: 101
---

## Problem

With 100 connected clients and 4-5 instructor clients viewing videos in patrol mode,
the screenshot pipeline shows significant queue buildup and latency spikes.

## Test Configuration

- **Connected Clients**: 100
- **Active Requests**: 15 (max-concurrent-requests limit)
- **Instructor Clients**: 4-5 viewing patrol mode simultaneously
- **Queue Size**: 85 clients waiting (85% of clients queued)
- **CPU Utilization**: 12.1%

## Key Metrics Observed

### Screenshot Pipeline Status

| Metric | Value | Analysis |
|--------|-------|----------|
| Queue Size | **85** | 85% of clients waiting in queue - severe backlog |
| Active Requests | **15** | At configured maximum limit |
| Connected Clients | **100** | Full test load |
| Uploads/sec | **25.3** | Actual throughput achieved |
| Timeouts (total) | **0** | No timeouts - system processing all requests |
| Uploads (total) | **9045** | ~90 uploads per client on average |

### Operation Latencies (milliseconds)

| Operation | p50 | p90 | p99 | Max | Count |
|-----------|-----|-----|-----|-----|-------|
| Screenshot Request (end-to-end) | 494.9 | 696.3 | 1.03s | 1.61s | 9045 |
| Image Decode (ImageIO.read) | 35.7 | 37.7 | 41.9 | 52.7 | 9045 |
| Image Encode (ImageIO.write) | 27.3 | 29.4 | 31.5 | 41.7 | 9040 |
| Beta Frame Merge | 1.1 | 1.5 | 2.6 | 3.9 | 8541 |
| Image File Read (disk) | 8.9 | 15.2 | 17.3 | 23.9 | 8541 |
| Image Save Total | 283.1 | 601.9 | 1.14s | 1.74s | 9040 |
| WebSocket Message Send | 79.7 | 184.5 | 268.4 | 381.1 | 9057 |
| WebSocket Ping Latency | 226.5 | 637.5 | 1.48s | 2.23s | 7751 |
| Image Download | 209.6 | 536.7 | 671.0 | 654.5 | 136 |

### Processing Time Breakdown (p50 per screenshot)

Based on the stacked bar chart, the breakdown of time per screenshot is:
- **Decode**: ~35ms (blue)
- **Merge**: ~1ms (yellow)
- **Encode**: ~27ms (red)
- **File Read**: ~9ms (cyan)

**Total measured image operations**: ~72ms

## Analysis

### The Math: Where Does the Time Go?

| Component | Time (p50) | Percentage |
|-----------|------------|------------|
| Image Decode | 35.7ms | 12.6% |
| Image Encode | 27.3ms | 9.6% |
| Beta Frame Merge | 1.1ms | 0.4% |
| Image File Read | 8.9ms | 3.1% |
| **Sum of measured ops** | **73.0ms** | **25.8%** |
| **Unaccounted time** | **~210ms** | **74.2%** |
| **Image Save Total** | **283.1ms** | **100%** |

**~74% of the save pipeline time is NOT spent on image processing.**

This unaccounted time includes:
- Database operations (persisting Image entity metadata)
- Reactive pipeline overhead (Uni/Mutiny scheduling)
- Directory existence checks
- Thread context switching
- I/O wait times between operations

### Primary Bottleneck: Database and Reactive Overhead

The **Image Save Total** operation takes **283.1ms p50**, but only **73ms** is
spent on actual image processing. The remaining **~210ms** is spent on:

1. **Database persistence** - Each upload persists an Image entity
2. **Reactive framework overhead** - Mutiny/Uni chain scheduling
3. **Participation lookup** - `findByIdWithExam()` query per upload
4. **File system checks** - Directory creation verification

### Secondary Bottleneck: Patrol Mode Downloads

When patrol mode is activated (visible in timeline around 40:00-41:00):
- **136 image downloads** during the test
- **p50: 209.6ms** per download
- **p90: 536.7ms** per download
- **p99: 671.0ms** per download

The timeline chart shows Image Download (cyan) spiking from ~100ms to ~200ms+
when patrol mode was activated, then stabilizing at elevated levels.

### WebSocket Degradation Under Load

WebSocket metrics show significant stress:
- **Ping Latency p50: 226.5ms** - Event loop processing delays
- **Ping Latency p99: 1.48s** - Severe tail latency
- **Message Send p50: 79.7ms** - Elevated send times

This indicates the Vert.x event loop is spending significant time on I/O
operations, delaying WebSocket message processing.

### Queue Saturation Analysis

With the current configuration:
- **Max concurrent requests**: 15
- **Throughput**: 25.3 uploads/sec
- **Required for 100 clients @ 5s interval**: 20 uploads/sec
- **Actual capacity utilization**: 25.3/20 = **126%** (headroom exists)

However, the queue remains at 85 because:
1. Each request takes ~283ms to complete the save pipeline
2. 15 concurrent × 283ms = ~4.2 seconds to cycle through all active slots
3. 85 clients waiting × (5s interval / 25.3 throughput) = ~17s queue wait

### Timeline Observations

The Request Pipeline Timeline shows:
1. **Screenshot Request (red ~500ms)**: Stable throughout test
2. **Image Decode (blue ~35ms)**: Stable, low variance
3. **Image Encode (yellow ~27ms)**: Stable, low variance
4. **Image Download (cyan)**: Sharp increase when patrol mode activated (~40:00), 
   rising from ~100ms to ~200ms and stabilizing

The stability of decode/encode times confirms image processing is NOT the bottleneck.

## Root Cause Summary

| Factor | Impact | Evidence |
|--------|--------|----------|
| Database/Reactive overhead | **HIGH** | 210ms of 283ms save time unaccounted |
| Patrol mode downloads | **MEDIUM** | 136 downloads @ 210ms competing for I/O |
| Image encoding | **LOW** | Only 27ms p50 |
| Image decoding | **LOW** | Only 36ms p50 |
| max-concurrent-requests=15 | **MEDIUM** | Creates 85-client queue backlog |

## Conclusions

1. **Image encoding/decoding is NOT the primary bottleneck** - Combined they take
   only 63ms (22% of total save time).

2. **~74% of save time is database/framework overhead** - The 210ms gap between
   measured image operations and total save time points to database persistence
   and reactive pipeline scheduling as the main culprits.

3. **Patrol mode adds measurable load** - Image downloads spike when instructors
   open patrol mode, adding I/O contention.

4. **The system handles 100 clients** - With 25.3 uploads/sec throughput and
   zero timeouts, the system is functional but operating at capacity with a
   saturated queue.

5. **CPU is not the bottleneck** - At 12.1% utilization, the server has ample
   CPU headroom. The bottleneck is I/O and database operations.

## Recommendations

### Immediate Optimizations

1. **Increase max-concurrent-requests** from 15 to 25-30 to reduce queue depth
2. **Batch database writes** - Persist image metadata asynchronously
3. **Add download caching** - Cache recent images in memory for patrol mode

### Architecture Changes

1. **Async metadata persistence** - Write image files immediately, persist 
   metadata in background
2. **WebSocket-based patrol** - Push updates instead of polling
3. **Connection pooling review** - Ensure database connections aren't a bottleneck

### Further Investigation Needed

1. Add timing around `participationRepository.findByIdWithExam()`
2. Add timing around `imageRepository.persist()`
3. Profile Mutiny/Uni scheduling overhead
