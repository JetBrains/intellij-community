// NEW_NAME: isMarkedNullable2
// RENAME: member

class ConeType

val ConeType.isMar<caret>kedNullable: Boolean get() = TODO()

class FirRef(val coneType: ConeType)

fun test1(firRef: FirRef) {
    firRef.coneType.isMarkedNullable
}

fun FirRef.test2() {
    coneType
}