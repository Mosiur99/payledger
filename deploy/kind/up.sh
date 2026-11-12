#!/usr/bin/env bash
# Creates a kind cluster, loads freshly built images and installs PayLedger with Helm.
set -euo pipefail
cd "$(dirname "$0")/../.."

CLUSTER=payledger
NS=payledger

kind get clusters | grep -qx "$CLUSTER" || kind create cluster --config deploy/kind/kind-config.yaml

# The archive goes under the project, not /tmp, because a snap-installed Docker cannot write to the host's /tmp.
mkdir -p target/kind-images
for service in payments-service notification-service; do
  image="ghcr.io/mosiur99/payledger-$service:local"
  archive="target/kind-images/$service.tar"
  docker build -q --build-arg SERVICE="$service" -t "$image" .
  docker save -o "$archive" "$image"
  kind load image-archive "$archive" --name "$CLUSTER"
done
rm -rf target/kind-images

kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NS" create configmap keycloak-realm \
  --from-file=deploy/keycloak/payledger-realm.json --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NS" apply -f deploy/kind/infra.yaml
kubectl -n "$NS" rollout status statefulset/postgres --timeout=5m
kubectl -n "$NS" rollout status deployment/kafka --timeout=5m
kubectl -n "$NS" rollout status deployment/keycloak --timeout=10m

helm upgrade --install payledger deploy/helm/payledger -n "$NS" \
  -f deploy/kind/values-kind.yaml --wait --timeout 10m

kubectl -n "$NS" get pods
