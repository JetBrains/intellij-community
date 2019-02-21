// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.events;

import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils;

public class ArtifactDownloadScanningTest extends MavenBuildToolLogTestUtils {

  public void testSuccesfulParsing() {
    testCase("[INFO] --------------------------------[ jar ]---------------------------------",
             "Downloading from central: https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.pom",
             "Downloaded from central: https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.pom (42 kB at 100 kB/s)",
             "Downloading from central: https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.jar",
             "Downloaded from central: https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.jar (42 kB at 100 kB/s)")
      .withParsers(new ArtifactDownloadScanning())
      .expectSucceed("Download https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.pom")
      .expectSucceed("Download https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.jar")
      .check();
  }

  public void testFailedDownloading() {
    testCase("[INFO] --------------------------------[ jar ]---------------------------------\n" +
             "Downloading from central: https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.pom\n" +
             "[WARNING] The POM for some.maven:artifact:jar:1.2 is missing, no dependency information available\n" +
             "Downloading from central: https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.jar\n" +
             "[INFO] ------------------------------------------------------------------------\n" +
             "[INFO] BUILD FAILURE\n" +
             "[INFO] ------------------------------------------------------------------------\n" +
             "[INFO] Total time:  1.308 s\n" +
             "[INFO] Finished at: 2019-02-11T13:08:47+03:00\n" +
             "[INFO] ------------------------------------------------------------------------\n" +
             "[ERROR] Failed to execute goal on project m1-pom: Could not resolve dependencies for project org.jb:m1-pom:jar:1: Could not find artifact some.maven:artifact:jar:1.2 in central (https://repo.maven.apache.org/maven2) -> [Help 1]\n" +
             "[ERROR] ")
      .withParsers(new ArtifactDownloadScanning())
      .expect("Download https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.pom", StartEventMatcher::new)
      .expect("Download https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.jar", StartEventMatcher::new)
      .expect("Download https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.pom", FinishFailedEventMatcher::new)
      .expect("Download https://repo.maven.apache.org/maven2/some/maven/artifact/1.2/artifact-1.2.jar", FinishFailedEventMatcher::new)
      .check();
  }

}