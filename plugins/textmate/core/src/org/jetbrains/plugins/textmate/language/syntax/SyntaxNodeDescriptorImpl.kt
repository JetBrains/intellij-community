package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.Constants

internal class SyntaxNodeDescriptorImpl(
  override val scopeName: CharSequence?,
  override val children: List<SyntaxNodeDescriptor>,
  override val injections: List<InjectionNodeDescriptor>,
  private val captures: Array<Array<TextMateCapture?>?>?,
  private val stringAttributes: Array<CharSequence?>?,
) : SyntaxNodeDescriptor {
  override fun getStringAttribute(key: Constants.StringKey): CharSequence? {
    return stringAttributes?.get(key.ordinal)
  }

  override fun getCaptureRules(key: Constants.CaptureKey): Array<TextMateCapture?>? {
    return captures?.get(key.ordinal)
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
