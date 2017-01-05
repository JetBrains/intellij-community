/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.browsers.actions

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.StandardTargetWeights
import com.intellij.ide.browsers.createOpenInBrowserRequest
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xml.XmlBundle
import com.intellij.xml.util.HtmlUtil

private val LOG = Logger.getInstance(SelectInDefaultBrowserTarget::class.java)

internal class SelectInDefaultBrowserTarget : SelectInTarget {
  override fun canSelect(context: SelectInContext): Boolean {
    val selectorInFile = context.selectorInFile as? PsiElement ?: return false
    val request = createOpenInBrowserRequest(selectorInFile) ?: return false
    val urlProvider = WebBrowserServiceImpl.getProvider(request)
    if (urlProvider == null) {
      val virtualFile = request.virtualFile ?: return false
      return virtualFile is HttpVirtualFile || (HtmlUtil.isHtmlFile(request.file) && virtualFile !is LightVirtualFile)
    }
    return true
  }

  override fun toString() = XmlBundle.message("browser.select.in.default.name")

  override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
    BaseOpenInBrowserAction.open(createOpenInBrowserRequest(context.selectorInFile as PsiElement), false, null)
  }

  override fun getWeight() = StandardTargetWeights.OS_FILE_MANAGER
}
