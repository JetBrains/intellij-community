// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test.sequence.exec

import com.intellij.debugger.streams.lib.LibrarySupportProvider
import org.jetbrains.kotlin.idea.debugger.sequence.lib.sequence.KotlinSequenceSupportProvider

abstract class AbstractSequenceTraceTestCase : KotlinTraceTestCase() {
    override fun fragmentCompilerBackend() = FragmentCompilerBackend.JVM
    override fun useIrBackend(): Boolean = false

    override val librarySupportProvider: LibrarySupportProvider = KotlinSequenceSupportProvider()
}