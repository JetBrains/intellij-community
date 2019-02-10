// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.events;

import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils;

public class ArtifactDownloadScanningTest extends MavenBuildToolLogTestUtils {

  public void testSuccesfulParsing() {
    testCase("[INFO] --------------------------------[ jar ]---------------------------------",
             "Downloading from central: https://repo.maven.apache.org/maven2/some/maven/artifact-1.2.pom",
             "Downloaded from central: https://repo.maven.apache.org/maven2/some/maven/artifact-1.2.pom (42 kB at 100 kB/s)",
             "Downloading from central: https://repo.maven.apache.org/maven2/some/maven/artifact-1.2.jar",
             "Downloaded from central: https://repo.maven.apache.org/maven2/some/maven/artifact1.2.jar (42 kB at 100 kB/s)")
      .withParsers(new ArtifactDownloadScanning())
      .expectSucceed("Download https://repo.maven.apache.org/maven2/some/maven/artifact-1.2.pom")
      .expectSucceed("Download https://repo.maven.apache.org/maven2/some/maven/artifact-1.2.jar")
      .check();
  }

}