package org.jetbrains.plugins.textmate.plist

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class JsonOrXmlPlistReaderTest {
  @Test
  fun parseJson() {
    val read = createReader().read("{}".encodeToByteArray())
    assertNotNull(read)
  }

  @Test
  fun parseJsonWithNewline() {
    val read = createReader().read("\n\n{}".encodeToByteArray())
    assertNotNull(read)
  }

  @Test
  fun parseXml() {
    val read = createReader().read(("""
       <?xml version="1.0" encoding="UTF-8"?>
       <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
       <plist version="1.0">
       <dict><key>someKey</key><string>someValue</string></dict></plist>
     """.trimIndent().encodeToByteArray()))
    assertNotNull(read)
  }

  @Test
  fun parseUnknown() {
    assertFailsWith(IllegalArgumentException::class) {
      createReader().read("!!!".encodeToByteArray())
    }
  }

  private fun createReader(): JsonOrXmlPlistReader = JsonOrXmlPlistReader(jsonReader = JsonPlistReader(), xmlReader = XmlPlistReaderForTests())
}
