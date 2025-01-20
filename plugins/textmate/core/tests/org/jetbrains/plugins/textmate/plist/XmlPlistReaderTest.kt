package org.jetbrains.plugins.textmate.plist

import org.jetbrains.plugins.textmate.plist.PListValue.Companion.array
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.bool
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.date
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.dict
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.integer
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.real
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.string
import org.jetbrains.plugins.textmate.plist.Plist.Companion.dateFormatter
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.test.assertEquals

class XmlPlistReaderTest {
  @Test
  fun getStringMethod() {
    val plist = read("<dict><key>someKey</key><string>someValue</string></dict>")
    Assert.assertEquals("someValue", plist.getPlistValue("someKey")!!.string)
    Assert.assertEquals("default", plist.getPlistValue("unknown", "default").string)
  }

  @Test
  fun parseString() {
    val plist = read("<dict><key>someKey</key><string>someValue</string><key>escape</key><string>&gt;</string></dict>")
    Assert.assertEquals(2, plist.entries().size.toLong())
    assertEquals(string("someValue"), plist.getPlistValue("someKey"))
    assertEquals(string(">"), plist.getPlistValue("escape"))
    assertEquals(string("default"), plist.getPlistValue("unknown", "default"))
    Assert.assertNull(plist.getPlistValue("unknown"))
  }

  @Test
  fun parseBoolean() {
    val plist = read("<dict><key>true</key><true/><key>false</key><false/></dict>")
    Assert.assertEquals(2, plist.entries().size.toLong())
    assertEquals(bool(true), plist.getPlistValue("true"))
    assertEquals(bool(false), plist.getPlistValue("false"))
    Assert.assertNull(plist.getPlistValue("unknown"))
    assertEquals(bool(true), plist.getPlistValue("unknown", true))
    assertEquals(bool(false), plist.getPlistValue("unknown", false))
  }

  @Test
  fun parseInteger() {
    val plist = read("<dict><key>int</key><integer>124</integer></dict>")
    Assert.assertEquals(1, plist.entries().size.toLong())
    assertEquals(integer(124), plist.getPlistValue("int"))
    Assert.assertNull(plist.getPlistValue("unknown"))
    assertEquals(integer(124), plist.getPlistValue("unknown", 124))
  }

  @Test
  fun parseReal() {
    val plist = read("<dict><key>real</key><real>145.3</real></dict>")
    Assert.assertEquals(1, plist.entries().size.toLong())
    assertEquals(real(145.3), plist.getPlistValue("real"))
    assertEquals(real(120.0), plist.getPlistValue("unknown", 120.0))
    Assert.assertNull(plist.getPlistValue("unknown"))
  }

  @Test
  fun parseDate() {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.MILLISECOND, 0) //Plist doesn't respect to ms
    val date = calendar.getTime()

    val plist = read("<dict><key>date</key><date>" + dateFormatter().format(date) + "</date></dict>")
    Assert.assertEquals(1, plist.entries().size.toLong())
    assertEquals(date(date), plist.getPlistValue("date"))
    Assert.assertNull(plist.getPlistValue("unknown"))
  }

  @Test
  fun parseArray() {
    val plist = read("<dict><key>list</key><array><string>alex</string><string>zolotov</string><integer>42</integer></array></dict>")
    val map = mapOf("list" to array(string("alex"), string("zolotov"), integer(42)))
    assertEquals(Plist(map), plist)
  }

  @Test
  fun parseInnerDict() {
    val plist = read("<dict><key>dict</key><dict>" +
                     "<key>name</key><string>alex</string>" +
                     "<key>lastname</key><string>zolotov</string>" +
                     "<key>age</key><integer>22</integer>" +
                     "</dict></dict>")
    val inner = mapOf(
      "name" to string("alex"),
      "lastname" to string("zolotov"),
      "age" to integer(22))
    val map = mapOf("dict" to dict(Plist(inner)))
    assertEquals(Plist(map), plist)
  }

  @Test
  fun plistWithoutDictRoot() {
    val plist = read("<key>someKey</key><string>someValue</string>")
    Assert.assertEquals(Plist.EMPTY_PLIST, plist)
  }

  @Test
  fun invalidPlist() {
    val plist = read("<dict><key>someKey</key><string>someValue</string>" +
                     "<string>withoutKey</string>" +
                     "<key>someKey2</key>" +
                     "<string>someValue2</string>" +
                     "</dict>")
    val map = mapOf(
      "someKey" to string("someValue"),
      "someKey2" to string("someValue2"))
    assertEquals(Plist(map), plist)
  }

  private fun read(string: String?): Plist {
    return XmlPlistReader().read(ByteArrayInputStream(prepareText(string).toByteArray(Charsets.UTF_8)))
  }

  companion object {
    private fun prepareText(string: String?): String {
      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
             "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
             "<plist version=\"1.0\">\n" +
             string +
             "</plist>"
    }
  }
}
