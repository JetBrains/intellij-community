// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiDirectory
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

/**
 * Allows disabling "New -> Kotlin Class/File" action where it's normally available, or enabling it outside of Kotlin source roots.
 * @see com.intellij.ide.actions.CreateJavaClassActionAvailabilityOverride
 */
@ApiStatus.Internal
interface NewKotlinFileActionAvailabilityOverride {
    fun isActionAvailable(directories: Array<PsiDirectory>): ThreeState

    companion object {
        private val EP_NAME: ExtensionPointName<NewKotlinFileActionAvailabilityOverride> =
            ExtensionPointName.create("org.jetbrains.kotlin.newKotlinFileActionAvailabilityOverride")

        fun getAvailabilityOverride(directories: Array<PsiDirectory>): ThreeState {
            for (extension in EP_NAME.extensionList) {
                val override = extension.isActionAvailable(directories)
                if (override != ThreeState.UNSURE) return override
            }
            return ThreeState.UNSURE
        }
    }
}
