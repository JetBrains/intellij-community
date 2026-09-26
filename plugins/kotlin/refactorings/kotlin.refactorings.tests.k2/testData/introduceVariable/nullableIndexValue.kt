val timestamps = listOf<Long>().toMutableList()

fun get(time: Long): Long? {
    <selection>timestamps.withIndex().firstOrNull{ it.value >= time}</selection>
    return 0
}