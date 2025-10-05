package org.jetbrains.plugins.textmate.language.syntax

import org.jetbrains.plugins.textmate.Constants
import kotlin.jvm.JvmField

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

  companion object {
    @JvmField
    val EMPTY_NODE: SyntaxNodeDescriptor = object : SyntaxNodeDescriptor {
      override fun getStringAttribute(key: Constants.StringKey): CharSequence? {
        return null
      }

      override fun hasBackReference(key: Constants.StringKey): Boolean {
        return false
      }

      override fun getCaptureRules(key: Constants.CaptureKey): Array<TextMateCapture?>? {
        return null
      }

      override fun hasBackReference(key: Constants.CaptureKey, group: Int): Boolean {
        return false
      }

      override val children: List<SyntaxNodeDescriptor> = emptyList()
    }
  }
}
