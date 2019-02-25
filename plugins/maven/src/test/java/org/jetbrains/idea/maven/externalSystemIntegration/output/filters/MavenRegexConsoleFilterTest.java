// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.filters;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenTestCase;

import java.io.IOException;

public class MavenRegexConsoleFilterTest extends MavenTestCase {

  public void testKotlinOutputParse() throws IOException {
    MavenRegexConsoleFilter filter = MavenRegexConsoleFilter.kotlinFilter(myProject);
    VirtualFile file = createProjectSubFile("src/main/kotlin/MyKtl.kt", "");
    String line =
      "[ERROR] " + file.getCanonicalPath() + ": (3, 16) Data class primary constructor must have only property (val / var) parameters\n";
    doTest(line, filter, file, 3, 16);
  }

  public void testJavaOutputParse() throws IOException {
    MavenRegexConsoleFilter filter = MavenRegexConsoleFilter.javaFilter(myProject);
    VirtualFile file = createProjectSubFile("src/main/java/a/b/c/MyClass.java", "");
    String line =
      "[ERROR] " + file.getCanonicalPath() + ":[13,2] cannot find symbol";
    doTest(line, filter, file, 13, 2);
  }


  private void doTest(String line, MavenRegexConsoleFilter filter, VirtualFile expectedFile, int expectedLine, int expectedColumn) {

    //LocalFileSystem.getInstance().
    Filter.Result result = filter.applyFilter(line, line.length());
    assertNotNull(result);
    HyperlinkInfo info = result.getFirstHyperlinkInfo();
    assertNotNull(info);
    assertTrue(info instanceof OpenFileHyperlinkInfo);
    assertEquals(expectedFile,
                 ((OpenFileHyperlinkInfo)info).getVirtualFile());
    assertEquals(expectedLine - 1, ((OpenFileHyperlinkInfo)info).getDescriptor().getLine());
    assertEquals(expectedColumn - 1, ((OpenFileHyperlinkInfo)info).getDescriptor().getColumn());
  }
}