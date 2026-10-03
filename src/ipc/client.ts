import { Channel, invoke } from "@tauri-apps/api/core";
import type * as T from "./types";

type Commands = {
  ui_ready: [undefined, number];
  get_profiles: [undefined, T.Profile[]];
  save_profile: [{ request: T.SaveProfile }, T.SaveResult];
  delete_profile: [{ id: string }, null];
  test_connection: [{ request: T.SaveProfile }, T.Cluster];
  connect: [{ id: string }, T.Cluster];
  disconnect: [undefined, null];
  get_cluster: [undefined, T.Cluster];
  get_brokers: [undefined, T.Broker[]];
  list_topics: [undefined, T.Topic[]];
  describe_topic: [{ topic: string }, T.TopicDetail];
  create_topic: [{ request: T.CreateTopic }, null];
  delete_topic: [{ topic: string; confirmed: boolean }, null];
  update_topic_config: [
    { topic: string; updates: Record<string, string> },
    null,
  ];
  produce_message: [{ request: T.ProduceRequest }, T.ProduceResult];
  start_consumer: [
    { request: T.StartConsumer; channel: Channel<T.Batch> },
    string,
  ];
  pause_consumer: [{ sessionId: string }, null];
  resume_consumer: [{ sessionId: string }, null];
  stop_consumer: [{ sessionId: string }, null];
  acknowledge_batch: [{ sessionId: string; sequence: number }, null];
  set_consumer_filter: [{ sessionId: string; filter: T.MessageFilter }, null];
  get_message_detail: [
    { sessionId: string; messageId: string; full: boolean },
    T.MessageDetail,
  ];
  export_message: [
    { sessionId: string; messageId: string; field: string },
    boolean,
  ];
  choose_certificate: [undefined, string | null];
  list_consumer_groups: [undefined, T.ConsumerGroup[]];
  describe_consumer_group: [{ id: string }, T.GroupDetail];
  preview_group_offsets: [{ request: T.ResetRequest }, T.ResetPreview];
  reset_group_offsets: [{ token: string; confirmed: boolean }, null];
  delete_consumer_group: [{ id: string; confirmed: boolean }, null];
  get_settings: [undefined, T.Settings];
  save_settings: [{ settings: T.Settings }, null];
  get_legacy_status: [undefined, T.LegacyStatus];
  import_legacy: [{ fingerprint: string }, T.ImportResponse];
  get_producer_templates: [undefined, unknown[]];
  local_kafka_status: [undefined, T.ContainerStatus];
  start_local_kafka: [undefined, T.ContainerStatus];
  stop_local_kafka: [undefined, T.ContainerStatus];
};
export function command<K extends keyof Commands>(
  name: K,
  ...args: Commands[K][0] extends undefined ? [] : [Commands[K][0]]
): Promise<Commands[K][1]> {
  return invoke(name, args[0]);
}
export function messageChannel(
  onBatch: (batch: T.Batch) => void,
): Channel<T.Batch> {
  const channel = new Channel<T.Batch>();
  channel.onmessage = onBatch;
  return channel;
}
export function errorMessage(error: unknown): string {
  if (
    typeof error === "object" &&
    error !== null &&
    "message" in error &&
    typeof error.message === "string"
  )
    return error.message;
  return "The operation failed. Check the connection and try again.";
}
