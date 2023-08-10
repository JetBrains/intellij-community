package com.jetbrains.python.sdk.poetry

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons.Actions.Install
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.*

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

object PoetryExtrasLineMarkerContributor : RunLineMarkerContributor() {
  override fun getInfo(element: PsiElement): Info? {
    val sdk = element.project.pythonSdk ?: return null
    if (!sdk.isPoetry) return null
    if (element !is TomlKey) return null
    val keyValue = element.parent as? TomlKeyValue ?: return null
    val header = (keyValue.parent as? TomlTable)?.header ?: return null
    if (header.key?.text != "tool.poetry.extras") return null
    if (keyValue.key.text == null) return null
    val value = keyValue.value as? TomlArray ?: return null
    if (value.elements.isEmpty() || value.elements.any { it !is TomlLiteral || it.textLength < 3 }) return null
    val action = ActionManager.getInstance().getAction(PoetryInstallExtras.actionID)
    return Info(Install, { parameter -> "Install " + ((parameter as? TomlKey)?.text ?: "extra") }, action)
  }
}



