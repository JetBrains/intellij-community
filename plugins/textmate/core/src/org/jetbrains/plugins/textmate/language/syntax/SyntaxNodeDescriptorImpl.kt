package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.Constants

internal class SyntaxNodeDescriptorImpl(
  override val scopeName: CharSequence?,
  override val children: List<SyntaxNodeDescriptor>,
  override val injections: List<InjectionNodeDescriptor>,
  private val captures: Array<Array<TextMateCapture?>?>?,
  private val stringAttributes: Array<CharSequence?>?,
) : SyntaxNodeDescriptor {
  init {
    require(stringAttributes == null || stringAttributes.size == Constants.StringKey.entries.size) {
      "stringAttributes must be either null or define all StringKey entries"
    }
    require(captures == null || captures.size == Constants.CaptureKey.entries.size) {
      "captures must be either null or define all CaptureKey entries"
    }
  }

  override fun getStringAttribute(key: Constants.StringKey): CharSequence? {
    return stringAttributes?.getOrNull(key.ordinal)
  }

  override fun getCaptureRules(key: Constants.CaptureKey): Array<TextMateCapture?>? {
    return captures?.getOrNull(key.ordinal)
  }

  override fun hasBackReference(key: Constants.StringKey): Boolean {
    return true
  }

  override fun hasBackReference(key: Constants.CaptureKey, group: Int): Boolean {
    return true
  }

  @Deprecated("node doesn't hold repository anymore")
  override fun findInRepository(ruleId: Int): SyntaxNodeDescriptor {
    return SyntaxNodeDescriptor.EMPTY_NODE
  }

  @Deprecated("node doesn't hold parent reference anymore")
  override val parentNode: SyntaxNodeDescriptor?
    get() = null

  override fun toString(): String {
    val name = getStringAttribute(Constants.StringKey.NAME)
    return if (name != null) "Syntax rule: $name" else super.toString()
  }
}
