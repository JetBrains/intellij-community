class Record(val number: Int)

typealias <caret>Alias = Record

val factory = ::Alias
val mapped = listOf(1, 2).map(::Alias)
