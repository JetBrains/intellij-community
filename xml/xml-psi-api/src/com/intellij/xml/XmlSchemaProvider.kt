// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.annotations.NonNls

abstract class XmlSchemaProvider : PossiblyDumbAware {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<XmlSchemaProvider> = ExtensionPointName<XmlSchemaProvider>("com.intellij.xml.schemaProvider")

    @JvmStatic
    fun getAvailableProviders(file: XmlFile): List<XmlSchemaProvider> {
      return EP_NAME.extensionList.filter { it.isAvailable(file) }
    }

    @JvmStatic
    fun findSchema(namespace: @NonNls String, module: Module?, file: PsiFile): XmlFile? {
      if (file.getProject().isDefault) {
        return null
      }

      for (provider in EP_NAME.extensionList) {
        if (!getInstance(file.getProject()).isUsableInCurrentContext(provider)) {
          continue
        }

        if (file is XmlFile && !provider.isAvailable(file)) {
          continue
        }
        provider.getSchema(namespace, module, file)?.let {
          return it
        }
      }
      return null
    }

    @JvmStatic
    fun findSchema(namespace: @NonNls String, baseFile: PsiFile): XmlFile? {
      return findSchema(namespace = namespace, module = ModuleUtilCore.findModuleForPsiElement(baseFile), file = baseFile)
    }
  }

  abstract fun getSchema(url: @NonNls String, module: Module?, baseFile: PsiFile): XmlFile?

  open fun isAvailable(file: XmlFile): Boolean = false

  /**
   * Provides specific namespaces for given XML file.
   *
   * @param file    XML or JSP file.
   * @param tagName optional
   * @return available namespace uris, or `null` if the provider did not recognize the file.
   */
  open fun getAvailableNamespaces(file: XmlFile, tagName: String?): Set<String> = emptySet()

  open fun getDefaultPrefix(namespace: @NonNls String, context: XmlFile): String? = null

  open fun getLocations(namespace: @NonNls String, context: XmlFile): Set<String>? = null
}
