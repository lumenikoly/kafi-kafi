package com.lightkafka.core.storage

interface TemplateStore {
    fun loadTemplates(): List<ProducerTemplate>

    fun saveTemplates(templates: List<ProducerTemplate>)
}
