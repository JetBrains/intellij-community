// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard

import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import com.intellij.AbstractBundle
import org.jetbrains.annotations.Nls

@NonNls
private const val BUNDLE = "messages.KotlinNewProjectWizardBundle"

object KotlinNewProjectWizardBundle : AbstractBundle(BUNDLE) {
    @Nls
    @JvmStatic
    fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)

}