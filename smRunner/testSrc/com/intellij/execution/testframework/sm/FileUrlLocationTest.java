package com.intellij.execution.testframework.sm;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.plugins.ruby.rails.RailsFixtureTestCase;

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
    doTest(189, "it", path, 16);
    doTest(261, "it", path, 22);
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
    final String element = location.getPsiElement().getText();
    assertTrue(element, element.startsWith(expectedStartsWith));
  }
}
