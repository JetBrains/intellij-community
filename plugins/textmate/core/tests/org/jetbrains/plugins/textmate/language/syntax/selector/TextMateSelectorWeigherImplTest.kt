package org.jetbrains.plugins.textmate.language.syntax.selector

class TextMateSelectorWeigherImplTest : TextMateSelectorWeigherTestCase() {
  override fun createWeigher(): TextMateSelectorWeigher {
    return TextMateSelectorWeigherImpl()
  }
}
