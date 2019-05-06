// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenSpyOutputParser;

import java.io.IOException;

public class MavenSpyOutputParserTest extends MavenBuildToolLogTestUtils {

  public void testSuccessfullBuildWithTwoSubmodules() throws IOException {
    assertSameLines("" +
    "Maven run\n" +
    " Project test:project:pom:1\n" +
    "  install\n" +
    " Project test:m1:jar:1\n" +
    "  Resolving Dependencies\n" +
    "  resources\n" +
    "  compile\n" +
    "  testResources\n" +
    "  testCompile\n" +
    "  test\n" +
    "  jar\n" +
    " Project test:m2:jar:1\n" +
    "  Resolving Dependencies\n",
                 testCase(fromFile("org/jetbrains/maven/buildlogs/Project2Modules.log"))
                   .withParsers(new MavenSpyOutputParser())
                                .runAndFormatToString());
  }
}
