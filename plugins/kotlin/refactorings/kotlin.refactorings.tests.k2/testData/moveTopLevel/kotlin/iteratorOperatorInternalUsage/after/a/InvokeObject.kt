package a

class Extended

operator fun Extended.iterator() = object : Iterator<Int> {
    override fun hasNext() = false
    override fun next() = 0
}

