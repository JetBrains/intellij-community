package circlet.plugins.pipelines.services.execution

import libraries.io.random.*

class TaskLongIdStorage {
    private val map = mutableMapOf<String, Long>()
    fun getOrCreateId(stringId: String): Long {
        return map.getOrPut(stringId) {
            Random.nextLong()
        }
    }
}
