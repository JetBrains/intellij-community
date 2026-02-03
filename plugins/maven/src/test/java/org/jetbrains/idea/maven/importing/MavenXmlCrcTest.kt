package org.jetbrains.idea.maven.importing

import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.utils.MavenUtil
import org.junit.Test
import java.io.ByteArrayInputStream

class MavenXmlCrcTest : TestCase() {
  @Test
  fun testCrc() = runBlocking {
    same("""
           <project a="a" b="b">
           </project>
           
           """.trimIndent(), """
           <project a="a"
                    b="b"   >
           </project>
           """.trimIndent())

    same("""
           <project>
             <build><a>value</a></build>
           </project>
           
           """.trimIndent(), """
           <project>
             <build>
               <a>value</a>
             </build>
           </project>
           """.trimIndent())

    same("""
           <project></project>
           
           """.trimIndent(), """
           <project>
                 <!-- comment -->
           </project>
           """.trimIndent())

    different("""
                <project>
                  <a>foo</a>
                </project>
                
                """.trimIndent(), """
                <project>
                  <a>foo</a> text
                </project>
                """.trimIndent())

    different("""
                <project>
                  <a>foo</a>
                </project>
                
                """.trimIndent(), """
                <project>
                  <b>foo</b>
                </project>
                """.trimIndent())

    same("""
           <project>
             <a>foo</a>
           </project>
           
           """.trimIndent(), """
           <project>
             <a><!-- comment -->foo<!-- comment --></a>
           </project>
           """.trimIndent())

    same("""
           <project>
             <a>foo</a>
           </project>
           
           """.trimIndent(), """
           <project>
             <a>
             <!-- comment -->foo<!-- comment -->
             </a>
           </project>
           """.trimIndent())

    same("""
           <project>
             <a>foo</a>
           </project>
           
           """.trimIndent(), """
           <project>
             <a>fo<!-- comment -->o</a>
           </project>
           """.trimIndent())

    same("""
           <project>
             ab <a/>cd
           </project>
           
           """.trimIndent(), """
           <project>
             ab <a/> <!-- c --> cd
           </project>
           """.trimIndent())

    different("""
                <project>
                  <a>foo bar</a>
                </project>
                
                """.trimIndent(), """
                <project>
                  <a>foo    bar</a>
                </project>
                """.trimIndent())

    different("""
                <project>
                  <a>foo bar</a>
                </project>
                
                """.trimIndent(), """
                <project>
                  <a>foo${'\t'}bar</a>
                </project>
                """.trimIndent())

    different("""
                <project>
                  <a>111</a>
                </project>
                
                """.trimIndent(), """
                <project>
                  <a>222</a>
                </project>
                """.trimIndent())

    different("""
                <project>
                </project>
                
                """.trimIndent(), """
                < project>
                </project>
                """.trimIndent())

    // All invalid xmls are same
    same("\n", """
      <project>
        <sss>
      </project>
      """.trimIndent())
  }

  fun testInvalidXml() {
    assert(crc("""
                 <   project>
                 </project>
                 """.trimIndent()) == -1)
    assert(crc("""
                 <project>
                 """.trimIndent()) == -1)
    assert(crc("""
                 <project>
                   <sss>
                 </project>
                 """.trimIndent()) == -1)
  }

  companion object {
    private fun crc(text: String): Int {
      return MavenUtil.crcWithoutSpaces(ByteArrayInputStream(text.toByteArray()))
    }

    private fun same(@Language("XML") xml1: String, @Language("XML") xml2: String) {
      val crc1 = crc(xml1)
      val crc2 = crc(xml2)

      assert(crc1 == crc2)
    }

    private fun different(@Language("XML") xml1: String, @Language("XML") xml2: String) {
      val crc1 = crc(xml1)
      val crc2 = crc(xml2)

      assert(crc1 != crc2)
    }
  }
}
