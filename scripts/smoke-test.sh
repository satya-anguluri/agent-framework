#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <executable-jar>" >&2
  exit 2
fi

project_root="$(cd "$(dirname "$0")/.." && pwd)"
jar_path="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
smoke_root="$(mktemp -d)"
trap 'rm -rf "$smoke_root"' EXIT

for repository in order-service payment-service; do
  cp -R "$project_root/examples/sample-multi-repo/$repository" "$smoke_root/$repository"
  git -C "$smoke_root/$repository" init --initial-branch=main --quiet
  git -C "$smoke_root/$repository" config user.name "Agent Framework Smoke Test"
  git -C "$smoke_root/$repository" config user.email "smoke@example.invalid"
  git -C "$smoke_root/$repository" add .
  git -C "$smoke_root/$repository" commit --quiet -m "sample baseline"
done

printf '{"repositories":[{"name":"order-service","localPath":"%s","defaultBranch":"main"},{"name":"payment-service","localPath":"%s","defaultBranch":"main"}]}\n'   "$smoke_root/order-service" "$smoke_root/payment-service" > "$smoke_root/repositories.json"

db_path="$smoke_root/context.db"
java -jar "$jar_path" init --db "$db_path"
java -jar "$jar_path" index --db "$db_path" --config "$smoke_root/repositories.json"
java -jar "$jar_path" work-item-import --db "$db_path"   --file "$project_root/examples/sample-multi-repo/sample-work-item.json"
java -jar "$jar_path" analyze-work-item --db "$db_path" --format json SAMPLE-1 > "$smoke_root/analysis.json"
java -jar "$jar_path" plan-work-item --db "$db_path" --format markdown   --output "$smoke_root/plan.md" SAMPLE-1
java -jar "$jar_path" validate-work-item --db "$db_path" --head HEAD   --format json SAMPLE-1 > "$smoke_root/validation.json"
java -jar "$jar_path" review-work-item --db "$db_path" --head HEAD   --format markdown --output "$smoke_root/review.md" SAMPLE-1

grep -q '"workItem"' "$smoke_root/analysis.json"
grep -q '# Implementation Plan: SAMPLE-1' "$smoke_root/plan.md"
grep -q '"HUMAN_REVIEW_REQUIRED"' "$smoke_root/validation.json"
grep -q '# Pull-request review evidence: SAMPLE-1' "$smoke_root/review.md"
echo "End-to-end smoke test passed."
