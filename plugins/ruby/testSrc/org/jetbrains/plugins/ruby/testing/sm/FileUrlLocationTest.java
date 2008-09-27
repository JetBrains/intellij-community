package org.jetbrains.plugins.ruby.testing.sm;

import com.intellij.execution.Location;
import org.jetbrains.plugins.ruby.rails.RailsFixtureTestCase;
import org.jetbrains.plugins.ruby.testing.sm.runner.SMTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class FileUrlLocationTest extends RailsFixtureTestCase {
  protected String getRailsRootPath() {
    return myFixture.getTempDirPath() + "/fileUrl";
  }

  public void testSpecNavigation() throws Throwable {
    myFixture.configureByFiles("fileUrl/my_example_spec.rb");

    final String path = myFixture.getFile().getVirtualFile().getPath();
    doTest(17, "describe", path, 4);
    doTest(189, "it \"should fail\"", path, 16);
    doTest(261, "it \"should pass\" do", path, 22);
  }

  private void doTest(final int expectedOffset, final String expectedStartsWith,
                      final String filePath, final int lineNum) {
    final SMTestProxy testProxy =
        new SMTestProxy("myTest", false, "file://" + filePath + ":" + lineNum);

    final Location location = testProxy.getLocation(getProject());
    assertNotNull(location);
    assertNotNull(location.getPsiElement());

    //System.out.println(location.getPsiElement().getText());
    //System.out.println(location.getPsiElement().getTextOffset());
    assertEquals(expectedOffset, location.getPsiElement().getTextOffset());
    assertTrue(location.getPsiElement().getText().startsWith(expectedStartsWith));
  }
}
