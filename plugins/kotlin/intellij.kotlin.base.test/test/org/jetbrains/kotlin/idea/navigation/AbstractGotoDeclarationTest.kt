// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

abstract class AbstractGotoDeclarationTest : AbstractGotoActionTest() {
    override val actionName: String get() = "GotoDeclaration"
}