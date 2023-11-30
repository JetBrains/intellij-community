package inlineOnlyFunctions

fun main() {
  // STEP_OVER: 1
  //Breakpoint!
  1.also {
    println(it)
  }.also { // In this example the entire line with `also` should be highlighted.
    println(it)
  }

  // RESUME: 1
  1.also {
    // STEP_OVER: 1
    //Breakpoint!
    println(it)
  }.also { // Now we should highlight the line before the curly brace, since we are still inside the `also` inline lambda.
    println(it)
  }
}
