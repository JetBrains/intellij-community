/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.MavenImportingTestCase;

public class MavenProjectsManagerSettingsXmlTest extends MavenImportingTestCase {
  public void testUpdatingProjectsOnSettingsXmlCreationAndDeletion() throws Exception {
    deleteSettingsXml();
    initProjectsManager(true);
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    importProject();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles());

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "  </profile>" +
                      "</profiles>");
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles(), "one");

    deleteSettingsXml();
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles());
  }
}