// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.LibrarySupport
import com.intellij.debugger.streams.lib.LibrarySupportProvider
import com.intellij.debugger.streams.psi.impl.InheritanceBasedChainDetector
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder
import com.intellij.debugger.streams.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.trace.dsl.Lambda
import com.intellij.debugger.streams.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory
import com.intellij.debugger.streams.trace.impl.JavaTraceExpressionBuilder
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.CallArgument
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.StreamCallType
import com.intellij.debugger.streams.wrapper.StreamChainBuilder
import com.intellij.debugger.streams.wrapper.impl.CallArgumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.CommonClassNames

/**
 * @author Vitaliy.Bibaev
 */
class JBIterableSupportProvider : LibrarySupportProvider {
  private companion object {
    const val CLASS_NAME = "com.intellij.util.containers.JBIterable"
  }

  private val librarySupport = JBIterableSupport()
  private val dsl = DslImpl(JBIterableJavaStatementFactory())
  override fun getLanguageId(): String = "JAVA"

  override fun getChainBuilder(): StreamChainBuilder {
    return JavaStreamChainBuilder(JavaChainTransformerImpl(), InheritanceBasedChainDetector(CLASS_NAME))
  }

  override fun getExpressionBuilder(project: Project): TraceExpressionBuilder {
    return JavaTraceExpressionBuilder(project, librarySupport.createHandlerFactory(dsl), dsl)
  }

  override fun getLibrarySupport(): LibrarySupport = librarySupport

  private class JBIterableJavaStatementFactory : JavaStatementFactory() {
    override fun createPeekCall(elementsType: GenericType, lambda: Lambda): IntermediateStreamCall {
      val lambdaBody = createEmptyLambdaBody(lambda.variableName).apply {
        add(lambda.body)
        doReturn(TextExpression("true"))
      }
      val newLambda = createLambda(lambda.variableName, lambdaBody)
      return JBIterablePeekCall(elementsType, newLambda.toCode())
    }
  }

  private class JBIterablePeekCall(private val elementsType: GenericType, private val argText: String) : IntermediateStreamCall {
    override fun getName(): String = "filter"

    override fun getArguments(): List<CallArgument> = listOf(CallArgumentImpl(CommonClassNames.JAVA_LANG_OBJECT, argText))

    override fun getType(): StreamCallType = StreamCallType.INTERMEDIATE

    override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE

    override fun getTypeBefore(): GenericType = elementsType

    override fun getTypeAfter(): GenericType = elementsType
  }
}