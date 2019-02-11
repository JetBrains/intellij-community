// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.events;

import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils;

public class WarningScanningTest extends MavenBuildToolLogTestUtils {


  public void testWarningNotify() {
    testCase("[INFO] --------------------------------[ jar ]---------------------------------\n" +
             "[WARNING] The POM for some.maven:artifact:jar:1.2 is missing, no dependency information available\n")
      .withParsers(new WarningNotifier())
      .expect("The POM for some.maven:artifact:jar:1.2 is missing, no dependency information available", WarningEventMatcher::new)
      .check();
  }
}