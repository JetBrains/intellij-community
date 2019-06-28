package org.intellij.plugins.markdown.ui.preview.javafx;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;

public class ImageRefreshFixTest extends BasePlatformTestCase {

  public void testSimple() throws IOException {
    final VirtualFile file = myFixture.getTempDirFixture().createFile("test.png");
    assertNotNull(file);

    String html = "<html>\n" +
                  "<body>\n" +
                  "<p>\n" +
                  "<a href=\"file:/Users/foo/project/qoo.md#booboo\">Link!</a>\n" +
                  "Text text\n" +
                  "</p>\n" +
                  "\n" +
                  "<p>\n" +
                  "  <img src=\"file:{0}\" alt=\"alt text\" />\n" +
                  "  <img src=\"file:{0}\" alt=\"alt text\" />\n" +
                  "  <img src=\"https://{0}\" alt=\"alt text\" />\n" +
                  "</p>\n" +
                  "</body>\n" +
                  "</html>";
    html = StringUtil.replace(html, "{0}", file.getPath());

    final String html1 = ImageRefreshFix.setStamps(html);
    assertNotSame(html, html1);

    WriteAction.run(() -> VfsUtil.saveText(file, "ololo"));
    final String html2 = ImageRefreshFix.setStamps(html);
    assertNotSame(html1, html2);

    final String html3 = ImageRefreshFix.setStamps(html);
    assertSameLines(html2, html3);
  }
}
