package org.jetbrains.plugins.textmate.language.syntax.selector

class TextMateSelectorWeigherImplTest : TextMateSelectorWeigherTestCase() {
  override fun <T> withWeigher(body: (TextMateSelectorWeigher) -> T): T {
    return body(TextMateSelectorWeigherImpl())
  }
}
