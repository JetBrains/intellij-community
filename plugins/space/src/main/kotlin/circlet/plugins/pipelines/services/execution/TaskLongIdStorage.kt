package circlet.plugins.pipelines.services.execution

class TaskLongIdStorage {
    private var nextId: Long = 0
    private val map = mutableMapOf<String, Long>()
    fun getOrCreateId(stringId: String): Long {
        return map.getOrPut(stringId) {
            nextId++
        }
    }
}
