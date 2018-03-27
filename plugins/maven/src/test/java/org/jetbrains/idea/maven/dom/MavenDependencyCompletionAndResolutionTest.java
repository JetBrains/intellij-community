/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.dom.intentions.ChooseFileIntentionAction;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MavenDependencyCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testGroupIdCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><caret></groupId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariantsInclude(myProjectPom, "junit", "jmock", "test");
  }

  public void testArtifactIdCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "junit");
  }

  public void testDoNotCompleteArtifactIdOnUnknownGroup() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>unknown</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom);
  }

  public void testVersionCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version><caret></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    List<String> variants = getCompletionVariants(myProjectPom);
    assertEquals(Arrays.asList("4.0", "3.8.2", "3.8.1", "RELEASE", "LATEST"), variants);
  }

  public void testDoesNotCompleteVersionOnUnknownGroupOrArtifact() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>unknown</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version><caret></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "RELEASE", "LATEST");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>unknown</artifactId>" +
                     "    <version><caret></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "RELEASE", "LATEST");
  }

  public void testDoNotCompleteVersionIfNoGroupIdAndArtifactId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <version><caret></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom); // should not throw
  }

  public void testAddingLocalProjectsIntoCompletion() {
    createProjectPom("<groupId>project-group</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     " <module>m1</module>" +
                     " <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>project-group</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    VirtualFile m = createModulePom("m2",
                                    "<groupId>project-group</groupId>" +
                                    "<artifactId>m2</artifactId>" +
                                    "<version>2</version>");

    importProject();

    createModulePom("m2", "<groupId>project-group</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>2</version>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>project-group</groupId>" +
                    "    <artifactId><caret></artifactId>" +
                    "  </dependency>" +
                    "</dependencies>");

    assertCompletionVariants(m, "project", "m1", "m2");
  }

  public void testResolvingPropertiesForLocalProjectsInCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <module1Name>module1</module1Name>" +
                     "</properties>" +

                     "<modules>" +
                     " <module>m1</module>" +
                     " <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>${pom.parent.groupId}</groupId>" +
                    "<artifactId>${module1Name}</artifactId>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");

    VirtualFile m = createModulePom("m2",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>module2</artifactId>" +
                                    "<version>1</version>");

    importProject();
    assertModules("project", "module1", "module2");

    createModulePom("m2", "<groupId>test</groupId>" +
                    "<artifactId>module2</artifactId>" +
                    "<version>1</version>" +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId>module1</artifactId>" +
                    "    <version><caret></version>" +
                    "  </dependency>" +
                    "</dependencies>");

    assertCompletionVariants(m, "RELEASE", "LATEST", "1");

    createModulePom("m2", "<groupId>test</groupId>" +
                    "<artifactId>module2</artifactId>" +
                    "<version>1</version>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId>module1</artifactId>" +
                    "    <version>1</version>" +
                    "  </dependency>" +
                    "</dependencies>");

    checkHighlighting(m);
  }

  public void testChangingExistingProjects() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     " <module>m1</module>" +
                     " <module>m2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");
    importProject();

    createModulePom("m1", "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId><caret></artifactId>" +
                    "  </dependency>" +
                    "</dependencies>");

    assertCompletionVariants(m1, "project", "m1", "m2");

    createModulePom("m1", "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                    "<artifactId>m2_new</artifactId>" +
                    "<version>1</version>");

    importProject();

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId><caret></artifactId>" +
                          "  </dependency>" +
                          "</dependencies>");

    assertCompletionVariants(m1, "project", "m1", "m2_new");
  }

  public void testChangingExistingProjectsWithArtifactIdsRemoval() {
    VirtualFile m = createModulePom("m1",
                                    "<groupId>project-group</groupId>" +
                                    "<artifactId>m1</artifactId>" +
                                    "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>project-group</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProjects(myProjectPom, m);

    assertCompletionVariants(myProjectPom, "m1");

    createModulePom("m1", "");
    importProjects(myProjectPom, m);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>project-group</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom);
  }

  public void testRemovingExistingProjects() {
    final VirtualFile m = createModulePom("m1",
                                    "<groupId>project-group</groupId>" +
                                    "<artifactId>m1</artifactId>" +
                                    "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>project-group</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProjects(myProjectPom, m);

    assertCompletionVariants(myProjectPom, "m1");

    myProjectsManager.listenForExternalChanges();
    new WriteAction() {
      protected void run(@NotNull Result result) throws Throwable {
        m.delete(null);
      }
    }.execute();

    waitForReadingCompletion();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>project-group</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom);
  }

  public void testResolutionOutsideTheProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId><caret>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  public void testResolveManagedDependency() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencyManagement>" +
                  "  <dependencies>" +
                  "    <dependency>" +
                  "      <groupId>junit</groupId>" +
                  "      <artifactId>junit</artifactId>" +
                  "      <version>4.0</version>" +
                  "    </dependency>" +
                  "  </dependencies>" +
                  "</dependencyManagement>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit<caret></artifactId>" +
                  "  </dependency>" +
                  "</dependencies>");

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  public void testResolveSystemManagedDependency() {
    String someJarPath = PathUtil.getJarPathForClass(ArrayList.class).replace('\\', '/');

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencyManagement>" +
                  "  <dependencies>" +
                  "    <dependency>" +
                  "      <groupId>direct-system-dependency</groupId>" +
                  "      <artifactId>direct-system-dependency</artifactId>" +
                  "      <version>1.0</version>" +
                  "      <scope>system</scope>" +
                  "      <systemPath>" + someJarPath + "</systemPath>" +
                  "    </dependency>" +
                  "  </dependencies>" +
                  "</dependencyManagement>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>direct-system-dependency</groupId>" +
                  "    <artifactId>direct-system-dependency</artifactId>" +
                  "  </dependency>" +
                  "</dependencies>");

    createProjectPom("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<version>1</version>" +

                      "<dependencyManagement>" +
                      "  <dependencies>" +
                      "    <dependency>" +
                      "      <groupId>direct-system-dependency</groupId>" +
                      "      <artifactId>direct-system-dependency</artifactId>" +
                      "      <version>1.0</version>" +
                      "      <scope>system</scope>" +
                      "      <systemPath>" + someJarPath + "</systemPath>" +
                      "    </dependency>" +
                      "  </dependencies>" +
                      "</dependencyManagement>" +

                      "<dependencies>" +
                      "  <dependency>" +
                      "    <groupId>direct-system-dependency</groupId>" +
                      "    <artifactId>direct-system-dependency</artifactId>" +
                      "  </dependency>" +
                      "</dependencies>");

    checkHighlighting(myProjectPom, true, false, true);
  }

  public void testResolveLATESTDependency() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
    String repoPath = helper.getTestDataPath("local1");
    setRepositoryPath(repoPath);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>[1,4.0]</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    createProjectPom("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<version>1</version>" +

                      "<dependencies>" +
                      "  <dependency>" +
                      "    <groupId>junit</groupId>" +
                      "    <artifactId>junit<caret></artifactId>" +
                      "    <version>[1,4.0]</version>" +
                      "  </dependency>" +
                      "</dependencies>");

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);

    assertResolved(myProjectPom, findPsiFile(f));
  }

  public void testResolutionIsTypeBased() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId><caret>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <type>pom</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  public void testResolutionInsideTheProject() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    importProjects(myProjectPom, m1, m2);

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId><caret>m2</artifactId>" +
                    "    <version>1</version>" +
                    "  </dependency>" +
                    "</dependencies>");

    assertResolved(m1, findPsiFile(m2));
  }

  public void testResolvingSystemScopeDependencies() throws Throwable {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version><caret>xxx</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>" + libPath + "</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertResolved(myProjectPom, findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath)));
    checkHighlighting();
  }

  public void testHighlightInvalidSystemScopeDependencies() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><error>xxx</error></groupId>" +
                     "    <artifactId><error>xxx</error></artifactId>" +
                     "    <version><error>xxx</error></version>" +
                     "    <scope>system</scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testDoNotHighlightValidSystemScopeDependencies() {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version>xxx</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>" + libPath + "</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");
    checkHighlighting();
  }

  public void testResolvingSystemScopeDependenciesWithProperties() throws Throwable {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <depPath>" + libPath + "</depPath>" +
                     "</properties>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version><caret>xxx</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>${depPath}</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertResolved(myProjectPom, findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath)));
    checkHighlighting();
  }

  public void testCompletionSystemScopeDependenciesWithProperties() {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <depDir>" + new File(libPath).getParent() + "</depDir>" +
                     "</properties>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version>xxx</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>${depDir}/<caret></systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "junit-4.0.jar");
  }

  public void testResolvingSystemScopeDependenciesFromSystemPath() throws Throwable {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version>xxx</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>" + libPath + "<caret></systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertResolved(myProjectPom, findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath)));
    checkHighlighting();
  }

  public void testChooseFileIntentionForSystemDependency() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency><caret>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version>xxx</version>" +
                     "    <scope>system</system>" +
                     "  </dependency>" +
                     "</dependencies>");

    IntentionAction action = getIntentionAtCaret("Choose File");
    assertNotNull(action);

    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");
    final VirtualFile libFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath);

    IntentionAction intentionAction = action;
    while (intentionAction instanceof IntentionActionDelegate) {
      intentionAction = ((IntentionActionDelegate)intentionAction).getDelegate();
    }

    ((ChooseFileIntentionAction)intentionAction).setFileChooser(() -> new VirtualFile[]{libFile});
    XmlCodeStyleSettings xmlSettings =
      CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().getCustomSettings(XmlCodeStyleSettings.class);

    int prevValue = xmlSettings.XML_TEXT_WRAP;
    try {
      // prevent file path from wrapping.
      xmlSettings.XML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
      myFixture.launchAction(action);
    }
    finally {
      xmlSettings.XML_TEXT_WRAP = prevValue;
      ((ChooseFileIntentionAction)intentionAction).setFileChooser(null);
    }

    MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(myProject, myProjectPom);
    MavenDomDependency dep = model.getDependencies().getDependencies().get(0);

    assertEquals(findPsiFile(libFile), dep.getSystemPath().getValue());
  }

  public void testNoChooseFileIntentionForNonSystemDependency() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency><caret>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version>xxx</version>" +
                     "    <scope>compile</system>" +
                     "  </dependency>" +
                     "</dependencies>");

    IntentionAction action = getIntentionAtCaret("Choose File");
    assertNull(action);
  }

  public void testTypeCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <type><caret></type>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "jar", "test-jar", "pom", "ear", "ejb", "ejb-client", "war", "bundle", "jboss-har", "jboss-sar", "maven-plugin");
  }

  public void testDoNotHighlightUnknownType() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <type>xxx</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(myProjectPom);
  }

  public void testScopeCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <scope><caret></scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "compile", "provided", "runtime", "test", "system");
  }

  public void testDoNotHighlightUnknownScopes() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <scope>xxx</scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(myProjectPom);
  }

  public void testPropertiesInScopes() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <my.scope>compile</my.scope>" +
                     "</properties>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <scope>${my.scope}</scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(myProjectPom);
  }

  public void testDoesNotHighlightCorrectValues() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingArtifactIdAndVersionIfGroupIsUnknown() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><error>unknown</error></groupId>" +
                     "    <artifactId><error>junit</error></artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingArtifactAndVersionIfGroupIsEmpty() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><error></error></groupId>" +
                     "    <artifactId><error>junit</error></artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingVersionAndArtifactIfArtifactTheyAreFromAnotherGroup() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>jmock</groupId>" +
                     "    <artifactId><error>junit</error></artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingVersionIfArtifactIsEmpty() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId><error></error></artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingVersionIfArtifactIsUnknown() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId><error>unknown</error></artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingVersionItIsFromAnotherGroup() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>jmock</groupId>" +
                     "    <artifactId>jmock</artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingCoordinatesWithClosedTags() {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId/><error></error>" +
                     "    <artifactId/><error></error>" +
                     "    <version/><error></error>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHandlingProperties() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <dep.groupId>junit</dep.groupId>" +
                     "  <dep.artifactId>junit</dep.artifactId>" +
                     "  <dep.version>4.0</dep.version>" +
                     "</properties>");
    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     // properties are taken from loaded project

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>${dep.groupId}</groupId>" +
                     "    <artifactId>${dep.artifactId}</artifactId>" +
                     "    <version>${dep.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHandlingPropertiesWhenProjectIsNotYetLoaded() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <dep.groupId>junit</dep.groupId>" +
                     "  <dep.artifactId>junit</dep.artifactId>" +
                     "  <dep.version>4.0</dep.version>" +
                     "</properties>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>${dep.groupId}</groupId>" +
                     "    <artifactId>${dep.artifactId}</artifactId>" +
                     "    <version>${dep.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testDontHighlightProblemsInNonManagedPom1() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <junitVersion>4.0</junitVersion>" +
                     "</properties>");

    VirtualFile m = createModulePom("m1",
                                    "<artifactId>m1</artifactId>" +

                                    "<parent>" +
                                    "  <groupId>test</groupId>" +
                                    "  <artifactId>project</artifactId>" +
                                    "  <version>1</version>" +
                                    "</parent>" +
                                    "<dependencies>" +
                                    " <dependency>" +
                                    " <groupId>junit</groupId>" +
                                    " <artifactId>junit</artifactId>" +
                                    " <version>${junitVersion}</version>" +
                                    " </dependency>" +
                                    "</dependencies>");

    importProject();

    checkHighlighting(m, true, false, true);
  }

  public void testDontHighlightProblemsInNonManagedPom2() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <junitVersion>4.0</junitVersion>" +
                     "</properties>");

    VirtualFile m = createModulePom("m1",
                                    "<artifactId>m1</artifactId>" +

                                    "<parent>" +
                                    "  <groupId>test</groupId>" +
                                    "  <artifactId>project</artifactId>" +
                                    "  <version>1</version>" +
                                    "</parent>" +
                                    "<properties>" +
                                    " <aaa>${junitVersion}</aaa>" +
                                    "</properties>");

    importProject();
    checkHighlighting(m, true, false, true);
  }

  public void testUpdateIndicesIntention() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><caret>xxx</groupId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertNotNull(getIntentionAtCaret("Update Maven Indices"));
  }

  public void testExclusionCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <exclusions>" +
                     "      <exclusion>" +
                     "        <groupId>jmock</groupId>" +
                     "        <artifactId><caret></artifactId>" +
                     "      </exclusion>" +
                     "    </exclusions>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "jmock");
  }

  public void testDoNotHighlightUnknownExclusions() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <exclusions>" +
                     "      <exclusion>" +
                     "        <groupId>foo</groupId>" +
                     "        <artifactId>bar</artifactId>" +
                     "      </exclusion>" +
                     "    </exclusions>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testExclusionHighlightingAbsentGroupId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <exclusions>" +
                     "      <<error descr=\"'groupId' child tag should be defined\">exclusion</error>>" +
                     "        <artifactId>jmock</artifactId>" +
                     "      </exclusion>" +
                     "    </exclusions>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testExclusionHighlightingAbsentArtifactId() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <exclusions>" +
                     "      <<error descr=\"'artifactId' child tag should be defined\">exclusion</error>>" +
                     "        <groupId>jmock</groupId>" +
                     "      </exclusion>" +
                     "    </exclusions>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }
}
