"""
============================================================
 CodeForge AI — Complete Granular Cloud Benchmark Suite
============================================================
 Measures BOTH:
   Part 1: High-Level End-to-End User Journey Latency
   Part 2: Granular Sub-Component Service Breakdown:
           • API Gateway & RTT Overhead
           • Nvidia AI 4096-dim Vector Embeddings
           • Qdrant Cloud Vector Database Search
           • Upstash Redis Cache Hit vs. Miss
           • Groq LLM Multi-Agent Generation

 Usage:
   python scripts/cloud_benchmark.py
============================================================
"""

import time
import requests
import statistics
import sys
import json
import uuid
from datetime import datetime

GATEWAY_URL = "https://codeforge-gatewayy.onrender.com"
TEST_REPO_URL = "https://github.com/octocat/Spoon-Knife"

# Generate a unique test user per run to test clean register flow
UNIQUE_ID = str(uuid.uuid4())[:8]
BENCHMARK_EMAIL = f"bench_user_{UNIQUE_ID}@codeforge.ai"
BENCHMARK_PASS = "BenchmarkPass123!"
BENCHMARK_NAME = f"Benchmark Runner {UNIQUE_ID}"

def print_header(title):
    print("\n" + "=" * 75)
    print(f" 🚀 {title}")
    print("=" * 75)

def calc_stats(latencies):
    if not latencies:
        return {"mean": 0.0, "p50": 0.0, "p95": 0.0, "min": 0.0, "max": 0.0, "count": 0}
    sorted_lat = sorted(latencies)
    n = len(sorted_lat)
    p50_idx = int(n * 0.50)
    p95_idx = min(int(n * 0.95), n - 1)
    return {
        "count": n,
        "mean": round(statistics.mean(sorted_lat), 2),
        "p50": round(sorted_lat[p50_idx], 2),
        "p95": round(sorted_lat[p95_idx], 2),
        "min": round(min(sorted_lat), 2),
        "max": round(max(sorted_lat), 2)
    }

