// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CompletionStatsPolicy {
    companion object {
        val Instance = LanguageExtension<CompletionStatsPolicy>("com.intellij.stats.completion.policy")

        fun isStatsLogDisabled(language: Language): Boolean {
            val policy = Instance.forLanguage(language) ?: return false
            return policy.isStatsLogDisabled()
        }
    }

    fun isStatsLogDisabled(): Boolean
}