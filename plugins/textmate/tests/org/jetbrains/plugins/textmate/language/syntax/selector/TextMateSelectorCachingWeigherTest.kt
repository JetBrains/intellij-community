package org.jetbrains.plugins.textmate.language.syntax.selector

class TextMateSelectorCachingWeigherTest : TextMateSelectorWeigherTestCase() {
  override fun createWeigher(): TextMateSelectorWeigher {
    return TextMateSelectorCachingWeigher(TextMateSelectorWeigherImpl())
  }
}
