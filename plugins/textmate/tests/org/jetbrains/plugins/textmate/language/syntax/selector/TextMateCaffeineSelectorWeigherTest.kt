package org.jetbrains.plugins.textmate.language.syntax.selector

class TextMateCaffeineSelectorWeigherTest : TextMateSelectorWeigherTestCase() {
  override fun <T> withWeigher(body: (TextMateSelectorWeigher) -> T): T {
    return body(TextMateSelectorCachingWeigher(TextMateSelectorWeigherImpl()))
  }
}
