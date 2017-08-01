fun main(args: Array<String>) {
  val toAr<caret>ray = listOf(1, 2, 3).asSequence().filter { it % 2 == 1 }.map { it * it }.toList()
}