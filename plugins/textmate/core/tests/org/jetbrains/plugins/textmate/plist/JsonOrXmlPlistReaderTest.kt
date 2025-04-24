package org.jetbrains.plugins.textmate.plist

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.jvm.Throws

class JsonOrXmlPlistReaderTest {
  @Test
  @Throws(IOException::class)
  fun parseJson() {
    val read = createReader().read(ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8)))
    assertNotNull(read)
  }

  @Test
  @Throws(IOException::class)
  fun parseJsonWithNewline() {
    val read = createReader().read(ByteArrayInputStream("\n\n{}".toByteArray(Charsets.UTF_8)))
    assertNotNull(read)
  }

  @Test
  @Throws(IOException::class)
  fun parseXml() {
    val read = createReader().read(ByteArrayInputStream(("""
       <?xml version="1.0" encoding="UTF-8"?>
       <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
       <plist version="1.0">
       <dict><key>someKey</key><string>someValue</string></dict></plist>
     """.trimIndent()).toByteArray(Charsets.UTF_8)))
    assertNotNull(read)
  }

  @Test
  fun parseUnknown() {
    assertFailsWith(IOException::class) {
      createReader().read(ByteArrayInputStream("!!!".toByteArray(Charsets.UTF_8)))
    }
  }

  private fun createReader(): JsonOrXmlPlistReader = JsonOrXmlPlistReader(jsonReader = JsonPlistReader(), xmlReader = XmlPlistReaderForTests())
}
