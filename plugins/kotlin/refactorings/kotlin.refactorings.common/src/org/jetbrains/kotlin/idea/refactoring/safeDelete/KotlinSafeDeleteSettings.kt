// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.psi.UserDataProperty

object KotlinSafeDeleteSettings {
    @set:TestOnly
    var Project.ALLOW_LIFTING_ACTUAL_PARAMETER_TO_EXPECTED
            by NotNullableUserDataProperty(Key.create("ALLOW_LIFTING_ACTUAL_PARAMETER_TO_EXPECTED"), true)

    var KtDeclaration.dropActualModifier: Boolean? by UserDataProperty(Key.create("DROP_ACTUAL_MODIFIER"))
}