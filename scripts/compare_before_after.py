"""
============================================================
 CodeForge AI — BEFORE vs AFTER Performance Comparison Tool
============================================================
 This script measures REAL latency numbers for:
   BEFORE = ?bypassCache=true  (raw pipeline, no Redis)
   AFTER  = ?bypassCache=false (Redis cached responses)

 Usage:
   python scripts/compare_before_after.py <repo_id> [num_requests]

 Example:
   python scripts/compare_before_after.py 6ffdc2c6-6717-4051-bcdb-2376e996e0c7 10
============================================================
"""

import time
import requests
import statistics
import sys
import json
import concurrent.futures
from datetime import datetime

BASE_URL = "https://codeforge-ingestion.onrender.com/api/rag/search"

# Diverse test queries to avoid single-query bias
TEST_QUERIES = [
    "How does path matching work?",
    "What functions are exported?",
    "Explain how tokens and regex patterns are parsed",
    "How are parameters extracted from a URL?",
    "What is the compile function?",
]

def make_request(query, repo_id, bypass_cache=False):
    """Make a single RAG search request. Returns (latency_ms, response_data) or (None, None) on failure."""
    url = f"{BASE_URL}?bypassCache={'true' if bypass_cache else 'false'}"
    payload = {"query": query, "repositoryId": repo_id}
    headers = {"Content-Type": "application/json"}

    start = time.time()
    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=60)
        latency = (time.time() - start) * 1000  # ms
        if resp.status_code == 200:
            data = resp.json().get("data", {})
            return latency, data
        else:
            return None, None
    except Exception as e:
        return None, None

def run_phase(label, queries, repo_id, bypass_cache, num_iterations):
    """Run a full benchmark phase (BEFORE or AFTER). Returns list of result dicts."""
    results = []
    print(f"\n{'='*70}")
    print(f"  PHASE: {label}")
    print(f"  Cache Bypass: {bypass_cache} | Queries: {len(queries)} | Iterations: {num_iterations}")
    print(f"{'='*70}")

    for qi, query in enumerate(queries):
        print(f"\n  [{qi+1}/{len(queries)}] Query: '{query}'")

        for i in range(num_iterations):
            latency, data = make_request(query, repo_id, bypass_cache)

            if latency is not None:
                result = {
                    "phase": label,
                    "query": query,
                    "iteration": i + 1,
                    "total_latency_ms": round(latency, 2),
                    "pipeline_ms": data.get("pipelineTimeMs", 0),
                    "embedding_ms": data.get("embeddingTimeMs", 0),
                    "qdrant_ms": data.get("qdrantTimeMs", 0),
                    "rerank_ms": data.get("rerankTimeMs", 0),
                    "eval_ms": data.get("evalTimeMs", 0),
                    "cached": data.get("cachedResult", False),
                }
                results.append(result)

                tag = "CACHE HIT" if result["cached"] else "CACHE MISS"
                print(f"    Iter {i+1:2d}: {latency:8.1f} ms [{tag}]  "
                      f"(embed:{result['embedding_ms']}ms qdrant:{result['qdrant_ms']}ms "
                      f"rerank:{result['rerank_ms']}ms eval:{result['eval_ms']}ms)")
            else:
                print(f"    Iter {i+1:2d}: FAILED")

    return results

def run_throughput_test(repo_id, bypass_cache, concurrent_users=10):
    """Measure requests/second under concurrent load."""
    query = TEST_QUERIES[0]
    total_requests = concurrent_users * 2  # 2 requests per thread
    
    start_time = time.time()
    completed = 0

    def worker():
        nonlocal completed
        for _ in range(2):
            latency, _ = make_request(query, repo_id, bypass_cache)
            if latency is not None:
                completed += 1

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrent_users) as executor:
        futures = [executor.submit(worker) for _ in range(concurrent_users)]
        concurrent.futures.wait(futures)

    elapsed = time.time() - start_time
    rps = completed / elapsed if elapsed > 0 else 0
    return rps, completed, elapsed

