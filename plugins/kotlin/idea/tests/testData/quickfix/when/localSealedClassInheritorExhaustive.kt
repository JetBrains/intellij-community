// "Add else branch" "false"
// KT-63795
// K2_AFTER_ERROR: SEALED_SUPERTYPE_IN_LOCAL_CLASS
// K2_ERROR: SEALED_SUPERTYPE_IN_LOCAL_CLASS

sealed class SealedClass

class SealedClassInheritor1 : SealedClass()
class SealedClassInheritor2 : SealedClass()

fun test(sealedClass: SealedClass): String {
    class LocalInheritor : SealedClass()

    return w<caret>hen (sealedClass) {
        is SealedClassInheritor1 -> "1"
        is SealedClassInheritor2 -> "2"
    }
}


// K1 analysis runs into a lazy value recursion exception.
