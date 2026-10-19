class Record(val number: Int) {
    fun read() = number
}

typealias <caret>Alias = Record

val unbound = Alias::read
val bound = Alias(1)::read
