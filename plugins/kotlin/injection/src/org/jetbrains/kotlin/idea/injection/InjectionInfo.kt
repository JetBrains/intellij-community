// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.injection

import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import org.intellij.plugins.intelliLang.inject.config.BaseInjection

internal class InjectionInfo(private val languageId: String?, val prefix: String?, val suffix: String?) {
    fun toBaseInjection(injectionSupport: LanguageInjectionSupport): BaseInjection? {
        if (languageId == null) return null

        val baseInjection = BaseInjection(injectionSupport.id)
        baseInjection.injectedLanguageId = languageId

        if (prefix != null) {
            baseInjection.prefix = prefix
        }

        if (suffix != null) {
            baseInjection.suffix = suffix
        }

        return baseInjection
    }

    companion object {
        fun fromBaseInjection(baseInjection: BaseInjection?): InjectionInfo? {
            if (baseInjection == null) {
                return null
            }

            return InjectionInfo(
                baseInjection.injectedLanguageId,
                baseInjection.prefix,
                baseInjection.suffix
            )
        }
    }
}