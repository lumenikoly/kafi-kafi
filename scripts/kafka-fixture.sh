#!/usr/bin/env bash
set -euo pipefail
runtime=${KAFI_CONTAINER_RUNTIME:-docker}
name=kafi-migration-integration
label=com.kafikafi.test=migration
if [[ "${1:-}" == stop ]]; then
  if "$runtime" inspect "$name" >/dev/null 2>&1; then
    [[ "$("$runtime" inspect --format '{{ index .Config.Labels "com.kafikafi.test" }}' "$name")" == migration ]]
    "$runtime" rm -f "$name"
  fi
  exit
fi
[[ "${1:-}" == start ]]
"$runtime" run -d --name "$name" --label "$label" -p 127.0.0.1:19092:19092 \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:19092,CONTROLLER://:19093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:19092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:19093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 apache/kafka:3.9.1
for attempt in $(seq 1 60); do
  if "$runtime" exec "$name" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --list; then exit; fi
  sleep 2
done
echo 'Kafka fixture did not become ready' >&2
exit 1
