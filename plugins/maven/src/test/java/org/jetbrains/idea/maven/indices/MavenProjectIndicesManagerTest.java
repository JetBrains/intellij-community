/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.indices;

import java.util.List;

public class MavenProjectIndicesManagerTest extends MavenIndicesTestCase {
  private MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject);
    myIndicesFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIndicesFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testAutomaticallyAddAndUpdateLocalRepository() {
    List<MavenIndex> indices = myIndicesFixture.getProjectIndicesManager().getIndices();

    assertEquals(1, indices.size());

    assertEquals(MavenIndex.Kind.LOCAL, indices.get(0).getKind());
    assertTrue(indices.get(0).getRepositoryPathOrUrl().endsWith("local1"));
    assertTrue(myIndicesFixture.getProjectIndicesManager().hasVersion("junit", "junit", "4.0"));
  }

  public void testAutomaticallyAddRemoteRepositoriesOnProjectUpdate() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    List<MavenIndex> indices = myIndicesFixture.getProjectIndicesManager().getIndices();
    assertEquals(2, indices.size());

    assertTrue(indices.get(0).getRepositoryPathOrUrl().endsWith("local1"));
    assertEquals("http://repo1.maven.org/maven2", indices.get(1).getRepositoryPathOrUrl());
  }

  public void testUpdatingIndicesOnResolution() throws Exception {
    removeFromLocalRepository("junit/junit/4.0");
    myIndicesFixture.getProjectIndicesManager().scheduleUpdate(myIndicesFixture.getProjectIndicesManager().getIndices());

    assertUnorderedElementsAreEqual(myIndicesFixture.getProjectIndicesManager().getVersions("junit", "junit"), "3.8.1", "3.8.2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertUnorderedElementsAreEqual(myIndicesFixture.getProjectIndicesManager().getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0");
  }

  public void testUpdatingIndexUsingMirrors() throws Exception {
    myIndicesFixture.tearDown();
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject, "local2", "remote_mirror");
    myIndicesFixture.setUp();

    updateSettingsXmlFully("<settings>" +
                           "  <mirrors>" +
                           "  </mirrors>" +
                           "</settings>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<repositories>" +
                  "  <repository>" +
                  "    <id>central</id>" +
                  "    <url>xxx://does.not.matter</url>" +
                  "  </repository>" +
                  "</repositories>");

    myIndicesFixture.getProjectIndicesManager().scheduleUpdateAll();
    assertUnorderedElementsAreEqual(myIndicesFixture.getProjectIndicesManager().getGroupIds(), "test", "jmock");

    updateSettingsXmlFully("<settings>" +
                           "  <mirrors>" +
                           "    <mirror>" +
                           "      <id>nexus</id>" +
                           "      <mirrorOf>anotherRepoId</mirrorOf>" +
                           "      <url>file:///" + myIndicesFixture.getRepositoryHelper().getTestDataPath("remote_mirror") + "</url>" +
                           "    </mirror>" +
                           "  </mirrors>" +
                           "</settings>");

    myIndicesFixture.getProjectIndicesManager().scheduleUpdateAll();
    assertUnorderedElementsAreEqual(myIndicesFixture.getProjectIndicesManager().getGroupIds(), "test", "jmock");

    updateSettingsXmlFully("<settings>" +
                           "  <mirrors>" +
                           "    <mirror>" +
                           "      <id>nexus</id>" +
                           "      <mirrorOf>central</mirrorOf>" +
                           "      <url>file:///" + myIndicesFixture.getRepositoryHelper().getTestDataPath("remote_mirror") + "</url>" +
                           "    </mirror>" +
                           "  </mirrors>" +
                           "</settings>");

    myIndicesFixture.getProjectIndicesManager().scheduleUpdateAll();
    assertUnorderedElementsAreEqual(myIndicesFixture.getProjectIndicesManager().getGroupIds(), "test", "jmock", "junit");

    myIndicesFixture.tearDown();
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject, "local2", "remote_mirror");
    myIndicesFixture.setUp();

    updateSettingsXmlFully("<settings>" +
                           "  <mirrors>" +
                           "    <mirror>" +
                           "      <id>nexus</id>" +
                           "      <mirrorOf>*</mirrorOf>" +
                           "      <url>file:///" + myIndicesFixture.getRepositoryHelper().getTestDataPath("remote_mirror") + "</url>" +
                           "    </mirror>" +
                           "  </mirrors>" +
                           "</settings>");

    myIndicesFixture.getProjectIndicesManager().scheduleUpdateAll();
    assertUnorderedElementsAreEqual(myIndicesFixture.getProjectIndicesManager().getGroupIds(), "test", "jmock", "junit");

  }

  public void testCheckingLocalRepositoryForAbsentIndices() throws Exception {
    myIndicesFixture.tearDown();
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject, "local2");
    myIndicesFixture.setUp();

    myIndicesFixture.addToRepository("local1");

    assertUnorderedElementsAreEqual(
      myIndicesFixture.getProjectIndicesManager().getGroupIds(), "jmock");

    assertTrue(myIndicesFixture.getProjectIndicesManager().hasGroupId("junit"));
    assertFalse(myIndicesFixture.getProjectIndicesManager().hasGroupId("xxx"));

    assertTrue(myIndicesFixture.getProjectIndicesManager().hasArtifactId("junit", "junit"));
    assertFalse(myIndicesFixture.getProjectIndicesManager().hasArtifactId("junit", "xxx"));
    assertFalse(myIndicesFixture.getProjectIndicesManager().hasArtifactId("xxx", "junit"));

    assertTrue(myIndicesFixture.getProjectIndicesManager().hasVersion("junit", "junit", "4.0"));
    assertFalse(myIndicesFixture.getProjectIndicesManager().hasVersion("junit", "junit", "xxx"));
    assertFalse(myIndicesFixture.getProjectIndicesManager().hasVersion("junit", "xxx", "4.0"));
    assertFalse(myIndicesFixture.getProjectIndicesManager().hasVersion("xxx", "junit", "4.0"));
    //assertUnorderedElementsAreEqual(
    //    myIndicesFixture.getProjectIndicesManager().getGroupIds(), "junit", "jmock");
    //assertUnorderedElementsAreEqual(
    //    myIndicesFixture.getProjectIndicesManager().getArtifactIds("junit"), "junit");
    //assertUnorderedElementsAreEqual(
    //    myIndicesFixture.getProjectIndicesManager().getVersions("junit", "junit"), "4.0");
  }
}
