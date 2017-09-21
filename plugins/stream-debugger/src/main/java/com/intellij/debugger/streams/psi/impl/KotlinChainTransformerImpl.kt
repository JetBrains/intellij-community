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

import com.intellij.debugger.streams.psi.*
import com.intellij.debugger.streams.trace.dsl.impl.kotlin.KotlinTypes
import com.intellij.debugger.streams.wrapper.CallArgument
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.streams.wrapper.impl.*
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.js.descriptorUtils.getJetTypeFqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiverOrThis

/**
 * @author Vitaliy.Bibaev
 */
class KotlinChainTransformerImpl : ChainTransformer.Kotlin {
  override fun transform(callChain: List<KtCallExpression>, context: PsiElement): StreamChain {
    val firstCall = callChain.first()
    val qualifiedExpression = firstCall.getQualifiedExpressionForReceiverOrThis()
    val qualifier = QualifierExpressionImpl(qualifiedExpression.text, qualifiedExpression.textRange, KotlinTypes.ANY)

    val intermediateCalls = mutableListOf<IntermediateStreamCall>()
    for (call in callChain.subList(0, callChain.size - 1)) {
      intermediateCalls += IntermediateStreamCallImpl(call.callName(), call.valueArguments.map { it.toCallArgument() },
                                                      KotlinTypes.ANY, KotlinTypes.ANY, call.textRange,
                                                      call.receiverType()!!.getPackage(false))
    }

    val terminationsPsiCall = callChain.last()
    // TODO: infer true types
    val terminationCall = TerminatorStreamCallImpl(terminationsPsiCall.callName(), emptyList(), KotlinTypes.ANY, KotlinTypes.ANY,
                                                   terminationsPsiCall.textRange, terminationsPsiCall.receiverType()!!.getPackage(false))

    return StreamChainImpl(qualifier, intermediateCalls, terminationCall, context)
  }

  private fun KtValueArgument.toCallArgument(): CallArgument {
    val argExpression = getArgumentExpression()!!
    return CallArgumentImpl(argExpression.resolveType().getJetTypeFqName(true), argExpression.text)
  }
}
