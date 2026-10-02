use super::{AppState, blocking};
use crate::{
    error::{AppError, Result},
    ipc::dto::*,
    kafka,
};
use std::collections::BTreeMap;
impl AppState {
    pub async fn cluster(&self) -> Result<Cluster> {
        let c = self.connection()?;
        blocking(move || kafka::admin::cluster(&c)).await
    }
    pub async fn topics(&self) -> Result<Vec<Topic>> {
        let c = self.connection()?;
        blocking(move || kafka::admin::list(&c)).await
    }
    pub async fn topic_detail(&self, topic: String) -> Result<TopicDetail> {
        let c = self.connection()?;
        let clone = c.clone();
        let name = topic.clone();
        let partitions = blocking(move || kafka::admin::partitions(&clone, &name)).await?;
        let config = kafka::admin::configuration(&c, &topic).await?;
        Ok(TopicDetail {
            name: topic,
            partitions,
            config,
        })
    }
    pub async fn create_topic(&self, request: CreateTopic) -> Result<()> {
        let c = self.connection()?;
        kafka::admin::create(&c, request).await
    }
    pub async fn delete_topic(&self, topic: String, confirmed: bool) -> Result<()> {
        if !confirmed {
            return Err(AppError::invalid("Confirm topic deletion explicitly."));
        }
        let c = self.connection()?;
        kafka::admin::delete(&c, &topic).await
    }
    pub async fn update_topic_config(
        &self,
        topic: String,
        updates: BTreeMap<String, String>,
    ) -> Result<()> {
        let c = self.connection()?;
        kafka::admin::update_config(&c, &topic, updates).await
    }
    pub async fn produce(&self, request: ProduceRequest) -> Result<ProduceResult> {
        let c = self.connection()?;
        kafka::producer::produce(&c, request).await
    }
    pub async fn groups(&self) -> Result<Vec<ConsumerGroup>> {
        let c = self.connection()?;
        blocking(move || kafka::groups::list(&c)).await
    }
    pub async fn group_detail(&self, id: String) -> Result<GroupDetail> {
        let c = self.connection()?;
        blocking(move || kafka::groups::describe(&c, &id)).await
    }
    pub async fn delete_group(&self, id: String, confirmed: bool) -> Result<()> {
        if !confirmed {
            return Err(AppError::invalid(
                "Confirm consumer group deletion explicitly.",
            ));
        }
        let c = self.connection()?;
        kafka::groups::delete(&c, &id).await
    }
}
