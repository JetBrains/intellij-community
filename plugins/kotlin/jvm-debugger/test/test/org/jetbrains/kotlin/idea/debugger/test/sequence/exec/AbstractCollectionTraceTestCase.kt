// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test.sequence.exec

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import org.jetbrains.kotlin.idea.debugger.sequence.lib.collections.KotlinCollectionSupportProvider

@Suppress("unused")
abstract class AbstractCollectionTraceTestCase : KotlinTraceTestCase() {
    override val librarySupportProvider: LibrarySupportProvider = KotlinCollectionSupportProvider()
}