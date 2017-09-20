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
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.streams.psi.impl.LambdaToAnonymousTransformer
import com.intellij.debugger.streams.psi.impl.MethodReferenceToLambdaTransformer
import com.intellij.debugger.streams.psi.impl.ToObjectInheritorTransformer
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
class JavaTraceExpressionBuilder(private val project: Project, dsl: Dsl) : TraceExpressionBuilderBase(project, dsl) {
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
        LambdaToAnonymousTransformer.transform(block)
        ToObjectInheritorTransformer.transform(block)

        val resultDeclaration = dsl.declaration(dsl.variable(dsl.types.ANY, resultVariableName), dsl.nullExpression, true).toCode()
        val result = "$resultDeclaration; \n " +
                     "${block.text} \n" +
                     resultVariableName

        LOG.info("trace expression: \n$result")
        result
      })
  }
}