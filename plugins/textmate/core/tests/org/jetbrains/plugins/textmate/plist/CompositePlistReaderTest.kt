package org.jetbrains.plugins.textmate.plist

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CompositePlistReaderTest {
  @Test
  @Throws(IOException::class)
  fun parseJson() {
    val read = CompositePlistReader().read(ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8)))
    assertNotNull(read)
  }

  @Test
  @Throws(IOException::class)
  fun parseJsonWithNewline() {
    val read = CompositePlistReader().read(ByteArrayInputStream("\n\n{}".toByteArray(Charsets.UTF_8)))
    assertNotNull(read)
  }

  @Test
  @Throws(IOException::class)
  fun parseXml() {
    val read = CompositePlistReader().read(ByteArrayInputStream(("""
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
      CompositePlistReader().read(ByteArrayInputStream("!!!".toByteArray(Charsets.UTF_8)))
    }
  }
}