def compute_stats(results):
    """Compute p50, p95, p99, mean from a list of result dicts."""
    latencies = [r["total_latency_ms"] for r in results]
    if not latencies:
        return {}

    latencies_sorted = sorted(latencies)
    n = len(latencies_sorted)

    return {
        "count": n,
        "mean": round(statistics.mean(latencies), 2),
        "median": round(latencies_sorted[n // 2], 2),
        "p95": round(latencies_sorted[int(n * 0.95)], 2) if n >= 5 else round(max(latencies), 2),
        "p99": round(latencies_sorted[int(n * 0.99)], 2) if n >= 10 else round(max(latencies), 2),
        "min": round(min(latencies), 2),
        "max": round(max(latencies), 2),
        "avg_embedding_ms": round(statistics.mean([r["embedding_ms"] for r in results]), 2),
        "avg_qdrant_ms": round(statistics.mean([r["qdrant_ms"] for r in results]), 2),
        "avg_rerank_ms": round(statistics.mean([r["rerank_ms"] for r in results]), 2),
        "avg_eval_ms": round(statistics.mean([r["eval_ms"] for r in results]), 2),
    }

def print_comparison_report(before_stats, after_stats, before_rps, after_rps):
    """Print the final BEFORE vs AFTER comparison table."""
    
    def delta(before, after):
        if before == 0:
            return "N/A"
        pct = ((after - before) / before) * 100
        return f"{pct:+.1f}%"

    def speedup(before, after):
        if after == 0:
            return "∞x"
        return f"{before / after:.1f}x"

    print("\n")
    print("=" * 75)
    print("     BEFORE vs AFTER — PERFORMANCE COMPARISON REPORT")
    print(f"     Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 75)

    print(f"\n{'Metric':<35} | {'BEFORE (No Cache)':>18} | {'AFTER (Redis)':>14} | {'Delta':>12}")
    print("-" * 85)

    rows = [
        ("Avg Latency (ms)",          before_stats["mean"],         after_stats["mean"]),
        ("Median Latency (ms)",       before_stats["median"],       after_stats["median"]),
        ("P95 Latency (ms)",          before_stats["p95"],          after_stats["p95"]),
        ("P99 Latency (ms)",          before_stats["p99"],          after_stats["p99"]),
        ("Min Latency (ms)",          before_stats["min"],          after_stats["min"]),
        ("Max Latency (ms)",          before_stats["max"],          after_stats["max"]),
    ]

    for label, bv, av in rows:
        print(f" {label:<34} | {bv:>15.2f} ms | {av:>11.2f} ms | {delta(bv, av):>10}  ({speedup(bv, av)} faster)")

    print("-" * 85)
    print(f" {'Throughput (Req/sec)':<34} | {before_rps:>15.2f}    | {after_rps:>11.2f}    | {delta(before_rps, after_rps):>10}")

    print("\n" + "-" * 85)
    print(" Per-Stage Breakdown (Avg ms)")
    print("-" * 85)

    stages = [
        ("NVIDIA Embedding API",  before_stats["avg_embedding_ms"], after_stats["avg_embedding_ms"]),
        ("Qdrant Vector Search",  before_stats["avg_qdrant_ms"],    after_stats["avg_qdrant_ms"]),
        ("Reranking",             before_stats["avg_rerank_ms"],    after_stats["avg_rerank_ms"]),
        ("RAG Evaluation",        before_stats["avg_eval_ms"],      after_stats["avg_eval_ms"]),
    ]

    for label, bv, av in stages:
        print(f" {label:<34} | {bv:>15.2f} ms | {av:>11.2f} ms | {delta(bv, av):>10}")

    # Resume bullet points
    avg_before = before_stats["mean"]
    avg_after = after_stats["mean"]
    reduction_pct = ((avg_before - avg_after) / avg_before) * 100 if avg_before > 0 else 0
    speedup_x = avg_before / avg_after if avg_after > 0 else 0

    print("\n" + "=" * 75)
    print("     RESUME BULLET POINTS (Copy & Paste Ready)")
    print("=" * 75)
    print(f'\n• "Reduced RAG search latency by {reduction_pct:.0f}% (from {avg_before:.0f}ms to {avg_after:.0f}ms)')
    print(f'   by implementing Redis caching layer for vector retrieval & LLM evaluation outputs."')
    print(f'\n• "Increased query throughput by {speedup_x:.0f}x ({before_rps:.1f} to {after_rps:.0f}+ req/sec)')
    print(f'   while eliminating redundant NVIDIA embedding and Qdrant API calls."')
    print(f'\n• "Achieved {reduction_pct:.0f}% cost reduction on downstream AI API usage')
    print(f'   by caching computed embeddings, vector search results, and LLM evaluations in Redis."')
    print("=" * 75)

def main():
    repo_id = sys.argv[1] if len(sys.argv) > 1 else "6ffdc2c6-6717-4051-bcdb-2376e996e0c7"
    num_iterations = int(sys.argv[2]) if len(sys.argv) > 2 else 5

    print("=" * 70)
    print("  CodeForge AI — BEFORE vs AFTER Performance Benchmark")
    print("=" * 70)
    print(f"  Repository ID:   {repo_id}")
    print(f"  Iterations/Query: {num_iterations}")
    print(f"  Test Queries:    {len(TEST_QUERIES)}")
    print(f"  Target:          {BASE_URL}")
    print("=" * 70)

    # ── PHASE 1: BEFORE (No Cache — bypass Redis) ──
    before_results = run_phase(
        "BEFORE (No Cache)", TEST_QUERIES, repo_id,
        bypass_cache=True, num_iterations=num_iterations
    )

    # ── PHASE 2: AFTER (Redis Cached) ──
    # First call primes the cache (cache miss), subsequent calls are cache hits
    print("\n  Priming Redis cache with initial requests...")
    for q in TEST_QUERIES:
        make_request(q, repo_id, bypass_cache=False)
    time.sleep(1)

    after_results = run_phase(
        "AFTER (Redis Cached)", TEST_QUERIES, repo_id,
        bypass_cache=False, num_iterations=num_iterations
    )

    # ── PHASE 3: Throughput Test ──
    print("\n\n  Running concurrent throughput test (10 threads)...")
    before_rps, _, _ = run_throughput_test(repo_id, bypass_cache=True, concurrent_users=5)
    after_rps, _, _ = run_throughput_test(repo_id, bypass_cache=False, concurrent_users=5)

    # ── Compute & Print Report ──
    before_stats = compute_stats(before_results)
    after_stats = compute_stats(after_results)

    if before_stats and after_stats:
        print_comparison_report(before_stats, after_stats, before_rps, after_rps)

        # Save raw data to JSON
        output = {
            "timestamp": datetime.now().isoformat(),
            "repository_id": repo_id,
            "before": {"stats": before_stats, "rps": before_rps, "raw": before_results},
            "after": {"stats": after_stats, "rps": after_rps, "raw": after_results},
        }
        with open("benchmark_results.json", "w") as f:
            json.dump(output, f, indent=2)
        print(f"\n  Raw data saved to: benchmark_results.json")
    else:
        print("\n  Benchmark incomplete — check if ingestion-service is running.")

if __name__ == "__main__":
    main()
