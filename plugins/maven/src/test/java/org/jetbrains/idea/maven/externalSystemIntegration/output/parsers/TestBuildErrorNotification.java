// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils;

public class TestBuildErrorNotification extends MavenBuildToolLogTestUtils {

  public void testParseJavaError() {
    String expectedFileName = FileUtil.toSystemDependentName("C:/path/to/MyFile.java");
    String expectedMessage = "';' expected";
    testCase("[INFO] -------------------------------------------------------------\n" +
             "[ERROR] /C:/path/to/MyFile.java:[13,21] ';' expected\n" +
             "[INFO] 1 error")
      .withParsers(new JavaBuildErrorNotification())
      .expect(expectedMessage, new FileEventMatcher(expectedMessage, expectedFileName, 12, 20))
      .withSkippedOutput()
      .check();
  }

  public void testParseKotlinError() {
    String expectedFileName = FileUtil.toSystemDependentName("C:\\path\\to\\MyFile.kt");
    String expectedMessage = "Data class primary constructor must have only property (val / var) parameters";
    testCase("[INFO] --- kotlin-maven-plugin:1.3.21:compile (compile) @ test-11 ---\n" +
             "[ERROR] C:\\path\\to\\MyFile.kt: (3, 16) Data class primary constructor must have only property (val / var) parameters\n" +
             "[INFO] ------------------------------------------------------------------------")
      .withParsers(new KotlinBuildErrorNotification())
      .expect(expectedMessage, new FileEventMatcher(expectedMessage, expectedFileName, 2, 15))
      .withSkippedOutput()
      .check();
  }

  public void testParseJavaCheckstyle() {
    String expectedFileName = FileUtil.toSystemDependentName("C:\\path\\to\\MyFile.java");
    String expectedMessage = "Line matches the illegal pattern 'System\\.(out|err).*?$'. [RegexpSinglelineJava]";
    testCase("[INFO] Starting audit...\n" +
             "[ERROR] C:\\path\\to\\MyFile.java:9: Line matches the illegal pattern 'System\\.(out|err).*?$'. [RegexpSinglelineJava]\n" +
             "Audit done.")
      .withParsers(new JavaBuildErrorNotification())
      .expect(expectedMessage, new FileEventMatcher(expectedMessage, expectedFileName, 8, 0))
      .withSkippedOutput()
      .check();
  }
}
