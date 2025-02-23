// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.handler

import com.intellij.debugger.streams.core.wrapper.*
import com.intellij.debugger.streams.core.wrapper.impl.IntermediateStreamCallImpl
import com.intellij.debugger.streams.core.wrapper.impl.TerminatorStreamCallImpl
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinSequenceTypes

fun IntermediateStreamCall.withArgs(args: List<CallArgument>) =
    IntermediateStreamCallImpl(name, genericArguments, args, typeBefore, typeAfter, textRange)

fun TerminatorStreamCall.withArgs(args: List<CallArgument>) =
    TerminatorStreamCallImpl(name, genericArguments, args, typeBefore, resultType, textRange, resultType == JavaTypes.VOID)

fun StreamCall.typeBefore() = if (this is TypeBeforeAware) this.typeBefore else KotlinSequenceTypes.ANY

fun StreamCall.typeAfter() = if (this is TypeAfterAware) this.typeAfter else KotlinSequenceTypes.ANY