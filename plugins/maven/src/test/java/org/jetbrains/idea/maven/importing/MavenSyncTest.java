// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import org.jetbrains.idea.maven.MavenImportingTestCase;

public class MavenSyncTest extends MavenImportingTestCase {
  public static final String PLUGINS_RESOLVE_PREFIX = "Downloading Maven plugins--";

  public void testSync() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>jar</packaging>" +
                  "<version>1</version>");

    assertNoErrorsInSync();
  }

  public void testUnresolvedDependency() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>jar</packaging>" +
                  "<version>1</version>" +
                  "<dependencies>" +
                  "<dependency>" +
                  "<groupId>unknown</groupId>" +
                  "<artifactId>unknown</artifactId>" +
                  "<version>123</version>" +
                  "</dependency>" +
                  "</dependencies>" +
                  "");

    assertEventRegistered("Resolving Maven dependencies");
    assertError("unknown:unknown:123 not resolved");

  }

  private void assertError(String message) {
    assertTrue("Should have error " + message, myProjectsManager.getSyncConsole().getErrors().stream().anyMatch(e -> message.equals(e.getMessage())));
  }

  private void assertEventRegistered(String step) {
    assertTrue("Should have step " + step, myProjectsManager.getSyncConsole().started(step));
  }

  private void assertNoErrorsInSync() {
    assertNullOrEmpty(myProjectsManager.getSyncConsole().getErrors());
  }
}

