package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.Constants

internal class SyntaxNodeReferenceDescriptor(
  private val ruleId: Int,
  private val syntaxTable: TextMateSyntaxTableCore,
) : SyntaxNodeDescriptor {
  override val children: List<SyntaxNodeDescriptor>
    get() = syntaxTable.getRule(ruleId).children

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

  override fun toString(): String {
    return "Proxy rule for $ruleId"
  }
}
