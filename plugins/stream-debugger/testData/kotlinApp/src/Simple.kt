import java.util.stream.Stream

fun main(args: Array<String>) {
  // Breakpoint!
  val toArray = Stream.of(1, 2, 3, 4).map { it % 2 == 0 }.toArray()
}