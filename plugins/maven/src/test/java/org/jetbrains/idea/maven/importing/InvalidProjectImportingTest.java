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
package org.jetbrains.idea.maven.importing;

import com.intellij.idea.Bombed;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class InvalidProjectImportingTest extends MavenImportingTestCase {
  public void testUnknownProblem() {
    importProject("");
    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'pom.xml' has syntax errors");
  }

  public void testUnknownProblemWithEmptyFile() {
    createProjectPom("");
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        myProjectPom.setBinaryContent(new byte[0]);
      }
    }.execute().throwException();

    importProject();
    
    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'pom.xml' has syntax errors");
  }

  public void testUndefinedPropertyInHeader() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>${undefined}</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'artifactId' with value '${undefined}' does not match a valid id pattern.");
  }

  public void testUnresolvedParent() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>parent</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>");

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Parent 'test:parent:1' not found");
  }

  public void testUnresolvedParentForInvalidProject() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>parent</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>" +

                  // not of the 'pom' type
                  "<modules>" +
                  "  <module>foo</module>" +
                  "</modules>");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root,
                   "Parent 'test:parent:1' not found",
                   "Packaging 'jar' is invalid. Aggregator projects require 'pom' as packaging.",
                   "Module 'foo' not found");
  }

  public void testMissingModules() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>foo</module>" +
                  "</modules>");

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Module 'foo' not found");
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testInvalidProjectModel() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>jar</packaging>" + // invalid packaging

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1</version>");
    importProject();
    assertModules("project", "foo");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Packaging 'jar' is invalid. Aggregator projects require 'pom' as packaging.");
  }

  public void testInvalidModuleModel() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1"); //  invalid tag

    importProject();
    assertModules("project", "foo");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);

    assertProblems(getModules(root).get(0), "'pom.xml' has syntax errors");
  }

  public void testSeveratInvalidModulesAndWithSameName() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>foo</module>" +
                     "  <module>bar1</module>" +
                     "  <module>bar2</module>" +
                     "  <module>bar3</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1"); //  invalid tag

    createModulePom("bar1", "<groupId>test</groupId>" +
                            "<artifactId>bar</artifactId>" +
                            "<version>1"); //  invalid tag

    createModulePom("bar2", "<groupId>test</groupId>" +
                            "<artifactId>bar</artifactId>" +
                            "<version>1"); //  invalid tag

    createModulePom("bar3", "<groupId>org.test</groupId>" +
                            "<artifactId>bar</artifactId>" +
                            "<version>1"); //  invalid tag

    importProject();
    assertModules("project", "foo", "bar (1)", "bar (2)", "bar (3) (org.test)");
  }

  public void testInvalidProjectWithModules() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1" + // invalid tag

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1</version>");

    importProject();

    assertModules("project", "foo");
  }

  public void testNonPOMProjectWithModules() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1</version>");

    importProject();

    assertModules("project", "foo");
  }

  public void testInvalidRepositoryLayout() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<distributionManagement>" +
                  "  <repository>" +
                  "    <id>test</id>" +
                  "    <url>http://www.google.com</url>" +
                  "    <layout>nothing</layout>" + // invalid layout
                  "  </repository>" +
                  "</distributionManagement>");

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Cannot find layout implementation corresponding to: 'nothing' for remote repository with id: 'test'.");
  }

  public void testDoNotFailIfRepositoryHasEmptyLayout() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<repositories>" +
                  " <repository>" +
                  "   <id>foo1</id>" +
                  "   <url>bar1</url>" +
                  "   <layout/>" +
                  " </repository>" +
                  "</repositories>" +
                  "<pluginRepositories>" +
                  " <pluginRepository>" +
                  "   <id>foo2</id>" +
                  "   <url>bar2</url>" +
                  "   <layout/>" +
                  " </pluginRepository>" +
                  "</pluginRepositories>");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
  }

  public void testDoNotFailIfDistributionRepositoryHasEmptyValues() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<distributionManagement>" +
                  "  <repository>" +
                  "   <id/>" +
                  "   <url/>" +
                  "   <layout/>" +
                  "  </repository>" +
                  "</distributionManagement>");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testUnresolvedDependencies() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>xxx</groupId>" +
                          "    <artifactId>xxx</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>yyy</groupId>" +
                          "    <artifactId>yyy</artifactId>" +
                          "    <version>2</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>zzz</groupId>" +
                          "    <artifactId>zzz</artifactId>" +
                          "    <version>3</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();

    MavenProject root = getRootProjects().get(0);

    assertProblems(root);
    assertProblems(getModules(root).get(0),
                   "Unresolved dependency: 'xxx:xxx:jar:1'",
                   "Unresolved dependency: 'yyy:yyy:jar:2'");
    assertProblems(getModules(root).get(1),
                   "Unresolved dependency: 'zzz:zzz:jar:3'");
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testUnresolvedPomTypeDependency() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>yyy</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <type>pom</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject();

    assertModuleLibDeps("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Unresolved dependency: 'xxx:yyy:pom:4.0'");
  }

  public void testDoesNotReportInterModuleDependenciesAsUnresolved() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
    assertProblems(getModules(root).get(0));
    assertProblems(getModules(root).get(1));
  }

  public void testCircularDependencies() {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module>m3</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m3</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m1</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
    assertProblems(getModules(root).get(0));
    assertProblems(getModules(root).get(1));
    assertProblems(getModules(root).get(2));
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testUnresolvedExtensionsAfterImport() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <extensions>" +
                  "   <extension>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Unresolved build extension: 'xxx:yyy:1'");
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testUnresolvedExtensionsAfterResolve() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <extensions>" +
                  "   <extension>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    resolveDependenciesAndImport();
    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Unresolved build extension: 'xxx:yyy:1'");
  }

  public void testDoesNotReportExtensionsThatWereNotTriedToBeResolved() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  // for some reasons this plugins is not rtied to be resolved by embedder.
                  // we shouldn't report it as unresolved.
                  "<build>" +
                  "  <extensions>" +
                  "   <extension>" +
                  "      <groupId>org.apache.maven.wagon</groupId>" +
                  "      <artifactId>wagon-ssh-external</artifactId>" +
                  "      <version>1.0-alpha-6</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    assertProblems(getRootProjects().get(0));

    resolveDependenciesAndImport();
    assertProblems(getRootProjects().get(0));
  }

  public void testDoesNotReportExtensionsThatDoNotHaveJarFiles() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  // for some reasons this plugins is not rtied to be resolved by embedder.
                  // we shouldn't report it as unresolved.
                  "<build>" +
                  "  <extensions>" +
                  "   <extension>" +
                  "      <groupId>org.apache.maven.wagon</groupId>" +
                  "      <artifactId>wagon</artifactId>" +
                  "      <version>1.0-alpha-6</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    assertProblems(getRootProjects().get(0));

    resolveDependenciesAndImport();
    assertProblems(getRootProjects().get(0));
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testUnresolvedBuildExtensionsInModules() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    " <extensions>" +
                    "   <extension>" +
                    "     <groupId>xxx</groupId>" +
                    "     <artifactId>xxx</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "  </extensions>" +
                    "</build>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    " <extensions>" +
                    "   <extension>" +
                    "     <groupId>yyy</groupId>" +
                    "     <artifactId>yyy</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "   <extension>" +
                    "     <groupId>zzz</groupId>" +
                    "     <artifactId>zzz</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "  </extensions>" +
                    "</build>");

    importProject();

    MavenProject root = getRootProjects().get(0);

    assertProblems(root);
    assertProblems(getModules(root).get(0),
                   "Unresolved build extension: 'xxx:xxx:1'");
    assertProblems(getModules(root).get(1),
                   "Unresolved build extension: 'yyy:yyy:1'",
                   "Unresolved build extension: 'zzz:zzz:1'");
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testUnresolvedPlugins() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Unresolved plugin: 'xxx:yyy:1'");
  }

  public void testDoNotReportResolvedPlugins() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins");

    setRepositoryPath(helper.getTestDataPath("plugins"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>org.apache.maven.plugins</groupId>" +
                  "     <artifactId>maven-compiler-plugin</artifactId>" +
                  "     <version>2.0.2</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertProblems(getRootProjects().get(0));
  }

  @Bombed(user = "Vladislav.Soroka", year=2020, month = Calendar.APRIL, day = 1, description = "temporary disabled")
  public void testUnresolvedPluginsAsExtensions() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "     <extensions>true</extensions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Unresolved plugin: 'xxx:yyy:1'");
  }

  public void testInvalidSettingsXml() throws Exception {
    updateSettingsXml("<localRepo<<");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'settings.xml' has syntax errors");
  }

  public void testInvalidProfilesXml() {
    createProfilesXml("<prof<<");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'profiles.xml' has syntax errors");
  }

  private static void assertProblems(MavenProject project, String... expectedProblems) {
    List<String> actualProblems = new ArrayList<>();
    for (MavenProjectProblem each : project.getProblems()) {
      actualProblems.add(each.getDescription());
    }
    assertOrderedElementsAreEqual(actualProblems, expectedProblems);
  }

  private List<MavenProject> getRootProjects() {
    return myProjectsTree.getRootProjects();
  }

  private List<MavenProject> getModules(MavenProject p) {
    return myProjectsTree.getModules(p);
  }
}
