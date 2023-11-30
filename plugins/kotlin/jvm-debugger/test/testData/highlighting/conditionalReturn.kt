package conditionalReturn

// RESUME: 20
fun main() {
  headAndTail(setOf(1, 2))
  headAndTail(setOf())
}

fun headAndTail(s: Set<Int>): Pair<Int, Int>? {
  //Breakpoint!, conditionalReturn = true
  val head = s.firstOrNull() ?: return null
  val tail = s.last()
  return Pair(head, tail)
}
