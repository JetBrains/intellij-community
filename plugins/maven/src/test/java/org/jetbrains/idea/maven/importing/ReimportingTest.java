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

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleOrderEntryImpl;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.module.EffectiveLanguageLevelUtil.getEffectiveLanguageLevel;

public class ReimportingTest extends MavenImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
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
                          "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");
    importProject();
  }

  public void testKeepingModuleGroups() {
    final Module m = getModule("project");

    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() {
        ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
        model.setModuleGroupPath(m, new String[]{"group"});
        model.commit();
      }
    }.execute().throwException();


    importProject();
    assertModuleGroupPath("project", "group");
  }

  public void testAddingNewModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module>m3</module>" +
                     "</modules>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2", "m3");
  }

  public void testRemovingObsoleteModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    configConfirmationForYesAnswer();
    importProject();
    assertModules("project", "m1");
  }

  public void testDoesNotRemoveObsoleteModuleIfUserSaysNo() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    configConfirmationForNoAnswer();
    importProject();
    assertModules("project", "m1", "m2");
  }

  public void testDoesNotAskUserTwiceToRemoveTheSameModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");
    AtomicInteger counter = configConfirmationForNoAnswer();

    assertEquals(0, counter.get());

    importProject();
    assertEquals(1, counter.get());

    importProject();
    assertEquals(1, counter.get());
  }

  public void testDoesNotAskToRemoveManuallyAdderModules() {
    createModule("userModule");
    assertModules("project", "m1", "m2", "userModule");

    AtomicInteger counter = configConfirmationForNoAnswer();

    importProject();

    assertEquals(0, counter.get());
    assertModules("project", "m1", "m2", "userModule");
  }

  public void testRemovingAndCreatingModulesForAggregativeProjects() throws Exception {
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<packaging>pom</packaging>");
    importProject();

    assertModules("project", "m1", "m2");

    configConfirmationForYesAnswer();

    getMavenImporterSettings().setCreateModulesForAggregators(false);
    myProjectsManager.performScheduledImportInTests();
    assertModules("m2");

    getMavenImporterSettings().setCreateModulesForAggregators(true);
    myProjectsManager.performScheduledImportInTests();
    assertModules("project", "m1", "m2");
  }

  public void testDoNotCreateModulesForNewlyCreatedAggregativeProjectsIfNotNecessary() throws Exception {
    getMavenImporterSettings().setCreateModulesForAggregators(false);
    configConfirmationForYesAnswer();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module>m3</module>" +
                     "</modules>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>" +
                          "<packaging>pom</packaging>");
    importProject();
    assertModules("m1", "m2");
  }

  public void testReimportingWithProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>profile1</id>" +
                     "    <activation>" +
                     "      <activeByDefault>false</activeByDefault>" +
                     "    </activation>" +
                     "    <modules>" +
                     "      <module>m1</module>" +
                     "    </modules>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>profile2</id>" +
                     "    <activation>" +
                     "      <activeByDefault>false</activeByDefault>" +
                     "    </activation>" +
                     "    <modules>" +
                     "      <module>m2</module>" +
                     "    </modules>" +
                     "  </profile>" +
                     "</profiles>");

    configConfirmationForYesAnswer(); // will ask about absent modules

    importProjectWithProfiles("profile1");
    assertModules("project", "m1");

    importProjectWithProfiles("profile2");
    assertModules("project", "m2");
  }

  public void testChangingDependencyTypeToTestJar() throws Exception {
    configConfirmationForYesAnswer();
    VirtualFile m1 = createModulePom("m1", createPomXmlWithModuleDependency("jar"));

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>1</version>");

    importProjects(m1, m2);
    assertModules("m1", "m2");
    ModuleOrderEntry dep = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(getModule("m1")), getModule("m2"));
    assertNotNull(dep);
    assertFalse(((ModuleOrderEntryImpl)dep).isProductionOnTestDependency());

    createModulePom("m1", createPomXmlWithModuleDependency("test-jar"));
    importProjects(m1, m2);
    ModuleOrderEntry dep2 = OrderEntryUtil.findModuleOrderEntry(ModuleRootManager.getInstance(getModule("m1")), getModule("m2"));
    assertNotNull(dep2);
    assertTrue(((ModuleOrderEntryImpl)dep2).isProductionOnTestDependency());

  }

  public void testSettingTargetLevel() throws Exception {
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");
    importProject();
    assertEquals("1.5", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")));

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<build>" +
                          "  <plugins>" +
                          "    <plugin>" +
                          "      <artifactId>maven-compiler-plugin</artifactId>" +
                          "        <configuration>" +
                          "          <target>1.3</target>" +
                          "        </configuration>" +
                          "     </plugin>" +
                          "  </plugins>" +
                          "</build>");
    importProject();
    assertEquals("1.3", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")));

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<build>" +
                          "  <plugins>" +
                          "    <plugin>" +
                          "      <artifactId>maven-compiler-plugin</artifactId>" +
                          "        <configuration>" +
                          "          <target>1.6</target>" +
                          "        </configuration>" +
                          "     </plugin>" +
                          "  </plugins>" +
                          "</build>");

    importProject();
    assertEquals("1.6", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")));

    // after configuration/target element delete in maven-compiler-plugin CompilerConfiguration#getBytecodeTargetLevel should be also updated
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");
    importProject();
    assertEquals("1.5", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")));
  }

  private static String createPomXmlWithModuleDependency(final String dependencyType) {
    return "<groupId>test</groupId>" +
           "<artifactId>m1</artifactId>" +
           "<version>1</version>" +

           "<dependencies>" +
           "  <dependency>" +
           "    <groupId>test</groupId>" +
           "    <artifactId>m2</artifactId>" +
           "    <version>1</version>" +
           "    <type>" + dependencyType + "</type>" +
           "  </dependency>" +
           "</dependencies>";
  }

  public void testReimportingWhenModuleHaveRootOfTheParent() throws Exception {
    createProjectSubDir("m1/res");
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    "  <resources>" +
                    "    <resource><directory>../m1</directory></resource>" +
                    "  </resources>" +
                    "</build>");

    AtomicInteger counter = configConfirmationForNoAnswer();
    importProject();
    resolveDependenciesAndImport();
    assertEquals(0, counter.get());
  }

  public void testParentVersionProperty() throws Exception {
    String parentPomTemplate =
                    "<groupId>test</groupId>\n" +
                    "<artifactId>project</artifactId>\n" +
                    "<version>${my.parent.version}</version>\n" +
                    "<modules>\n" +
                    "  <module>m1</module>\n" +
                    "</modules>\n" +
                    "<properties>\n" +
                    "  <my.parent.version>1</my.parent.version>\n" +
                    "</properties>\n" +
                    "<build>\n" +
                    "  <plugins>\n" +
                    "    <plugin>\n" +
                    "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                    "      <version>3.1</version>\n" +
                    "      <configuration>\n" +
                    "        <source>%s</source>\n" +
                    "        <target>%<s</target>\n" +
                    "      </configuration>\n" +
                    "    </plugin>\n" +
                    "  </plugins>\n" +
                    "</build>";
    createProjectPom(String.format(parentPomTemplate, "1.8"));

    createModulePom("m1",
                    "<parent>\n" +
                    "  <groupId>test</groupId>\n" +
                    "  <artifactId>project</artifactId>\n" +
                    "  <version>${my.parent.version}</version>\n" +
                    "</parent>\n" +
                    "<artifactId>m1</artifactId>\n" +
                    "<version>${parent.version}</version>");

    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);

    configConfirmationForYesAnswer();
    importProjectWithMaven3();
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule("project")));
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule("m1")));
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule("project")));
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule("m1")));

    createProjectPom(String.format(parentPomTemplate, "1.7"));

    importProjectWithMaven3();
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule("project")));
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule("m1")));
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule("project")));
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule("m1")));
  }

  public void testParentVersionProperty2() throws Exception {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +
                     "<modules>\n" +
                     "  <module>m1</module>\n" +
                     "</modules>");

    String m1pomTemplate = "<parent>\n" +
                 "  <groupId>${my.parent.groupId}</groupId>\n" +
                 "  <artifactId>project</artifactId>\n" +
                 "  <version>${my.parent.version}</version>\n" +
                 "</parent>\n" +
                 "<artifactId>m1</artifactId>\n" +
                 "<version>${my.parent.version}</version>\n" +
                 "<properties>\n" +
                 "  <my.parent.version>1</my.parent.version>\n" +
                 "  <my.parent.groupId>test</my.parent.groupId>\n" +
                 "</properties>\n" +
                 "<build>\n" +
                 "  <plugins>\n" +
                 "    <plugin>\n" +
                 "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                 "      <version>3.1</version>\n" +
                 "      <configuration>\n" +
                 "        <source>%s</source>\n" +
                 "        <target>%<s</target>\n" +
                 "      </configuration>\n" +
                 "    </plugin>\n" +
                 "  </plugins>\n" +
                 "</build>";
    createModulePom("m1", String.format(m1pomTemplate, "1.8"));

    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);

    configConfirmationForYesAnswer();
    importProjectWithMaven3();
    assertEquals(LanguageLevel.JDK_1_8, getEffectiveLanguageLevel(getModule("m1")));
    assertEquals("1.8", compilerConfiguration.getBytecodeTargetLevel(getModule("m1")));

    createModulePom("m1", String.format(m1pomTemplate, "1.7"));

    importProjectWithMaven3();
    assertEquals(LanguageLevel.JDK_1_7, getEffectiveLanguageLevel(getModule("m1")));
    assertEquals("1.7", compilerConfiguration.getBytecodeTargetLevel(getModule("m1")));
  }
}
