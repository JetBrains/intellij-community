package com.intellij.execution.testframework.sm;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.testFramework.LightProjectDescriptor;

/**
 * @author Roman Chernyatchik
 */
public class FileUrlLocationTest extends SMLightFixtureTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourDescriptor;
  }

  public void testSpecNavigation() throws Throwable {
    createAndAddFile("my_example_spec.rb",
                     "\n" +
                     "require \"spec\"\n" +
                     "\n" +
                     "describe \"Blabla\" do\n" +
                     "\n" +
                     "  # Called before each example.\n" +
                     "  before(:each) do\n" +
                     "    # Do nothing\n" +
                     "  end\n" +
                     "\n" +
                     "  # Called after each example.\n" +
                     "  after(:each) do\n" +
                     "    # Do nothing\n" +
                     "  end\n" +
                     "\n" +
                     "  it \"should fail\" do\n" +
                     "\n" +
                     "    #should pass\n" +
                     "    true.should == false\n" +
                     "  end\n" +
                     "\n" +
                     "  it \"should pass\" do\n" +
                     "\n" +
                     "    #should pass\n" +
                     "    true.should == true\n" +
                     "  end\n" +
                     "end");

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
