package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.Constants

/**
 * Syntax rule of languages from TextMate bundle.
 *
 * Consists of:
 *  - String attributes - string attributes of syntax node [Constants.StringKey]
 *  - Captures attributes - captures attributes of syntax node [Constants.CaptureKey]
 *  - Children rules - set of nested syntax rules (from 'patterns' node)
 */
interface SyntaxNodeDescriptor {
  fun getStringAttribute(key: Constants.StringKey): CharSequence?

  fun hasBackReference(key: Constants.StringKey): Boolean

  fun getCaptureRules(key: Constants.CaptureKey): Array<TextMateCapture?>?

  fun hasBackReference(key: Constants.CaptureKey, group: Int): Boolean

  val children: List<SyntaxNodeDescriptor>

  val injections: List<InjectionNodeDescriptor>

  @Deprecated("node doesn't hold repository anymore")
  fun findInRepository(ruleId: Int): SyntaxNodeDescriptor

  /**
   * @return scope name if node is root for language or null otherwise
   */
  val scopeName: CharSequence?

  @get:Deprecated("node doesn't hold parent reference anymore")
  val parentNode: SyntaxNodeDescriptor?

  companion object {
    @JvmField
    val EMPTY_NODE: SyntaxNodeDescriptor = SyntaxNodeDescriptorImpl(null,
                                                                    emptyList(),
                                                                    emptyList(),
                                                                    emptyArray(),
                                                                    emptyArray())
  }
}
