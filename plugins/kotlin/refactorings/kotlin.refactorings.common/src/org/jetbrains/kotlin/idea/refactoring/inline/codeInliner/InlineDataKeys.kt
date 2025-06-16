// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement

object InlineDataKeys {
    // keys below are used on expressions
    @JvmStatic
    val USER_CODE_KEY = Key<Unit>("USER_CODE")

    @JvmStatic
    val RECEIVER_VALUE_KEY = Key<Unit>("RECEIVER_VALUE")

    @JvmStatic
    val WAS_FUNCTION_LITERAL_ARGUMENT_KEY = Key<Unit>("WAS_FUNCTION_LITERAL_ARGUMENT")

    @JvmStatic
    val WAS_CONVERTED_TO_FUNCTION_KEY = Key<Unit>("WAS_CONVERTED_TO_FUNCTION")

    @JvmStatic
    val NEW_DECLARATION_KEY = Key<Unit>("NEW_DECLARATION")

    // these keys are used on KtValueArgument
    @JvmStatic
    val MAKE_ARGUMENT_NAMED_KEY = Key<Unit>("MAKE_ARGUMENT_NAMED")

    @JvmStatic
    val DEFAULT_PARAMETER_VALUE_KEY = Key<Unit>("DEFAULT_PARAMETER_VALUE")

    @JvmStatic
    val PARAMETER_VALUE_KEY = Key<Name>("PARAMETER_VALUE")

    @JvmStatic
    val NON_LOCAL_JUMP_KEY = Key<NonLocalJumpToken>("NON_LOCAL_JUMP")

    fun clearUserData(it: KtElement) {
        it.putCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY, null)
        it.putCopyableUserData(USER_CODE_KEY, null)
        it.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, null)
        it.putCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY, null)
        it.putCopyableUserData(CodeToInline.FAKE_SUPER_CALL_KEY, null)

        it.putCopyableUserData(RECEIVER_VALUE_KEY, null)
        it.putCopyableUserData(WAS_FUNCTION_LITERAL_ARGUMENT_KEY, null)
        it.putCopyableUserData(NEW_DECLARATION_KEY, null)
        it.putCopyableUserData(MAKE_ARGUMENT_NAMED_KEY, null)
        it.putCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY, null)
        it.putCopyableUserData(PARAMETER_VALUE_KEY, null)
        it.putCopyableUserData(NON_LOCAL_JUMP_KEY, null)
    }
}