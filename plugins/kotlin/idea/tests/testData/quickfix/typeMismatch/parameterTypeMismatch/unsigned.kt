// "Change parameter 'u' type of function 'takeUInt' to 'Int'" "true"
// WITH_STDLIB

fun takeUInt(u: UInt) = 0

val b = takeUInt(<caret>1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix