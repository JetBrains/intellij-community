// ISSUE: KTIJ-23069
// "Add type 'Int' to parameter 'one'" "true"
// K2_ERROR: An explicit type is required on a value parameter.
// K2_ERROR: An explicit type is required on a value parameter.

data class dataClass(val on<caret>e = 1)


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypeAnnotationToValueParameterFixFactory$AddTypeAnnotationToValueParameterFix