package com.jetbrains.python.sdk.poetry

import com.intellij.execution.Location
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import org.toml.lang.psi.TomlKey

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PoetryInstallExtras : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val tomlKey = e.dataContext.getData(Location.DATA_KEY)?.psiElement as? TomlKey ?: return
    val project = e.project ?: return
    val pythonSdk = project.pythonSdk ?: return
    project.modules.firstOrNull { pythonSdk.isAssociatedWithModule(it) }?.let {
      runPoetryInBackground(it, listOf("install", "--extras", tomlKey.text), "installing ${tomlKey.text}")
    }
  }

  companion object {
    const val actionID = "poetryInstallExtras"
  }
}