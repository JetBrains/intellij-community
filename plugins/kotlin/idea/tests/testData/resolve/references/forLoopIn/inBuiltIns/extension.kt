fun main(it: Iterator<Any>) {
  for (i <caret>in it.iterator()) {}
}

// MULTIRESOLVE
// REF: (in kotlin.collections.Iterator).hasNext()
// REF: (in kotlin.collections.Iterator).next()
// REF: (kotlin.collections).Iterator<T>.iterator()
