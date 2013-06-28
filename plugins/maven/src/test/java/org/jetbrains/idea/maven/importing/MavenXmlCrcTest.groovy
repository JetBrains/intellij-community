package org.jetbrains.idea.maven.importing

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.utils.MavenUtil

/**
 * @author Sergey Evdokimov
 */
class MavenXmlCrcTest extends TestCase {

  public void testCrc() {
    same("""
<project a="a" b="b">
</project>
""", """
<project a="a"
         b="b"   >
</project>
""");

    same("""
<project>
  <build><a>value</a></build>
</project>
""", """
<project>
  <build>
    <a>value</a>
  </build>
</project>
""")

    same("""
<project></project>
""", """
<project>
      <!-- comment -->
</project>
"""
    );

    different("""
<project>
  <a>foo</a>
</project>
""", """
<project>
  <a>foo</a> text
</project>
""")

    different("""
<project>
  <a>foo</a>
</project>
""", """
<project>
  <b>foo</b>
</project>
""")

    same("""
<project>
  <a>foo</a>
</project>
""", """
<project>
  <a><!-- comment -->foo<!-- comment --></a>
</project>
""")

    same("""
<project>
  <a>foo</a>
</project>
""", """
<project>
  <a>
  <!-- comment -->foo<!-- comment -->
  </a>
</project>
""")

    same("""
<project>
  <a>foo</a>
</project>
""", """
<project>
  <a>fo<!-- comment -->o</a>
</project>
""")

    same("""
<project>
  ab <a/>cd
</project>
""", """
<project>
  ab <a/> <!-- c --> cd
</project>
""")

    different("""
<project>
  <a>foo bar</a>
</project>
""", """
<project>
  <a>foo    bar</a>
</project>
""")

    different("""
<project>
  <a>foo bar</a>
</project>
""", """
<project>
  <a>foo\tbar</a>
</project>
""")

    different("""
<project>
  <a>111</a>
</project>
""", """
<project>
  <a>222</a>
</project>
""")

  }

  private static void same(@Language("XML") String xml1, @Language("XML") String xml2) {
    int crc1 = MavenUtil.crcWithoutSpaces(xml1);
    int crc2 = MavenUtil.crcWithoutSpaces(xml2);

    assert crc1 == crc2;
  }

  private static void different(@Language("XML") String xml1, @Language("XML") String xml2) {
    int crc1 = MavenUtil.crcWithoutSpaces(xml1);
    int crc2 = MavenUtil.crcWithoutSpaces(xml2);

    assert crc1 != crc2;
  }


}