def run_e2e_and_granular_benchmark():
    print_header("CodeForge AI — Full Granular Cloud Benchmark Suite")
    print(f"Gateway URL : {GATEWAY_URL}")
    print(f"Test User   : {BENCHMARK_EMAIL}")
    print(f"Test Repo   : {TEST_REPO_URL}")
    print(f"Timestamp   : {datetime.now().isoformat()}")

    results_report = {}
    granular_report = {}

    # -------------------------------------------------------------
    # STEP 1: REGISTER USER
    # -------------------------------------------------------------
    print("\n[STEP 1/6] Registering New User Account...")
    reg_url = f"{GATEWAY_URL}/api/auth/register"
    reg_payload = {"name": BENCHMARK_NAME, "email": BENCHMARK_EMAIL, "password": BENCHMARK_PASS}
    start = time.time()
    try:
        resp = requests.post(reg_url, json=reg_payload, timeout=30)
        reg_time = (time.time() - start) * 1000
        if resp.status_code in (200, 201):
            print(f"   ✓ Register Success: {reg_time:.1f} ms (HTTP {resp.status_code})")
            results_report["1. Register User"] = {"latency_ms": round(reg_time, 2), "status": "SUCCESS"}
        else:
            print(f"   ❌ Register Failed: HTTP {resp.status_code} - {resp.text[:120]}")
            return
    except Exception as e:
        print(f"   ❌ Register Exception: {e}")
        return

    # -------------------------------------------------------------
    # STEP 2: LOGIN USER & OBTAIN JWT TOKEN
    # -------------------------------------------------------------
    print("\n[STEP 2/6] Authenticating User (Login)...")
    login_url = f"{GATEWAY_URL}/api/auth/login"
    login_payload = {"email": BENCHMARK_EMAIL, "password": BENCHMARK_PASS}
    start = time.time()
    jwt_token = None
    try:
        resp = requests.post(login_url, json=login_payload, timeout=30)
        login_time = (time.time() - start) * 1000
        if resp.status_code == 200:
            res_json = resp.json()
            data = res_json.get("data", {})
            if isinstance(data, dict) and "token" in data:
                jwt_token = data["token"]
            print(f"   ✓ Login Success: {login_time:.1f} ms (JWT Token Acquired)")
            results_report["2. User Login"] = {"latency_ms": round(login_time, 2), "status": "SUCCESS"}
        else:
            print(f"   ❌ Login Failed: HTTP {resp.status_code} - {resp.text[:120]}")
            return
    except Exception as e:
        print(f"   ❌ Login Exception: {e}")
        return

    headers = {
        "Authorization": f"Bearer {jwt_token}",
        "X-User-Email": BENCHMARK_EMAIL,
        "Content-Type": "application/json"
    }

    # -------------------------------------------------------------
    # STEP 3: INGEST GITHUB REPOSITORY
    # -------------------------------------------------------------
    print(f"\n[STEP 3/6] Submitting Repository for Ingestion ({TEST_REPO_URL})...")
    ingest_url = f"{GATEWAY_URL}/api/ingest/repository"
    ingest_payload = {"url": TEST_REPO_URL}
    repo_id = None
    start = time.time()
    try:
        resp = requests.post(ingest_url, json=ingest_payload, headers=headers, timeout=30)
        ingest_time = (time.time() - start) * 1000
        if resp.status_code == 200:
            res_json = resp.json()
            data = res_json.get("data", {})
            if isinstance(data, dict) and "repositoryId" in data:
                repo_id = data["repositoryId"]
            print(f"   ✓ Ingestion Initiated: {ingest_time:.1f} ms | Repo ID: {repo_id}")
            results_report["3. Ingestion Submit"] = {"latency_ms": round(ingest_time, 2), "repo_id": repo_id}
        else:
            print(f"   ❌ Ingestion Submit Failed: HTTP {resp.status_code} - {resp.text[:120]}")
            return
    except Exception as e:
        print(f"   ❌ Ingestion Exception: {e}")
        return

    # -------------------------------------------------------------
    # STEP 4: POLL INGESTION STATUS UNTIL COMPLETED
    # -------------------------------------------------------------
    print("\n[STEP 4/6] Polling Ingestion Progress...")
    repos_url = f"{GATEWAY_URL}/api/ingest/repositories"
    poll_start = time.time()
    completed = False

    for attempt in range(15):
        time.sleep(2)
        try:
            resp = requests.get(repos_url, headers=headers, timeout=15)
            if resp.status_code == 200:
                repo_list = resp.json().get("data", [])
                target_repo = next((r for r in repo_list if r.get("id") == repo_id), None)
                if target_repo:
                    status = target_repo.get("status")
                    print(f"   Poll #{attempt+1}: Status = {status}")
                    if status == "COMPLETED":
                        completed = True
                        break
                    elif status == "FAILED":
                        print(f"   ❌ Ingestion failed on backend: {target_repo.get('errorMessage')}")
                        break
        except Exception as e:
            print(f"   Poll #{attempt+1} warning: {e}")

    total_ingest_time = (time.time() - poll_start)
    if completed:
        print(f"   ✓ Ingestion Completed in {total_ingest_time:.1f} seconds!")
        results_report["4. Ingestion Polling"] = {"duration_sec": round(total_ingest_time, 1), "status": "COMPLETED"}
    else:
        print("   ⚠️ Ingestion polling timeout. Proceeding with benchmark...")

    # -------------------------------------------------------------
    # STEP 5: GENERATE EMBEDDINGS (POST /api/rag/embed/{repo_id})
    # -------------------------------------------------------------
    if repo_id:
        print(f"\n[STEP 5/6] Triggering 4096-dim Vector Embeddings Generation...")
        embed_url = f"{GATEWAY_URL}/api/rag/embed/{repo_id}"
        start = time.time()
        try:
            resp = requests.post(embed_url, headers=headers, timeout=30)
            embed_time = (time.time() - start) * 1000
            if resp.status_code == 200:
                print(f"   ✓ Embedding Pipeline Started: {embed_time:.1f} ms (HTTP 200)")
                results_report["5. Vector Embed Trigger"] = {"latency_ms": round(embed_time, 2), "status": "SUCCESS"}
            else:
                print(f"   ⚠️ Embed trigger response: HTTP {resp.status_code} - {resp.text[:120]}")
        except Exception as e:
            print(f"   ⚠️ Embed trigger exception: {e}")

        print("   Waiting 5 seconds for vector embeddings & Qdrant storage...")
        time.sleep(5)

    # -------------------------------------------------------------
    # STEP 6: GRANULAR BENCHMARKING (SEARCH & MULTI-AGENT)
    # -------------------------------------------------------------
    if repo_id:
        print(f"\n[STEP 6/6] Executing Granular RAG & Multi-Agent Benchmarks...")

        # Sub-component metrics arrays
        nvidia_embeddings_ms = []
        qdrant_vector_ms = []
        gateway_overheads_ms = []
        groq_agent_ms = []
        
        uncached_search_latencies = []
        cached_search_latencies = []

        # A. Run 3 Iterations of Uncached Search (Fresh RAG Pipeline)
        print("   -> Benchmarking Uncached Fresh RAG Search (Nvidia + Qdrant + Reranker)...")
        search_uncached_url = f"{GATEWAY_URL}/api/rag/search?bypassCache=true"
        search_payload = {"query": "Find index HTML layout and styles", "repositoryId": repo_id}
        
        for i in range(3):
            start = time.time()
            try:
                resp = requests.post(search_uncached_url, json=search_payload, headers=headers, timeout=30)
                e2e_ms = (time.time() - start) * 1000
                if resp.status_code == 200:
                    uncached_search_latencies.append(e2e_ms)
                    res_data = resp.json().get("data", {})
                    
                    emb_ms = res_data.get("embeddingTimeMs", 0)
                    qdrant_ms = res_data.get("qdrantTimeMs", 0)
                    pipeline_ms = res_data.get("pipelineTimeMs", 0)
                    
                    if emb_ms: nvidia_embeddings_ms.append(emb_ms)
                    if qdrant_ms: qdrant_vector_ms.append(qdrant_ms)
                    is_cached = res_data.get("cachedResult", False)
                    if not is_cached and e2e_ms and pipeline_ms and e2e_ms >= pipeline_ms:
                        gateway_overheads_ms.append(e2e_ms - pipeline_ms)
                    
                    print(f"      Run #{i+1}: Total {e2e_ms:.1f}ms | Nvidia: {emb_ms}ms | Qdrant: {qdrant_ms}ms | Network/Gateway: {e2e_ms-pipeline_ms:.1f}ms")
            except Exception as e:
                print(f"      Run #{i+1} error: {e}")

        # B. Run 3 Iterations of Cached Search (Upstash Redis)
        print("   -> Benchmarking Cached RAG Search (Upstash Redis)...")
        search_cached_url = f"{GATEWAY_URL}/api/rag/search"
        for i in range(3):
            start = time.time()
            try:
                resp = requests.post(search_cached_url, json=search_payload, headers=headers, timeout=30)
                e2e_ms = (time.time() - start) * 1000
                if resp.status_code == 200:
                    cached_search_latencies.append(e2e_ms)
                    print(f"      Run #{i+1}: Total {e2e_ms:.1f}ms (Redis Cached)")
            except Exception as e:
                print(f"      Run #{i+1} error: {e}")

        # C. Run 2 Iterations of Multi-Agent Orchestration (Groq LLM Engine)
        print("   -> Benchmarking Multi-Agent AI Orchestration (Groq LLM)...")
        analyze_url = f"{GATEWAY_URL}/api/orchestrator/analyze"
        analyze_payload = {"query": "Analyze styles and HTML structure", "repositoryId": repo_id}
        for i in range(2):
            start = time.time()
            try:
                resp = requests.post(analyze_url, json=analyze_payload, headers=headers, timeout=60)
                e2e_ms = (time.time() - start) * 1000
                if resp.status_code == 200:
                    res_data = resp.json().get("data", {})
                    groq_total = res_data.get("totalProcessingTimeMs", 0)
                    if groq_total: groq_agent_ms.append(groq_total)
                    print(f"      Run #{i+1}: Total {e2e_ms:.1f}ms | Groq Parallel Agents: {groq_total}ms")
            except Exception as e:
                print(f"      Run #{i+1} error: {e}")

        # Store in Report
        results_report["6. RAG Search (Uncached E2E)"] = calc_stats(uncached_search_latencies)
        results_report["6. RAG Search (Upstash Redis Cached)"] = calc_stats(cached_search_latencies)

        granular_report["1. API Gateway & Network Overhead"] = calc_stats(gateway_overheads_ms)
        granular_report["2. Nvidia AI 4096-dim Embeddings API"] = calc_stats(nvidia_embeddings_ms)
        granular_report["3. Qdrant Cloud Vector Database"] = calc_stats(qdrant_vector_ms)
        granular_report["4. Groq LLM Multi-Agent Engine"] = calc_stats(groq_agent_ms)

    # -------------------------------------------------------------
    # FINAL CLOUD BENCHMARK SUMMARY & GRANULAR BREAKDOWN
    # -------------------------------------------------------------
    print_header("PART 1: HIGH-LEVEL END-TO-END WORKFLOW SUMMARY")
    print(f"{'Workflow Step':<40} | {'Latency / Duration':<25}")
    print("-" * 75)
    for step_name, data in results_report.items():
        if isinstance(data, dict) and "mean" in data:
            val = f"Mean: {data['mean']} ms (p50: {data['p50']} ms)"
        elif isinstance(data, dict) and "latency_ms" in data:
            val = f"{data['latency_ms']} ms"
        elif isinstance(data, dict) and "duration_sec" in data:
            val = f"{data['duration_sec']} seconds"
        else:
            val = str(data)
        print(f"{step_name:<40} | {val:<25}")
    print("-" * 75)

    print_header("PART 2: GRANULAR CLOUD SERVICE COMPONENT BREAKDOWN")
    print(f"{'Cloud / Microservice Sub-Component':<40} | {'Mean Latency':<15} | {'p50 Latency':<15}")
    print("-" * 75)
    for comp_name, stats in granular_report.items():
        mean_str = f"{stats.get('mean', 0)} ms" if stats else "N/A"
        p50_str = f"{stats.get('p50', 0)} ms" if stats else "N/A"
        print(f"{comp_name:<40} | {mean_str:<15} | {p50_str:<15}")
    print("-" * 75)

    # Export full report JSON
    report_file = "granular_cloud_benchmark_report.json"
    full_output = {
        "timestamp": datetime.now().isoformat(),
        "workflow_summary": results_report,
        "granular_service_breakdown": granular_report
    }
    with open(report_file, "w") as f:
        json.dump(full_output, f, indent=2)
    print(f"\n✅ Full Granular Benchmark Report exported to '{report_file}'")

if __name__ == "__main__":
    run_e2e_and_granular_benchmark()
