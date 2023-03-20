// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.idea.debugger.test.AbstractKotlinExceptionFilterTest

abstract class AbstractK2KotlinExceptionFilterTest : AbstractKotlinExceptionFilterTest() {
    override fun isFirPlugin(): Boolean = true
}