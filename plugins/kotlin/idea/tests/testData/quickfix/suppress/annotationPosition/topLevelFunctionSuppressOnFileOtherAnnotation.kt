// "Suppress 'DIVISION_BY_ZERO' for file ${file}" "true"
// ERROR: This annotation is not applicable to target 'file' and use site target '@file'
// K2_ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET
// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET

@file:Deprecated("Some")

package test

public fun foo() = 2 / <caret>0

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction