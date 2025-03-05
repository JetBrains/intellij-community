package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.Constants

internal class SyntaxNodeReferenceDescriptor(
  private val ruleId: Int,
  private val syntaxTable: TextMateSyntaxTableCore,
) : SyntaxNodeDescriptor {
  override val scopeName: CharSequence?
    get() = syntaxTable.getRule(ruleId).scopeName

  override val children: List<SyntaxNodeDescriptor>
    get() = syntaxTable.getRule(ruleId).children

  override val injections: List<InjectionNodeDescriptor>
    get() = syntaxTable.getRule(ruleId).injections

  override fun getStringAttribute(key: Constants.StringKey): CharSequence? {
    return syntaxTable.getRule(ruleId).getStringAttribute(key)
  }

  override fun hasBackReference(key: Constants.StringKey): Boolean {
    return syntaxTable.getRule(ruleId).hasBackReference(key)
  }

  override fun hasBackReference(key: Constants.CaptureKey, group: Int): Boolean {
    return syntaxTable.getRule(ruleId).hasBackReference(key, group)
  }

  override fun getCaptureRules(key: Constants.CaptureKey): Array<TextMateCapture?>? {
    return syntaxTable.getRule(ruleId).getCaptureRules(key)
  }

  @Deprecated("node doesn't hold repository anymore")
  override fun findInRepository(ruleId: Int): SyntaxNodeDescriptor {
    return SyntaxNodeDescriptor.EMPTY_NODE
  }

  @Deprecated("node doesn't hold parent reference anymore")
  override val parentNode: SyntaxNodeDescriptor?
    get() = null

  override fun toString(): String {
    return "Proxy rule for $ruleId"
  }
}
