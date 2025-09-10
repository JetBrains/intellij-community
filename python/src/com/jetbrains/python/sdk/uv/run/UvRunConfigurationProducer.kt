// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.PythonRunConfigurationProducer
import com.jetbrains.python.run.RunnableScriptFilter
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.uv.isUv

internal class UvRunConfigurationProducer : LazyRunConfigurationProducer<UvRunConfiguration>(), DumbAware {
  override fun getConfigurationFactory(): ConfigurationFactory {
    val type = ConfigurationTypeUtil.findConfigurationType(UvRunConfigurationType::class.java)
    return type.configurationFactories.first()
  }

  override fun setupConfigurationFromContext(configuration: UvRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
    val location: Location<*> = context.location ?: return false
    val script: PsiFile = location.psiElement.containingFile ?: return false
    if (!isAvailable(location, script)) return false

    val module: Module? = ModuleUtilCore.findModuleForPsiElement(script)
    val uvSdk = module?.pythonSdk?.takeIf { it.isUv } ?: context.project.pythonSdk?.takeIf { it.isUv }
    if (uvSdk == null) return false

    val vFile: VirtualFile = script.virtualFile ?: return false

    configuration.options = configuration.options.copy(
      runType = UvRunType.SCRIPT,
      scriptOrModule = vFile.path,
      uvSdkKey = uvSdk.name,
    )

    val parent = vFile.parent
    if (parent != null && StringUtil.isEmpty(configuration.workingDirectory)) {
      configuration.workingDirectory = parent.path
    }

    if (module != null) {
      configuration.isUseModuleSdk = true
      configuration.module = module
    }

    configuration.setGeneratedName()
    return true
  }

  override fun isConfigurationFromContext(configuration: UvRunConfiguration, context: ConfigurationContext): Boolean {
    val location: Location<*> = context.location ?: return false
    val script: PsiFile = location.psiElement.containingFile ?: return false
    if (!isAvailable(location, script)) return false

    val vFile: VirtualFile = script.virtualFile ?: return false
    if (vFile is LightVirtualFile) return false

    val uvSdk = (ModuleUtilCore.findModuleForPsiElement(script))?.pythonSdk?.takeIf { it.isUv }
                ?: context.project.pythonSdk?.takeIf { it.isUv }
                ?: return false

    return configuration.options.scriptOrModule == vFile.path && configuration.options.uvSdkKey == uvSdk.name
  }

  override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
    return other.isProducedBy(PythonRunConfigurationProducer::class.java)
  }

  override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
    return other.isProducedBy(PythonRunConfigurationProducer::class.java)
  }

  private fun isAvailable(location: Location<*>, script: PsiFile?): Boolean {
    if (script == null || script.fileType != PythonFileType.INSTANCE || !script.viewProvider.baseLanguage.isKindOf(PythonLanguage.INSTANCE)) {
      return false
    }
    val module = ModuleUtilCore.findModuleForPsiElement(script)
    if (module != null) {
      for (f in RunnableScriptFilter.EP_NAME.extensionList) {
        if (f.isRunnableScript(script, module, location, TypeEvalContext.userInitiated(location.project, null))) {
          return false
        }
      }
    }
    return true
  }
}