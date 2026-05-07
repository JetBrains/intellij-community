package org.jetbrains.plugins.textmate.plist

import org.jetbrains.plugins.textmate.plist.PListValue.Companion.array
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.bool
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.dict
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.integer
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.real
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.string
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class YamlPlistReaderTest {
  @Test
  fun parseArray() {
    val plist = read("""
      list:
        - dmitro
        - batko
        - 239
    """.trimIndent())
    val map = mapOf("list" to array(string("dmitro"), string("batko"), integer(239)))
    assertEquals(Plist(map), plist)
  }

  @Test
  fun parseBasicTypes() {
    val plist = read("""
      string: someValue
      stringNumber: "42"
      stringBoolean: "true"
      boolean: true
      integer: 124
      real: 145.3
    """.trimIndent())

    assertEquals(string("someValue"), plist.getPlistValue("string"))
    assertEquals(string("42"), plist.getPlistValue("stringNumber"))
    assertEquals(string("true"), plist.getPlistValue("stringBoolean"))
    assertEquals(bool(true), plist.getPlistValue("boolean"))
    assertEquals(integer(124), plist.getPlistValue("integer"))
    assertEquals(real(145.3), plist.getPlistValue("real"))
  }

  @Test
  fun parseInnerDict() {
    val plist = read("""
      dict:
        name: dmitro
        lastname: batko
        age: 239
    """.trimIndent())
    val inner = mapOf(
      "name" to string("dmitro"),
      "lastname" to string("batko"),
      "age" to integer(239),
    )
    val map = mapOf("dict" to dict(Plist(inner)))

    assertEquals(Plist(map), plist)
  }

  @Test
  fun parseYamlViaCompositeReader() {
    val read = createReader().read("""
      someKey: someValue
      list:
        - first
        - second
    """.trimIndent().encodeToByteArray())
    assertNotNull(read)
  }

  @Test
  fun parseYamlWithCommentViaCompositeReader() {
    val read = createReader().read("""
      # yaml comment
      someKey: someValue
    """.trimIndent().encodeToByteArray())
    assertNotNull(read)
  }

  private fun read(string: String): Plist {
    return YamlPlistReader().read(string.encodeToByteArray())
  }

  private fun createReader(): JsonOrXmlOrYamlPlistReader {
    return JsonOrXmlOrYamlPlistReader(xmlReader = XmlPlistReader(), yamlReader = YamlPlistReader())
  }
}
