/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.psi.impl

import com.intellij.debugger.streams.lib.LibraryManager
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.js.descriptorUtils.getJetTypeFqName
import org.jetbrains.kotlin.psi.*

/**
 * @author Vitaliy.Bibaev
 */
class KotlinJavaStreamChainBuilder : KotlinChainBuilderBase() {
  override val existenceChecker: ExistenceChecker = MyExistenceChecker()

  override fun build(startElement: PsiElement): List<StreamChain> {
    val visitor = MyBuilderVisitor()
    var element = getLatestElementInScope(startElement)
    while (element != null) {
      element.accept(visitor)
      element = toUpperLevel(element)
      element = if (element == null) null else getLatestElementInScope(element)
    }

    visitor.chains()

    return super.build(startElement)
  }

  private class MyExistenceChecker : ExistenceChecker() {
    override fun visitCallExpression(expression: KtCallExpression) {
      // TODO: make the check more sophisticated
      val name = expression.analyze().getType(expression)!!.getJetTypeFqName(false)
      if (LibraryManager.getInstance(expression.project).isPackageSupported(StringUtil.getPackageName(name))) {
        fireElementFound()
      }
    }
  }

  private class MyBuilderVisitor : MyTreeVisitor() {
    private val myTerminationCalls = mutableSetOf<KtCallExpression>()
    private val myPreviousCalls = mutableMapOf<KtCallExpression, KtCallExpression>()
    override fun visitCallExpression(expression: KtCallExpression) {
      super.visitCallExpression(expression)
    }

    fun chains(): List<List<KtCallExpression>> {
      return emptyList()
    }
  }
}
