// "Add constructor parameters from Base(T, String, Base<T, String>?)" "true"
interface I

open class Base<T1, T2>(p1: T1, p2: T2, p3: Base<T1, T2>?)

class C<T> : I, Base<T, String><caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// IGNORE_K2