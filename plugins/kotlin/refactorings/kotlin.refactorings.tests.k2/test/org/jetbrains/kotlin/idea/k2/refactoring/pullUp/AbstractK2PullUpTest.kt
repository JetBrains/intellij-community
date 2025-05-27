// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import org.jetbrains.kotlin.idea.refactoring.pullUp.AbstractPullUpTest

abstract class AbstractK2PullUpTest : AbstractPullUpTest() {
    override fun getSuffix(): String = "k2"
}
