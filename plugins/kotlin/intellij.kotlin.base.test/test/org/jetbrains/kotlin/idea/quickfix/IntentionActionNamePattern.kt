// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.text.StringUtil
import java.util.regex.Pattern

/**
 * Parse intention action name in the test file header, e.g.:
 * // "/(Create member function 'T.bar')|(Create member function 'X.bar')/" "true"
 * will be parsed as "Create member function 'T.bar'" or "Create member function 'X.bar'" pattern
 * [findActionByPattern] method will match this pattern against a list of available intentions.
 * This method can be called several times, minimizing patterns recompilations
 */
class IntentionActionNamePattern(text: String) {
    val pattern: Pattern = if (text.startsWith("/"))
        Pattern.compile(text.substring(1, text.length - 1))
    else
        Pattern.compile(StringUtil.escapeToRegexp(text))

    fun findActionByPattern(availableActions: List<IntentionAction>, acceptMatchByFamilyName: Boolean): IntentionAction? =
        findActionsByPattern(availableActions, acceptMatchByFamilyName).firstOrNull()

    fun findActionsByPattern(availableActions: List<IntentionAction>, acceptMatchByFamilyName: Boolean): List<IntentionAction> =
        availableActions.filter {
            pattern.matcher(it.text).matches() || (acceptMatchByFamilyName && pattern.matcher(it.familyName).matches())
        }
}