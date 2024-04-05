package inlineOnlyFunctions

fun main() {
  //Breakpoint!
  1.also {
    println(it)
  }.also { // First we should highlight the line before the curly brace, since we are still inside the `also` inline lambda.
           // Then we step out of it and the entire line with `also` should be highlighted.
    println(it)
  }
}

// STEP_OVER: 99
