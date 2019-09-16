package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.api.*
import libraries.klogging.*

class JobExecutionParametersImpl : JobExecutionParameters {
    companion object : KLogging()
    private val keys = mutableMapOf<Long, MutableMap<String, String>>()

    override fun set(taskExecutionId: Long, key: String, value: String, constValue: Boolean) {
        logger.info { "set. taskExecutionId $taskExecutionId. key $key. value $value. constValue $constValue" }
        val map = keys[taskExecutionId] ?: error("Can't find keys for $taskExecutionId")
        map[key] = value
    }

    override fun get(taskExecutionId: Long, key: String): String? {
        logger.info { "get. taskExecutionId $taskExecutionId. key $key" }
        val map = keys[taskExecutionId] ?: error("Can't find keys for $taskExecutionId")
        return map[key]
    }

    override fun delete(taskExecutionId: Long, key: String) {
        logger.info { "delete. taskExecutionId $taskExecutionId. key $key" }
        val map = keys[taskExecutionId] ?: error("Can't find keys for $taskExecutionId")
        map.remove(key)
    }

    override fun deleteAll(taskExecutionId: Long) {
        logger.info { "deleteAll. taskExecutionId $taskExecutionId" }
        keys.remove(taskExecutionId)
    }

    override fun initSystemParams(taskExecutionId: Long, taskContext: TaskStartContext) {
        logger.info { "initSystemParams. taskExecutionId $taskExecutionId" }
        keys[taskExecutionId] = mutableMapOf<String, String>()
    }
}
