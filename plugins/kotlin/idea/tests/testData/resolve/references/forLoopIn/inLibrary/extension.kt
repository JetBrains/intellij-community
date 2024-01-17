fun main() {
  for (i <caret>in "") {}
}

// MULTIRESOLVE
// REF: (in kotlin.collections.CharIterator).next()
// REF: (in kotlin.collections.Iterator).hasNext()
// REF: (kotlin.text).CharSequence.iterator()