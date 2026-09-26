@file:Suppress("UNUSED_PARAMETER")

package com.intellij.tools.apiDump.testData.defaultParameters

interface KtInterface<T> {

  fun function(abc: Int = 69, def: Int, fgh: Int = 420)

  fun genericFunction(abc: Int = 69, def: T, fgh: Int = 420): T
}

abstract class KtMiddleClass<T : Number> : KtInterface<T>

class KtChildClass : KtMiddleClass<Double>() {

  override fun function(abc: Int, def: Int, fgh: Int) {}

  override fun genericFunction(abc: Int, def: Double, fgh: Int): Double {
    TODO("")
  }
}

fun function(abc: Int = 69, def: Int, fgh: Int = 420) {}
