// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test.sequence.exec

import com.intellij.debugger.streams.lib.LibrarySupportProvider
import org.jetbrains.kotlin.idea.debugger.sequence.lib.sequence.KotlinSequenceSupportProvider

abstract class AbstractIrSequenceTraceTestCase : KotlinTraceTestCase() {
    override fun fragmentCompilerBackend() = FragmentCompilerBackend.JVM

    override val librarySupportProvider: LibrarySupportProvider = KotlinSequenceSupportProvider()
}

abstract class AbstractIrSequenceTraceWithIREvaluatorTestCase : AbstractIrSequenceTraceTestCase() {
    override fun fragmentCompilerBackend() = FragmentCompilerBackend.JVM_IR
}