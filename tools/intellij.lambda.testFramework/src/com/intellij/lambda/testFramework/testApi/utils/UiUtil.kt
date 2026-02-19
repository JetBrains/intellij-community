package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JLabel
import javax.swing.text.JTextComponent

fun Component.containsText(text: String): Boolean {
  return UIUtil.uiTraverser(this)
    .any { it.getText()?.let { text -> StringUtil.removeHtmlTags(text) }?.contains(text) ?: false }
}

fun Component.containsText(regex: Regex): Boolean {
  return UIUtil.uiTraverser(this)
    .any { it.getText()?.let { text -> StringUtil.removeHtmlTags(text) }?.let { text -> regex.find(text) != null } ?: false }
}

fun Component.getAllText(): List<String> {
  return UIUtil.uiTraverser(this)
    .mapNotNull { it.getText() }.toList()
}

private fun Component.getText() : String? {
  return when (this) {
    is JTextComponent -> text
    is JLabel -> text
    is SimpleColoredComponent -> getCharSequence(true).toString()
    // TODO add other text components if needed
    else -> {
      frameworkLogger.info("No text support for $this")
      null
    }
  }
}
