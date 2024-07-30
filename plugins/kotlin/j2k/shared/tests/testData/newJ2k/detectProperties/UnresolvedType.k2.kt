// ERROR: Unresolved reference 'Type'.
// ERROR: Unresolved reference 'Type'.
internal interface I {
    val type: Type?
}

internal class C {
    var x: Type? = null
        private set
}
