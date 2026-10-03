//! librdkafka's DescribeCluster API is not yet wrapped by rust-rdkafka.
//! Keep FFI and native lifetimes confined to this Kafka adapter.
use crate::{
    error::{AppError, Result},
    ipc::dto::{Broker, Cluster},
    kafka::connection::Connection,
};
use rdkafka::bindings as ffi;
use std::ffi::CStr;
struct Queue(*mut ffi::rd_kafka_queue_t);
impl Drop for Queue {
    fn drop(&mut self) {
        unsafe {
            ffi::rd_kafka_queue_destroy(self.0);
        }
    }
}
struct Event(*mut ffi::rd_kafka_event_t);
impl Drop for Event {
    fn drop(&mut self) {
        unsafe {
            ffi::rd_kafka_event_destroy(self.0);
        }
    }
}
unsafe fn text(ptr: *const std::ffi::c_char) -> Option<String> {
    if ptr.is_null() {
        None
    } else {
        Some(
            unsafe { CStr::from_ptr(ptr) }
                .to_string_lossy()
                .into_owned(),
        )
    }
}
pub fn describe(connection: &Connection) -> Result<Cluster> {
    // The connection remains borrowed until every native result has been copied.
    // Node/string pointers belong to Event; Event is destroyed before Queue.
    unsafe {
        let queue = Queue(ffi::rd_kafka_queue_new(
            connection.admin.inner().native_ptr(),
        ));
        if queue.0.is_null() {
            return Err(AppError::new(
                "INTERNAL_ERROR",
                "Cannot allocate cluster metadata queue.",
            ));
        }
        ffi::rd_kafka_DescribeCluster(
            connection.admin.inner().native_ptr(),
            std::ptr::null(),
            queue.0,
        );
        let event = Event(ffi::rd_kafka_queue_poll(queue.0, 10_000));
        if event.0.is_null() {
            return Err(AppError::new(
                "TIMEOUT",
                "Cluster metadata did not arrive before the timeout.",
            ));
        }
        let error = ffi::rd_kafka_event_error(event.0);
        if error != ffi::rd_kafka_resp_err_t::RD_KAFKA_RESP_ERR_NO_ERROR {
            return Err(AppError::from(rdkafka::error::KafkaError::AdminOp(
                error.into(),
            )));
        }
        let result = ffi::rd_kafka_event_DescribeCluster_result(event.0);
        if result.is_null() {
            return Err(AppError::new(
                "CONNECTION_FAILED",
                "Kafka returned no cluster description.",
            ));
        }
        let mut count = 0;
        let nodes = ffi::rd_kafka_DescribeCluster_result_nodes(result, &mut count);
        let mut brokers = Vec::new();
        if !nodes.is_null() {
            for &node in std::slice::from_raw_parts(nodes, count) {
                if !node.is_null() {
                    brokers.push(Broker {
                        id: ffi::rd_kafka_Node_id(node),
                        host: text(ffi::rd_kafka_Node_host(node)).unwrap_or_default(),
                        port: ffi::rd_kafka_Node_port(node).into(),
                        rack: text(ffi::rd_kafka_Node_rack(node)),
                    });
                }
            }
        }
        let controller = ffi::rd_kafka_DescribeCluster_result_controller(result);
        Ok(Cluster {
            cluster_id: text(ffi::rd_kafka_DescribeCluster_result_cluster_id(result)),
            controller: if controller.is_null() {
                None
            } else {
                Some(ffi::rd_kafka_Node_id(controller))
            },
            brokers,
            profile_id: connection.profile_id.clone(),
            generation: connection.generation.clone(),
        })
    }
}
