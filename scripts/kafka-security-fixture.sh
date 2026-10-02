#!/usr/bin/env bash
set -euo pipefail

runtime=${KAFI_CONTAINER_RUNTIME:-podman}
name=kafi-secure-kafka-integration
owner_label=com.kafikafi.test
owner_value=kafka-security
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd -- "$script_dir/.." && pwd)
fixture_dir="$repo_dir/src-tauri/tests/fixtures/kafka-security"
container_config=/run/kafka/server.properties
container_keystore=/etc/kafka/secrets/server.p12
container_jaas=/etc/kafka/secrets/server-jaas.conf
host_fixture_dir=$fixture_dir
if command -v cygpath >/dev/null 2>&1; then
  host_fixture_dir=$(cygpath -w "$fixture_dir" | sed 's#\\#/#g')
  export MSYS_NO_PATHCONV=1
fi

owned_container() {
  "$runtime" inspect "$name" >/dev/null 2>&1 || return 1
  [[ $("$runtime" inspect --format "{{ index .Config.Labels \"$owner_label\" }}" "$name") == "$owner_value" ]]
}

if [[ "${1:-}" == stop ]]; then
  if "$runtime" inspect "$name" >/dev/null 2>&1; then
    owned_container || { echo "Refusing to stop unowned container $name" >&2; exit 1; }
    "$runtime" rm -f "$name"
  fi
  exit 0
fi

[[ "${1:-}" == start ]] || { echo "Usage: $0 start|stop" >&2; exit 2; }
if "$runtime" inspect "$name" >/dev/null 2>&1; then
  owned_container || { echo "Refusing to replace unowned container $name" >&2; exit 1; }
  "$runtime" rm -f "$name"
fi

[[ -f "$fixture_dir/server.p12" && -f "$fixture_dir/server.properties" && -f "$fixture_dir/server-jaas.conf" ]]
"$runtime" run -d --name "$name" --label "$owner_label=$owner_value" --memory=600m \
  -p 127.0.0.1:19094:19094 \
  -e KAFKA_HEAP_OPTS='-Xms128m -Xmx256m' \
  -e KAFKA_OPTS="-Djava.security.auth.login.config=$container_jaas" \
  --volume "$host_fixture_dir/server.properties:$container_config:ro" \
  --volume "$host_fixture_dir/server.p12:$container_keystore:ro" \
  --volume "$host_fixture_dir/server-jaas.conf:$container_jaas:ro" \
  --entrypoint /bin/bash apache/kafka:3.9.1 -ec \
  '/opt/kafka/bin/kafka-storage.sh format --cluster-id MkU3OEVBNTcwNTJENDM2Qk --config /run/kafka/server.properties --add-scram '\''SCRAM-SHA-256=[name="test-user",password="kafi-test-password"]'\'' --add-scram '\''SCRAM-SHA-512=[name="test-user",password="kafi-test-password"]'\''; exec /opt/kafka/bin/kafka-server-start.sh /run/kafka/server.properties' >/dev/null

for attempt in $(seq 1 60); do
  if "$runtime" exec -e KAFKA_HEAP_OPTS='-Xms32m -Xmx64m' "$name" /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:19095 >/dev/null 2>&1; then
    echo "Secure Kafka fixture ready at localhost:19094 (SASL_SSL); test user: test-user"
    exit 0
  fi
  sleep 2
done

"$runtime" logs "$name" >&2 || true
if owned_container; then "$runtime" rm -f "$name" >/dev/null; fi
echo "Secure Kafka fixture did not become ready" >&2
exit 1
