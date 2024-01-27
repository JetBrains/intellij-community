package target

import library.KtData
import library.KtEnum

class Foo {
    val javaEnum = JavaEnum.values()
    val ktEnum = KtEnum.values()
    val ktData = KtData()
    val n = KtData(1).component1()
}