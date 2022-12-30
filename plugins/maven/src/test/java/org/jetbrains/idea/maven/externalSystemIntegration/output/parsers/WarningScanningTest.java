// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils;

public class WarningScanningTest extends MavenBuildToolLogTestUtils {


  public void testWarningNotify() {
    testCase("""
               [INFO] --------------------------------[ jar ]---------------------------------
               [WARNING] The POM for some.maven:artifact:jar:1.2 is missing, no dependency information available
               """)
      .withParsers(new WarningNotifier())
      .expect("The POM for some.maven:artifact:jar:1.2 is missing, no dependency information available", WarningEventMatcher::new)
      .withSkippedOutput()
      .check();
  }


  public void testWarningConcatenate() {
    testCase("""
               [INFO] --------------------------------[ jar ]---------------------------------
               [WARNING]\s
               [WARNING] Some problems were encountered while building the effective model for org.jb:m1-pom:jar:1
               [WARNING] 'build.plugins.plugin.version' for org.apache.maven.plugins:maven-compiler-plugin is missing. @ line 30, column 21
               [WARNING] 'build.plugins.plugin.version' for org.apache.maven.plugins:maven-surefire-plugin is missing. @ line 23, column 21
               [WARNING]\s
               [WARNING] It is highly recommended to fix these problems because they threaten the stability of your build.
               [WARNING]\s
               [WARNING] For this reason, future Maven versions might no longer support building such malformed projects""")
      .withParsers(new WarningNotifier())
      .expect("""
                Some problems were encountered while building the effective model for org.jb:m1-pom:jar:1
                'build.plugins.plugin.version' for org.apache.maven.plugins:maven-compiler-plugin is missing. @ line 30, column 21
                'build.plugins.plugin.version' for org.apache.maven.plugins:maven-surefire-plugin is missing. @ line 23, column 21
                It is highly recommended to fix these problems because they threaten the stability of your build.
                For this reason, future Maven versions might no longer support building such malformed projects""",
              WarningEventMatcher::new)
      .withSkippedOutput()
      .check();
  }
}