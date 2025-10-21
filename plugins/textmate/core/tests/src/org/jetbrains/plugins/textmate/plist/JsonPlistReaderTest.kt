package org.jetbrains.plugins.textmate.plist

import org.jetbrains.plugins.textmate.plist.PListValue.Companion.array
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.bool
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.dict
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.integer
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.real
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.string
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonPlistReaderTest {
  @Test
  fun parseArray() {
    val plist = read("{list:[\"alex\",\"zolotov\",42]}")
    val map = mapOf("list" to array(string("alex"), string("zolotov"), integer(42)))
    assertEquals(Plist(map), plist)
  }

  @Test
  fun getStringMethod() {
    val plist = read("{someKey: \"someValue\"}")
    assertEquals("someValue", plist.getPlistValue("someKey")!!.string)
    assertEquals("default", plist.getPlistValue("unknown", "default").string)
  }

  @Test
  fun parseString() {
    val plist = read("{someKey: \"someValue\",anotherKey: \">\"}")
    assertEquals(2, plist.entries().size.toLong())
    assertEquals(string("someValue"), plist.getPlistValue("someKey"))
    assertEquals(string(">"), plist.getPlistValue("anotherKey"))
    assertEquals(string("default"), plist.getPlistValue("unknown", "default"))
    assertNull(plist.getPlistValue("unknown"))
  }

  @Test
  fun parseBoolean() {
    val plist = read("{true: true,false: false}")
    assertEquals(2, plist.entries().size.toLong())
    assertEquals(bool(true), plist.getPlistValue("true"))
    assertEquals(bool(false), plist.getPlistValue("false"))
    assertNull(plist.getPlistValue("unknown"))
    assertEquals(bool(true), plist.getPlistValue("unknown", true))
    assertEquals(bool(false), plist.getPlistValue("unknown", false))
  }

  @Test
  fun parseInteger() {
    val plist = read("{int: 124}")
    assertEquals(1, plist.entries().size.toLong())
    assertEquals(integer(124), plist.getPlistValue("int"))
    assertNull(plist.getPlistValue("unknown"))
    assertEquals(integer(124), plist.getPlistValue("unknown", 124))
  }

  @Test
  fun parseReal() {
    val plist = read("{real: 145.3}")
    assertEquals(1, plist.entries().size.toLong())
    assertEquals(real(145.3), plist.getPlistValue("real"))
    assertEquals(real(120.0), plist.getPlistValue("unknown", 120.0))
    assertNull(plist.getPlistValue("unknown"))
  }


  @Test
  fun parseInnerDict() {
    val plist = read("{dict: {name: \"alex\",lastname: \"zolotov\",age: 22}}")
    val inner = mapOf(
      "name" to string("alex"),
      "lastname" to string("zolotov"),
      "age" to integer(22),
    )
    val map = mapOf("dict" to dict(Plist(inner)))

    assertEquals(Plist(map), plist)
  }

  private fun read(string: String): Plist {
    return JsonPlistReader().read(string.encodeToByteArray())
  }
}
