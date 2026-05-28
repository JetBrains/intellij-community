// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.handler

import com.intellij.debugger.streams.core.wrapper.CallArgument
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.StreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.core.wrapper.TypeAfterAware
import com.intellij.debugger.streams.core.wrapper.TypeBeforeAware
import com.intellij.debugger.streams.core.wrapper.impl.IntermediateStreamCallImpl
import com.intellij.debugger.streams.core.wrapper.impl.TerminatorStreamCallImpl
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinSequenceTypes

@K1Deprecation
fun IntermediateStreamCall.withArgs(args: List<CallArgument>) =
    IntermediateStreamCallImpl(name, genericArguments, args, typeBefore, typeAfter, textRange)

@K1Deprecation
fun TerminatorStreamCall.withArgs(args: List<CallArgument>) =
    TerminatorStreamCallImpl(name, genericArguments, args, typeBefore, resultType, textRange, resultType == JavaTypes.VOID)

@K1Deprecation
fun StreamCall.typeBefore() = if (this is TypeBeforeAware) this.typeBefore else KotlinSequenceTypes.ANY

@K1Deprecation
fun StreamCall.typeAfter() = if (this is TypeAfterAware) this.typeAfter else KotlinSequenceTypes.ANY