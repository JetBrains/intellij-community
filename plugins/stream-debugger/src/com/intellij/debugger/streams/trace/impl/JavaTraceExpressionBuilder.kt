// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.streams.lib.HandlerFactory
import com.intellij.debugger.streams.psi.impl.MethodReferenceToLambdaTransformer
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.JavaPsiFacade

/**
 * @author Vitaliy.Bibaev
 */
class JavaTraceExpressionBuilder(private val project: Project, handlerFactory: HandlerFactory, dsl: Dsl)
  : TraceExpressionBuilderBase(dsl, handlerFactory) {
  private companion object {
    private val LOG = Logger.getInstance(JavaTraceExpressionBuilder::class.java)
  }

  override fun createTraceExpression(chain: StreamChain): String {
    val codeBlock = super.createTraceExpression(chain)
    val elementFactory = JavaPsiFacade.getElementFactory(project)

    return ApplicationManager.getApplication()
      .runReadAction(Computable<String> {
        val block = elementFactory.createCodeBlockFromText(
          codeBlock, chain.context)

        MethodReferenceToLambdaTransformer.transform(block)

        val resultDeclaration = dsl.declaration(dsl.variable(dsl.types.ANY, resultVariableName), dsl.nullExpression, true).toCode()
        val result = "$resultDeclaration; \n " +
                     "${block.text} \n" +
                     resultVariableName

        LOG.debug("trace expression: \n$result")
        result
      })
  }
}