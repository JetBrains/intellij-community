typealias <caret>Action = suspend (String) -> Int

val action: Action = { it.length }
