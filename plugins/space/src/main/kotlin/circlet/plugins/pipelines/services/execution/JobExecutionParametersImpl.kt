package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.api.*
import libraries.klogging.*

class JobExecutionParametersImpl : JobExecutionParameters {
    companion object : KLogging()
    private val keys = mutableMapOf<Long, MutableMap<String, String>>()

    override fun set(jobExecutionId: Long, key: String, value: String, constValue: Boolean) {
        logger.info { "set. jobExecutionId $jobExecutionId. key $key. value $value. constValue $constValue" }
        val map = keys[jobExecutionId] ?: error("Can't find keys for $jobExecutionId")
        map[key] = value
    }

    override fun get(jobExecutionId: Long, key: String): String? {
        logger.info { "get. jobExecutionId $jobExecutionId. key $key" }
        val map = keys[jobExecutionId] ?: error("Can't find keys for $jobExecutionId")
        return map[key]
    }

    override fun delete(jobExecutionId: Long, key: String) {
        logger.info { "delete. jobExecutionId $jobExecutionId. key $key" }
        val map = keys[jobExecutionId] ?: error("Can't find keys for $jobExecutionId")
        map.remove(key)
    }

    override fun deleteAll(jobExecutionId: Long) {
        logger.info { "deleteAll. jobExecutionId $jobExecutionId" }
        keys.remove(jobExecutionId)
    }

    override fun initSystemParams(jobExecutionId: Long, commit: String, branch: String, triggerData: TriggerData) {
        logger.info { "initSystemParams. jobExecutionId $jobExecutionId" }
        keys[jobExecutionId] = mutableMapOf<String, String>()
    }
}
