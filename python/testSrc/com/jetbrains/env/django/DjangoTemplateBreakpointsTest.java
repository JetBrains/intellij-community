package com.jetbrains.env.django;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.xdebugger.XDebuggerUtil;
import com.jetbrains.django.fixtures.DjangoTestCase;
import junit.framework.Assert;

/**
 * @author traff
 */
public class DjangoTemplateBreakpointsTest extends DjangoTestCase {
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/django/debug/";
  }

  public void testCanSetBreakpoints() {
    prepareProject("djangoDebug");
    inFile("djangoDebug/templates/test1.html")
      .notCan(0)
      .notCan(1)
      .notCan(2)
      .can(3)
      .notCan(4)
      .can(5)
      .can(6)
      .notCan(7)
      .can(8)
      .can(9)
      .notCan(10)
      .notCan(11)
      .notCan(12);
  }

  private OpenedTemplate inFile(String fileName) {
    return new OpenedTemplate(fileName, myFixture);
  }

  private static class OpenedTemplate {
    private String myFile;
    private CodeInsightTestFixture myFixture;

    private OpenedTemplate(String file, CodeInsightTestFixture fixture) {
      myFile = file;
      myFixture = fixture;
    }

    private OpenedTemplate can(int line) {
      Assert.assertTrue("at line " + line, XDebuggerUtil.getInstance().canPutBreakpointAt(myFixture.getProject(), findFile(), line));
      return this;
    }

    private VirtualFile findFile() {
      return myFixture.findFileInTempDir(myFile);
    }

    private OpenedTemplate notCan(int line) {
      Assert.assertFalse("at line " + line, XDebuggerUtil.getInstance().canPutBreakpointAt(myFixture.getProject(), findFile(), line));
      return this;
    }
  }
}
