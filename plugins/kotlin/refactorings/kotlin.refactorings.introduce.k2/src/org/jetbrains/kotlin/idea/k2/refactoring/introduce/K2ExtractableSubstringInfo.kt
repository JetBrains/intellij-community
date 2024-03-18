// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import org.jetbrains.kotlin.idea.refactoring.introduce.ExtractableSubstringInfo
import org.jetbrains.kotlin.psi.KtStringTemplateEntry

class K2ExtractableSubstringInfo(
    startEntry: KtStringTemplateEntry,
    endEntry: KtStringTemplateEntry,
    prefix: String,
    suffix: String,
) : ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix) {
    override val isString: Boolean = true   // TODO: KTIJ-28404

    override fun copy(
        newStartEntry: KtStringTemplateEntry,
        newEndEntry: KtStringTemplateEntry
    ): ExtractableSubstringInfo = K2ExtractableSubstringInfo(newStartEntry, newEndEntry, prefix, suffix)
}