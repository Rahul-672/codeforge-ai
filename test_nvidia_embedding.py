"""
Quick test for NVIDIA NV-EmbedCode-7B-v1 embedding model.
Reads the API key from ingestion-service/src/main/resources/application.yml
"""

import urllib.request
import json
import sys
import re
import time

# --- Read API key from application.yml ---
CONFIG_PATH = r"ingestion-service\src\main\resources\application.yml"

def read_nvidia_key():
    with open(CONFIG_PATH, "r") as f:
        content = f.read()
    # Match nvidia.api.key value
    match = re.search(r"nvidia:\s*\n\s*api:\s*\n\s*key:\s*(\S+)", content)
    if match:
        return match.group(1)
    return None

API_KEY = read_nvidia_key()
if not API_KEY:
    print("ERROR: Could not read NVIDIA API key from", CONFIG_PATH)
    sys.exit(1)

print(f"API key loaded (starts with: {API_KEY[:10]}...)")

# --- Config ---
BASE_URL = "https://integrate.api.nvidia.com/v1/embeddings"
MODEL = "nvidia/nv-embedcode-7b-v1"

# --- Test cases ---
test_inputs = [
    {
        "name": "Simple code snippet",
        "text": "public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello\"); } }",
        "input_type": "passage"
    },
    {
        "name": "Natural language query",
        "text": "How does the authentication service validate JWT tokens?",
        "input_type": "query"
    },
    {
        "name": "Python function",
        "text": "def fibonacci(n):\n    if n <= 1:\n        return n\n    return fibonacci(n-1) + fibonacci(n-2)",
        "input_type": "passage"
    },
]

def get_embedding(text, input_type="passage"):
    """Call NVIDIA NV-EmbedCode API and return embedding vector."""
    payload = {
        "input": [text],
        "model": MODEL,
        "encoding_format": "float",
        "input_type": input_type
    }

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE_URL,
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {API_KEY}"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return body
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        print(f"  HTTP {e.code}: {error_body}")
        return None
    except Exception as e:
        print(f"  Error: {e}")
        return None

# --- Run tests ---
print("=" * 60)
print(f"Testing NVIDIA NV-EmbedCode-7B-v1")
print(f"Endpoint: {BASE_URL}")
print(f"Model: {MODEL}")
print("=" * 60)

for i, test in enumerate(test_inputs, 1):
    print(f"\n--- Test {i}: {test['name']} ---")
    print(f"  Input type: {test['input_type']}")
    print(f"  Text: {test['text'][:80]}...")

    start = time.time()
    result = get_embedding(test["text"], test["input_type"])
    elapsed = time.time() - start

    if result and "data" in result:
        embedding = result["data"][0]["embedding"]
        dims = len(embedding)
        first_5 = [f"{v:.6f}" for v in embedding[:5]]
        model_used = result.get("model", "unknown")
        usage = result.get("usage", {})

        print(f"  Status: SUCCESS")
        print(f"  Dimensions: {dims}")
        print(f"  First 5 values: [{', '.join(first_5)}]")
        print(f"  Model: {model_used}")
        print(f"  Tokens used: {usage.get('total_tokens', 'N/A')}")
        print(f"  Latency: {elapsed:.2f}s")
    else:
        print(f"  Status: FAILED")
        print(f"  Latency: {elapsed:.2f}s")

    # Small delay between requests to respect rate limits
    if i < len(test_inputs):
        time.sleep(1)

print("\n" + "=" * 60)
print("Test complete!")
print("=" * 60)
