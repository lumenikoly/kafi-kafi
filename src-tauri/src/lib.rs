pub mod app;
pub mod containers;
pub mod domain;
pub mod error;
pub mod ipc;
pub mod kafka;
pub mod secrets;
pub mod storage;

pub fn run() {
    ipc::run();
}
