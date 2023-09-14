// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.importing.MavenProjectLegacyImporter;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public class MavenProjectsManagerAutoImportTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(true);
    Assume.assumeFalse(MavenUtil.isLinearImportEnabled());
  }


  @Test
  public void testResolvingEnvVariableInRepositoryPath() throws Exception {
    String temp = System.getenv(getEnvVar());
    updateSettingsXml("<localRepository>${env." + getEnvVar() + "}/tmpRepo</localRepository>");

    File repo = new File(temp + "/tmpRepo").getCanonicalFile();
    assertEquals(repo.getPath(), getMavenGeneralSettings().getEffectiveLocalRepository().getPath());

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """);

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + FileUtil.toSystemIndependentName(repo.getPath()) + "/junit/junit/4.0/junit-4.0.jar!/");
  }

  @Test
  public void testUpdatingProjectsOnProfilesXmlChange() throws IOException {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       <build>
                         <sourceDirectory>${prop}</sourceDirectory>
                       </build>
                       """);

    createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>${prop}</sourceDirectory>
                      </build>
                      """);

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value1</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);

    importProject();

    List<MavenProject> roots = getProjectsTree().getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = getProjectsTree().getModules(roots.get(0)).get(0);

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);
    importProject();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));

    updateSettingsXml("<profiles/>");
    importProject();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/${prop}")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}")));

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value2</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);
    importProject();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));
  }

  @Test
  public void testUpdatingProjectsWhenSettingsXmlLocationIsChanged() throws Exception {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       <build>
                         <sourceDirectory>${prop}</sourceDirectory>
                       </build>
                       """);

    createModulePom("m",
                    """
                      <groupId>test</groupId>
                      <artifactId>m</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>${prop}</sourceDirectory>
                      </build>
                      """);

    updateSettingsXml("""
                        <profiles>
                          <profile>
                            <id>one</id>
                            <activation>
                              <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                              <prop>value1</prop>
                            </properties>
                          </profile>
                        </profiles>
                        """);

    importProject();

    List<MavenProject> roots = getProjectsTree().getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = getProjectsTree().getModules(roots.get(0)).get(0);

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));

    getMavenGeneralSettings().setUserSettingsFile("");
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/${prop}")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}")));

    getMavenGeneralSettings().setUserSettingsFile(new File(myDir, "settings.xml").getPath());
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));
  }

  @Test
  public void testUpdatingMavenPathsWhenSettingsChanges() throws Exception {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """);

    File repo1 = new File(myDir, "localRepo1");
    updateSettingsXml("<localRepository>\n" + repo1.getPath() + "</localRepository>");

    waitForReadingCompletion();
    assertEquals(repo1, getMavenGeneralSettings().getEffectiveLocalRepository());

    File repo2 = new File(myDir, "localRepo2");
    updateSettingsXml("<localRepository>\n" + repo2.getPath() + "</localRepository>");

    waitForReadingCompletion();
    assertEquals(repo2, getMavenGeneralSettings().getEffectiveLocalRepository());
  }

  @Test
  public void testSchedulingReimportWhenPomFileIsDeleted() throws IOException {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """);

    final VirtualFile m = createModulePom("m",
                                          """
                                            <groupId>test</groupId>
                                            <artifactId>m</artifactId>
                                            <version>1</version>
                                            """);
    importProject();
    //myProjectsManager.performScheduledImportInTests(); // ensure no pending requests
    assertModules("project", mn("project", "m"));

    runWriteAction(() -> m.delete(this));

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    scheduleProjectImportAndWait();
    assertModules("project");
  }

  @Test
  public void testHandlingDirectoryWithPomFileDeletion() throws IOException {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    """);

    createModulePom("dir/module", """
      <groupId>test</groupId>
      <artifactId>module</artifactId>
      <version>1</version>
      """);
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir/module</module>
                       </modules>
                       """);
    scheduleProjectImportAndWait();

    assertEquals(2, MavenProjectsManager.getInstance(myProject).getProjects().size());

    final VirtualFile dir = myProjectRoot.findChild("dir");
    WriteCommandAction.writeCommandAction(myProject).run(() -> dir.delete(null));

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    scheduleProjectImportAndWait();

    assertEquals(1, MavenProjectsManager.getInstance(myProject).getProjects().size());
  }

  @Test
  public void testScheduleReimportWhenPluginConfigurationChangesInTagName() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <configuration>
                            <foo>value</foo>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """);
    assertNoPendingProjectForReload();

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>group</groupId>
                             <artifactId>id</artifactId>
                             <version>1</version>
                             <configuration>
                               <bar>value</bar>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);
    assertHasPendingProjectForReload();

    scheduleProjectImportAndWait();
  }


  @Test
  public void testUpdatingProjectsWhenAbsentModuleFileAppears() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                    """);

    List<MavenProject> roots = getProjectsTree().getRootProjects();
    MavenProject parentNode = roots.get(0);

    assertNotNull(parentNode);
    assertTrue(getProjectsTree().getModules(roots.get(0)).isEmpty());

    VirtualFile m = createModulePom("m",
                                    """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """);
    scheduleProjectImportAndWait();

    List<MavenProject> children = getProjectsTree().getModules(roots.get(0));
    assertEquals(1, children.size());
    assertEquals(m, children.get(0).getFile());
  }

  @Test
  public void testScheduleReimportWhenPluginConfigurationChangesInValue() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>group</groupId>
                          <artifactId>id</artifactId>
                          <version>1</version>
                          <configuration>
                            <foo>value</foo>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """);
    assertNoPendingProjectForReload();

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <plugins>
                           <plugin>
                             <groupId>group</groupId>
                             <artifactId>id</artifactId>
                             <version>1</version>
                             <configuration>
                               <foo>value2</foo>
                             </configuration>
                           </plugin>
                         </plugins>
                       </build>
                       """);
    assertHasPendingProjectForReload();

    scheduleProjectImportAndWait();
  }

  @Test
  public void testSchedulingResolveOfDependentProjectWhenDependencyChanges() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """);

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """);

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);

    importProject();

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
        </dependency>
      </dependencies>
      """);

    scheduleProjectImportAndWait();

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");
  }


  @Test
  public void testAddingManagedFileAndChangingAggregation() {
    assumeTrue(isWorkspaceImport());
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                    """);

    VirtualFile m = createModulePom("m",
                                    """
                                      <groupId>test</groupId>
                                      <artifactId>m</artifactId>
                                      <version>1</version>
                                      """);
    scheduleProjectImportAndWait();

    assertEquals(1, getProjectsTree().getRootProjects().size());
    assertEquals(1, getProjectsTree().getModules(getProjectsTree().getRootProjects().get(0)).size());

    getProjectsManager().addManagedFiles(Arrays.asList(m));
    waitForReadingCompletion();

    assertEquals(1, getProjectsTree().getRootProjects().size());
    assertEquals(1, getProjectsTree().getModules(getProjectsTree().getRootProjects().get(0)).size());

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """);
    importProject();

    assertEquals(1, getProjectsTree().getRootProjects().size());
    assertEquals(0, getProjectsTree().getModules(getProjectsTree().getRootProjects().get(0)).size());
  }

  @Test
  public void testSchedulingResolveOfDependentProjectWhenDependencyIsDeleted() throws IOException {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """);

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """);

    final VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
        </dependency>
      </dependencies>
      """);

    importProject();

    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");

    WriteCommandAction.writeCommandAction(myProject).run(() -> m2.delete(this));


    //configConfirmationForYesAnswer();// should update deps even if module is not removed
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    scheduleProjectImportAndWait();

    assertModules("project", "m1");

    assertModuleModuleDeps("m1");
    assertModuleLibDeps("m1", "Maven: test:m2:1");
  }

  @Test
  public void testUpdatingProjectsWhenAbsentManagedProjectFileAppears() throws IOException {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                    """);
    assertEquals(1, getProjectsTree().getRootProjects().size());

    WriteCommandAction.writeCommandAction(myProject).run(() -> myProjectPom.delete(this));

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    importProject();

    assertEquals(0, getProjectsTree().getRootProjects().size());

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m</module>
                       </modules>
                       """);
    //importProject();
    scheduleProjectImportAndWait();

    assertEquals(1, getProjectsTree().getRootProjects().size());
  }

  @Test
  public void testUpdatingProjectsWhenRenaming() throws IOException {
    VirtualFile p1 = createModulePom("project1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """);

    final VirtualFile p2 = createModulePom("project2",
                                           """
                                             <groupId>test</groupId>
                                             <artifactId>project2</artifactId>
                                             <version>1</version>
                                             """);
    importProjects(p1, p2);

    assertEquals(2, getProjectsTree().getRootProjects().size());

    runWriteAction(() -> p2.rename(this, "foo.bar"));

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(1, getProjectsTree().getRootProjects().size());

    runWriteAction(() -> p2.rename(this, "pom.xml"));
    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(2, getProjectsTree().getRootProjects().size());
  }

  @Test
  public void testUpdatingProjectsWhenMoving() throws IOException, InterruptedException {
    VirtualFile p1 = createModulePom("project1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """);

    final VirtualFile p2 = createModulePom("project2",
                                           """
                                             <groupId>test</groupId>
                                             <artifactId>project2</artifactId>
                                             <version>1</version>
                                             """);
    importProjects(p1, p2);

    final VirtualFile oldDir = p2.getParent();
    runWriteAction(() -> VfsUtil.markDirtyAndRefresh(false, true, true, myProjectRoot));
    VirtualFile newDir = runWriteAction(() -> myProjectRoot.createChildDirectory(this, "foo"));
    assertEquals(2, getProjectsTree().getRootProjects().size());

    runWriteAction(() -> p2.move(this, newDir));

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(1, getProjectsTree().getRootProjects().size());

    runWriteAction(() -> p2.move(this, oldDir));
    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(2, getProjectsTree().getRootProjects().size());
  }

  @Test
  public void testUpdatingProjectsWhenMovingModuleFile() throws IOException {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>parent</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """);

    final VirtualFile m = createModulePom("m1",
                                          """
                                            <groupId>test</groupId>
                                            <artifactId>m</artifactId>
                                            <version>1</version>
                                            """);
    importProject();

    final VirtualFile oldDir = m.getParent();
    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      VirtualFile newDir = myProjectRoot.createChildDirectory(this, "m2");

      assertEquals(1, getProjectsTree().getRootProjects().size());
      assertEquals(1, getProjectsTree().getModules(getProjectsTree().getRootProjects().get(0)).size());

      m.move(this, newDir);
      scheduleProjectImportAndWaitWithoutCheckFloatingBar();

      assertEquals(1, getProjectsTree().getModules(getProjectsTree().getRootProjects().get(0)).size());

      m.move(this, oldDir);
      scheduleProjectImportAndWaitWithoutCheckFloatingBar();

      assertEquals(1, getProjectsTree().getModules(getProjectsTree().getRootProjects().get(0)).size());

      m.move(this, myProjectRoot.createChildDirectory(this, "xxx"));
    });

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    getProjectsManager().forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    waitForImportCompletion();
    assertEquals(0, getProjectsTree().getModules(getProjectsTree().getRootProjects().get(0)).size());
  }

  /**
   * temporary solution. since The maven deletes files during the import process (renaming the file).
   * And therefore the floating bar is always displayed.
   * Because there is no information who deleted the import file or the other user action
   * problem in MavenProjectsAware#collectSettingsFiles() / yieldAll(projectsTree.projectsFiles.map { it.path })
   */
  private void scheduleProjectImportAndWaitWithoutCheckFloatingBar() {
    ExternalSystemProjectTracker.getInstance(myProject).scheduleProjectRefresh();
    resolveDependenciesAndImport();
  }
}
