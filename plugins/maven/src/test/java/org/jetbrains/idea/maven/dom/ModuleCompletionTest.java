package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

public class ModuleCompletionTest extends MavenImportingTestCase {
  protected CodeInsightTestFixture myCodeInsightFixture;

  @Override
  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder().getFixture();

    myCodeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture);
    myCodeInsightFixture.setUp();

    myTempDirFixture = myCodeInsightFixture.getTempDirFixture();
  }

  @Override
  protected void tearDownFixtures() throws Exception {
    myCodeInsightFixture.tearDown();
  }

  public void testCompleteFromAllAvailableModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    VirtualFile module2Pom = createModulePom("m2",
                                             "<groupId>test</groupId>" +
                                             "<artifactId>m2</artifactId>" +
                                             "<version>1</version>" +
                                             "<packaging>pom</packaging>" +

                                             "<modules>" +
                                             "  <module>m3</module>" +
                                             "</modules>");

    createModulePom("m2/m3",
                    "<groupId>test</groupId>" +
                    "<artifactId>m3</artifactId>" +
                    "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2", "m3");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertComplectionVariants(myProjectPom, "m1", "m2", "m2/m3");

    updateModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>pom</packaging>" +

                    "<modules>" +
                    "  <module>m3</module>" +
                    "  <module><caret></module>" +
                    "</modules>");

    assertComplectionVariants(module2Pom, "..", "../m1", "m3");
  }

  public void testDoesNotCompeteIfThereIsNoModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertComplectionVariants(myProjectPom);
  }

  public void testIncludesAllThePomsAvailable() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");
    importProject();

    createModulePom("subDir1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("subDir1/subDir2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module><caret></module>" +
                     "</modules>");

    assertComplectionVariants(myProjectPom, "subDir1", "subDir1/subDir2");
  }
  
  private void assertComplectionVariants(VirtualFile f, String... expected) throws IOException {
    myCodeInsightFixture.configureFromExistingVirtualFile(f);
    LookupElement[] variants = myCodeInsightFixture.completeBasic();

    List<String> actual = new ArrayList<String>();
    for (LookupElement each : variants) {
      actual.add(each.getLookupString());
    }

    assertUnorderedElementsAreEqual(actual, expected);
  }
}
