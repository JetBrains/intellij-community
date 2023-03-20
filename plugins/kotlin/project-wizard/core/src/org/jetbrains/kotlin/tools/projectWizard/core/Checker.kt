// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core

typealias Checker = Reader.() -> Boolean

val ALWAYS_AVAILABLE_CHECKER = checker { true }

fun checker(check: Checker) = check

interface ContextOwner {
    val context: Context
}

interface ActivityCheckerOwner {
    val isAvailable: Checker

    fun isActive(reader: Reader) = isAvailable(reader)
}