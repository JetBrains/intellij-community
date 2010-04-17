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
package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.intentions.ChooseFileIntentionAction;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import javax.swing.*;
import java.awt.*;

public class MavenDependencyCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testGroupIdCompletion() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><caret></groupId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "junit", "jmock", "test");
  }

  public void testArtifactIdCompletion() throws Exception {
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

  public void testDoNotCompleteArtifactIdOnUnknownGroup() throws Exception {
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

  public void testDoNotCompleteArtifactIdIfNoGroupId() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom); // should not throw
  }

  public void testVersionCompletion() throws Exception {
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

    assertCompletionVariants(myProjectPom, "3.8.1", "3.8.2", "4.0");
  }

  public void testDoesNotCompleteVersionOnUnknownGroupOrArtifact() throws Exception {
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

    assertCompletionVariants(myProjectPom);

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

    assertCompletionVariants(myProjectPom);
  }

  public void testDoNotCompleteVersionIfNoGroupIdAndArtifactId() throws Exception {
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

  public void testAddingLocalProjectsIntoCompletion() throws Exception {
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

  public void testResolvingPropertiesForLocalProjectsInCompletion() throws Throwable {
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

    assertCompletionVariants(m, "1");

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

  public void testChangingExistingProjects() throws Exception {
    if (ignore()) return;

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

  public void testChangingExistingProjectsWithArtifactIdsRemoval() throws Exception {
    if (ignore()) return;

    createModulePom("m1",
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

    assertCompletionVariants(myProjectPom, "m1");

    createModulePom("m1", "");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><caret></groupId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom);
  }

  public void testRemovingExistingProjects() throws Exception {
    if (ignore()) return;

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

    assertCompletionVariants(myProjectPom, "m1");

    m.delete(null);

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
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(filePath);
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
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(filePath);
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

    assertResolved(myProjectPom, findPsiFile(LocalFileSystem.getInstance().findFileByPath(libPath)));
    checkHighlighting();
  }

  public void testHighlightInvalidSystemScopeDependencies() throws Throwable {
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

  public void testDoNotHighlightValidSystemScopeDependencies() throws Throwable {
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

  public void testChooseFileIntentionForSystemDependency() throws Throwable {
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
    VirtualFile libFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath);

    MyFileChooserFactory factory = new MyFileChooserFactory();
    factory.setFiles(new VirtualFile[]{libFile});
    ((ChooseFileIntentionAction)((IntentionActionWrapper)action).getDelegate()).setTestFileChooserFactory(factory);

    int prevValue = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().XML_TEXT_WRAP;
    try {
      // prevent file path from wrapping.
      CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().XML_TEXT_WRAP = CodeStyleSettings.DO_NOT_WRAP;
      myFixture.launchAction(action);
    }
    finally {
      CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().XML_TEXT_WRAP = prevValue;
    }

    MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(myProject, myProjectPom);
    MavenDomDependency dep = model.getDependencies().getDependencies().get(0);

    assertEquals(findPsiFile(libFile), dep.getSystemPath().getValue());
  }

  public void testNoChooseFileIntentionForNonSystemDependency() throws Throwable {
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

  public void testTypeCompletion() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <type><caret></type>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "jar", "pom", "ear", "ejb", "ejb-client", "war");
  }

  public void testDoNotHighlightUnknownType() throws Throwable {
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

  public void testScopeCompletion() throws Exception {
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

  public void testInvalidScopeHighlighting() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <scope><error>xxx</error></scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(myProjectPom);
  }

  public void testPropertiesInScopes() throws Throwable {
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

  public void testDoesNotHighlightCorrectValues() throws Throwable {
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

  public void testHighlightingArtifactIdAndVersionIfGroupIsUnknown() throws Throwable {
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

  public void testHighlightingArtifactAndVersionIfGroupIsEmpty() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><error><</error>/groupId>" +
                     "    <artifactId><error>junit</error></artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingVersionAndArtifactIfArtifactTheyAreFromAnotherGroup() throws Throwable {
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

  public void testHighlightingVersionIfArtifactIsEmpty() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId><error><</error>/artifactId>" +
                     "    <version><error>4.0</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingVersionIfArtifactIsUnknown() throws Throwable {
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

  public void testHighlightingVersionItIsFromAnotherGroup() throws Throwable {
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

  public void testDoNotHighlightVersionRanges() throws Throwable {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>jmock</groupId>" +
                     "    <artifactId>jmock</artifactId>" +
                     "    <version>[1,2]</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testHighlightingCoordinatesWithClosedTags() throws Throwable {
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

  public void testHandlingProperties() throws Throwable {
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

  public void testHandlingPropertiesWhenProjectIsNotYetLoaded() throws Throwable {
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

  public void testDoNotHighlightProblemsInNotLoadedProject() throws Throwable {
    VirtualFile m = createModulePom("not-a-module",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>project</artifactId>" +
                                    "<version>1</version>" +

                                    "<dependencies>" +
                                    "  <dependency>" +
                                    "    <groupId><error>xxx</error></groupId>" +
                                    "    <artifactId><error>xxx</error></artifactId>" +
                                    "    <version><error>xxx</error></version>" +
                                    "  </dependency>" +
                                    "</dependencies>");

    checkHighlighting(m);
  }

  public void testUpdateIndicesIntention() throws Throwable {
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

  public void testExclusionCompletion() throws Exception {
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

  public void testDoNotHighlightUnknownExclusions() throws Throwable {
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

  public void testExclusionHighlightingAbsentGroupId() throws Throwable {
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

  public void testExclusionHighlightingAbsentArtifactId() throws Throwable {
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

  private static class MyFileChooserFactory extends FileChooserFactory {
    private VirtualFile[] myFiles;

    public void setFiles(VirtualFile[] files) {
      myFiles = files;
    }

    public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Project project) {
      return new MyFileChooserDialog(myFiles);
    }

    public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Component parent) {
      return new MyFileChooserDialog(myFiles);
    }

    public FileTextField createFileTextField(FileChooserDescriptor descriptor, boolean showHidden, Disposable parent) {
      throw new UnsupportedOperationException();
    }

    public FileTextField createFileTextField(FileChooserDescriptor descriptor, Disposable parent) {
      throw new UnsupportedOperationException();
    }

    public void installFileCompletion(JTextField field, FileChooserDescriptor descriptor, boolean showHidden, Disposable parent) {
      throw new UnsupportedOperationException();
    }

    public FileSaverDialog createSaveFileDialog(FileSaverDescriptor descriptor, Project project) {
      throw new UnsupportedOperationException();
    }
  }

  private static class MyFileChooserDialog implements FileChooserDialog {
    private final VirtualFile[] myFiles;

    public MyFileChooserDialog(VirtualFile[] files) {
      myFiles = files;
    }

    @NotNull
    public VirtualFile[] choose(@Nullable VirtualFile toSelect, @Nullable Project project) {
      return myFiles;
    }
  }
}
