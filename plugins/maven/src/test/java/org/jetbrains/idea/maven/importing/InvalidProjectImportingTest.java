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

import com.intellij.openapi.application.WriteAction;
import org.intellij.lang.annotations.Language;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InvalidProjectImportingTest extends MavenMultiVersionImportingTestCase {

  @Test
  public void testResetDependenciesWhenProjectContainsErrors() {
    //Registry.get("maven.server.debug").setValue(true);
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>jar</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>somegroup</groupId>" +
                          "    <artifactId>artifact</artifactId>" +
                          "    <version>1.0</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProjectWithErrors();
    assertModules("project", "m1");
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0");


    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>jar</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>somegroup</groupId>" +
                          "    <artifactId>artifact</artifactId>" +
                          "    <version>2.0</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProjectWithErrors();
    assertModules("project", "m1");
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:2.0");
  }

  @Test
  public void testShouldNotResetDependenciesWhenProjectContainsUnrecoverableErrors() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>jar</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>somegroup</groupId>" +
                          "    <artifactId>artifact</artifactId>" +
                          "    <version>1.0</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProjectWithErrors();
    assertModules("project", "m1");
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0");


    createProjectPom("<groupId>test</groupId>" +
                     "<packaging>jar</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "" +
                          "  <dependency>" +
                          "    <groupId>somegroup</groupId>" +
                          "    <artifactId>artifact</artifactId>" +
                          "    <version>2.0" +
                          "  </dependency>" +
                          "</dependencies>");

    importProjectWithErrors();
    assertModules("project", "m1");
    assertModuleLibDeps("m1", "Maven: somegroup:artifact:1.0");
  }

  @Test
  public void testUnknownProblemWithEmptyFile() throws IOException {
    createProjectPom("");
    WriteAction.runAndWait(() -> myProjectPom.setBinaryContent(new byte[0]));

    importProjectWithErrors();

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'pom.xml' has syntax errors");
  }

  @Test
  public void testUndefinedPropertyInHeader() {
    importProjectWithErrors("<groupId>test</groupId>" +
                            "<artifactId>${undefined}</artifactId>" +
                            "<version>1</version>");

    assertModules("project");
    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'artifactId' with value '${undefined}' does not match a valid id pattern.");
  }

  @Test
  public void testRecursiveInterpolation() {
    importProjectWithErrors("<groupId>test</groupId>" +
                            "<artifactId>project</artifactId>" +
                            "<version>${version}</version>" +

                            "<dependencies>" +
                            "  <dependency>" +
                            "    <groupId>group</groupId>" +
                            "    <artifactId>artifact</artifactId>" +
                            "    <version>1</version>" +
                            "   </dependency>" +
                            "</dependencies>");

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    List<MavenProjectProblem> problems = root.getProblems();
    assertFalse(problems.isEmpty());
    assertModuleLibDeps("project", "Maven: group:artifact:1");
  }

  @Test
  public void testUnresolvedParent() {
    importProjectWithErrors("<groupId>test</groupId>" +
                            "<artifactId>project</artifactId>" +
                            "<version>1</version>" +

                            "<parent>" +
                            "  <groupId>test</groupId>" +
                            "  <artifactId>parent</artifactId>" +
                            "  <version>1</version>" +
                            "</parent>");

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    List<MavenProjectProblem> problems = root.getProblems();
    assertSize(1, problems);
    assertTrue(problems.get(0).getDescription().contains("Could not find artifact test:parent:pom:1"));
  }

  @Test
  public void testUnresolvedParentForInvalidProject() {
    importProjectWithErrors("<groupId>test</groupId>" +
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
    List<MavenProjectProblem> problems = root.getProblems();
    assertSize(2, problems);
    assertTrue(problems.get(0).getDescription(), problems.get(0).getDescription().contains("Could not find artifact test:parent:pom:1"));
    assertTrue(problems.get(1).getDescription(), problems.get(1).getDescription().equals("Module 'foo' not found"));
  }

  @Test
  public void testMissingModules() throws IOException {
    importProjectWithErrors("<groupId>test</groupId>" +
                            "<artifactId>project</artifactId>" +
                            "<version>1</version>" +
                            "<packaging>pom</packaging>" +

                            "<modules>" +
                            "  <module>foo</module>" +
                            "</modules>");
    resolvePlugins();

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Module 'foo' not found");
  }

  private static String toString(MavenGeneralSettings settings) {
    return "MavenGeneralSettings{" +
           "workOffline=" + settings.isWorkOffline() +
           ", mavenHome='" + settings.getMavenHome() + '\'' +
           ", mavenSettingsFile='" + settings.getUserSettingsFile() + '\'' +
           ", overriddenLocalRepository='" + settings.getLocalRepository() + '\'' +
           ", printErrorStackTraces=" + settings.isPrintErrorStackTraces() +
           ", usePluginRegistry=" + settings.isUsePluginRegistry() +
           ", nonRecursive=" + settings.isNonRecursive() +
           ", alwaysUpdateSnapshots=" + settings.isAlwaysUpdateSnapshots() +
           ", threads='" + settings.getThreads() + '\'' +
           ", outputLevel=" + settings.getOutputLevel() +
           ", checksumPolicy=" + settings.getChecksumPolicy() +
           ", failureBehavior=" + settings.getFailureBehavior() +
           ", pluginUpdatePolicy=" + settings.getPluginUpdatePolicy() +
           ", myEffectiveLocalRepositoryCache=" + settings.getEffectiveLocalRepository() +
           //", myDefaultPluginsCache=" + settings.myDefaultPluginsCache +
           //", myBulkUpdateLevel=" + settings.myBulkUpdateLevel +
           //", myListeners=" + settings.myListeners +
           '}';
  }

  @Test
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
    importProjectWithErrors();
    assertModules("project", "foo");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'packaging' with value 'jar' is invalid. Aggregator projects require 'pom' as packaging.");
  }

  @Test
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

    importProjectWithErrors();
    //resolvePlugins();
    assertModules("project", "foo");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);

    assertProblems(getModules(root).get(0), "'pom.xml' has syntax errors");
  }

  @Test
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

    importProjectWithErrors();
    assertModules("project", "foo", "bar (1)", "bar (2)", "bar (3) (org.test)");
  }

  @Test
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

    importProjectWithErrors();

    assertModules("project", "foo");
  }

  @Test
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

    importProjectWithErrors();

    assertModules("project", "foo");
  }

  @Test
  public void testDoNotFailIfRepositoryHasEmptyLayout() {
    importProjectWithErrors("<groupId>test</groupId>" +
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
    resolvePlugins();

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
  }

  @Test
  public void testDoNotFailIfDistributionRepositoryHasEmptyValues() {
    importProjectWithErrors("<groupId>test</groupId>" +
                            "<artifactId>project</artifactId>" +
                            "<version>1</version>" +

                            "<distributionManagement>" +
                            "  <repository>" +
                            "   <id/>" +
                            "   <url/>" +
                            "   <layout/>" +
                            "  </repository>" +
                            "</distributionManagement>");
    resolvePlugins();

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
  }

  @Test
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

    importProjectWithErrors();
    resolvePlugins();

    MavenProject root = getRootProjects().get(0);

    assertProblems(root);
    assertProblems(getModules(root).get(0),
                   "Unresolved dependency: 'xxx:xxx:jar:1'",
                   "Unresolved dependency: 'yyy:yyy:jar:2'");
    assertProblems(getModules(root).get(1),
                   "Unresolved dependency: 'zzz:zzz:jar:3'");
  }

  @Test
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

    importProjectWithErrors();
    resolvePlugins();

    assertModuleLibDeps("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Unresolved dependency: 'xxx:yyy:pom:4.0'");
  }

  @Test
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

    importProjectWithErrors();
    resolvePlugins();

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
    assertProblems(getModules(root).get(0));
    assertProblems(getModules(root).get(1));
  }

  @Test
  public void testCircularDependencies() {

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

    importProjectWithErrors();

    MavenProject root = getRootProjects().get(0);
    assertProblems(root);
    assertProblems(getModules(root).get(0));
    assertProblems(getModules(root).get(1));
    assertProblems(getModules(root).get(2));
  }

  @Test
  public void testUnresolvedExtensionsAfterResolve() {
    importProjectWithErrors("<groupId>test</groupId>" +
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
    List<MavenProjectProblem> problems = root.getProblems();
    assertSize(1, problems);
    assertTrue(problems.get(0).getDescription().contains("Could not find artifact xxx:yyy:jar:1"));
  }

  @Test
  public void testDoesNotReportExtensionsThatWereNotTriedToBeResolved() {
    importProjectWithErrors("<groupId>test</groupId>" +
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
    resolvePlugins();

    assertProblems(getRootProjects().get(0));

    resolveDependenciesAndImport();
    assertProblems(getRootProjects().get(0));
  }

  @Test
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

    importProjectWithErrors();

    MavenProject root = getRootProjects().get(0);

    assertProblems(root);

    List<MavenProjectProblem> problems = getModules(root).get(0).getProblems();
    assertSize(1, problems);
    assertTrue(problems.get(0).getDescription(), problems.get(0).getDescription().contains("Could not find artifact xxx:xxx:jar:1"));


    problems = getModules(root).get(1).getProblems();
    assertSize(1, problems);
    assertTrue(problems.get(0).getDescription(), problems.get(0).getDescription().contains("Could not find artifact yyy:yyy:jar:1"));
  }

  @Test
  public void testUnresolvedPlugins() {
    importProjectWithErrors("<groupId>test</groupId>" +
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
    resolvePlugins();

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "Unresolved plugin: 'xxx:yyy:1'");
  }

  @Test
  public void testDoNotReportResolvedPlugins() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins");

    setRepositoryPath(helper.getTestDataPath("plugins"));

    importProjectWithErrors("<groupId>test</groupId>" +
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

  @Test
  public void testUnresolvedPluginsAsExtensions() {
    importProjectWithErrors("<groupId>test</groupId>" +
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
    resolvePlugins();

    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    List<MavenProjectProblem> problems = root.getProblems();
    assertSize(2, problems);
    assertTrue(problems.get(0).getDescription(), problems.get(0).getDescription().contains("Could not find artifact xxx:yyy:jar:1"));
    assertTrue(problems.get(1).getDescription(), problems.get(1).getDescription().contains("Unresolved plugin: 'xxx:yyy:1'"));
  }

  @Test
  public void testInvalidSettingsXml() throws Exception {
    updateSettingsXml("<localRepo<<");

    importProjectWithErrors("<groupId>test</groupId>" +
                            "<artifactId>project</artifactId>" +
                            "<version>1</version>");
    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'settings.xml' has syntax errors");
  }

  @Test
  public void testInvalidProfilesXml() {
    createProfilesXml("<prof<<");

    importProjectWithErrors("<groupId>test</groupId>" +
                            "<artifactId>project</artifactId>" +
                            "<version>1</version>");
    assertModules("project");

    MavenProject root = getRootProjects().get(0);
    assertProblems(root, "'profiles.xml' has syntax errors");
  }

  private void importProjectWithErrors(@Language(value = "XML", prefix = "<project>", suffix = "</project>") String s) {
    createProjectPom(s);
    importProjectWithErrors();
  }

  private static void assertProblems(MavenProject project, String... expectedProblems) {
    List<String> actualProblems = new ArrayList<>();
    for (MavenProjectProblem each : project.getProblems()) {
      actualProblems.add(each.getDescription());
    }
    assertOrderedElementsAreEqual(actualProblems, expectedProblems);
  }

  private static void assertContainsProblems(MavenProject project, String... expectedProblems) {
    List<String> actualProblems = new ArrayList<>();
    for (MavenProjectProblem each : project.getProblems()) {
      actualProblems.add(each.getDescription());
    }
    assertContainsElements(actualProblems, expectedProblems);
  }

  private List<MavenProject> getRootProjects() {
    return myProjectsTree.getRootProjects();
  }

  private List<MavenProject> getModules(MavenProject p) {
    return myProjectsTree.getModules(p);
  }
}
