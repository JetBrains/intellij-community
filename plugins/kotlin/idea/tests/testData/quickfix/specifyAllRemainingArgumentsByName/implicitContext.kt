// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_ERROR: No value passed for parameter 'a'.
  context(d: Int)
  fun foo(a: Int, b: Int = 5) {}

  context(d: Int)
  fun test() {
      fo<caret>o()
  }

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix