import test.ToBeImportedJava
import test.ToBeImportedJava.TO_BE_IMPORTED_CONST
import test.ToBeImportedJava.staticMethod
import test.ToBeImportedKotlin

class Target {
    var listOfPlatformType: MutableList<String?> = ArrayList<String?>()

    var unresolved: UnresolvedInterface<UnresolvedGeneric?> = UnresolvedImplementation() // Should not add import

    var hashMapOfNotImported: MutableMap<ToBeImportedJava?, ToBeImportedKotlin?> =
        HashMap<ToBeImportedJava?, ToBeImportedKotlin?>()

    fun acceptKotlinClass(tbi: ToBeImportedKotlin?) {
    }

    fun acceptJavaClass(tbi: ToBeImportedJava?) {
    }

    var ambiguousKotlin: IAmbiguousKotlin =
        AmbiguousKotlin() // Should not add import in case of 2 declarations in Kotlin
    var ambiguous: IAmbiguous =
        Ambiguous() // Should not add import in case of ambiguous declarations in Kotlin and in Java
    var ambiguousJava: IAmbiguousJava = AmbiguousJava() // Should not add import in case of 2 declarations in Java

    fun workWithStatics() {
        val a = TO_BE_IMPORTED_CONST
        staticMethod()
    }
}
