// ISSUE: KTIJ-23069
// "Add type 'Int' to parameter 'one'" "true"

data class dataClass(val on<caret>e = 1)


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypeAnnotationToValueParameterFixFactory$AddTypeAnnotationToValueParameterFix