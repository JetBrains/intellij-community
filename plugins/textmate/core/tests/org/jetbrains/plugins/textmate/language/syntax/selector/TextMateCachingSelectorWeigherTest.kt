package org.jetbrains.plugins.textmate.language.syntax.selector

class TextMateCachingSelectorWeigherTest : TextMateSelectorWeigherTestCase() {
  override fun <T> withWeigher(body: (TextMateSelectorWeigher) -> T): T {
    return withCachingSelectorWeigher(TextMateSelectorWeigherImpl(), body)
  }
}