// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.intentions.ChooseFileIntentionAction;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MavenDependencyCompletionAndResolutionTest extends MavenDomWithIndicesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Test
  public void testGroupIdCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><caret></groupId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "junit", "jmock", "test");
  }

  @Test 
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

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "junit");
  }

  @Test 
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

  @Test 
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
    assertEquals(Arrays.asList("4.0", "3.8.2", "3.8.1"), variants);
  }

  @Test 
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

  @Test 
  public void testAddingLocalProjectsIntoCompletion() {
    createProjectPom("<groupId>project-group</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

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

    assertCompletionVariants(m, LOOKUP_STRING, "project-group:project:1", "project-group:m1:1", "project-group:m2:2");
    assertCompletionVariants(m, RENDERING_TEXT, "project", "m1", "m2");
  }

  @Test 
  public void testResolvingPropertiesForLocalProjectsInCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

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

  @Test 
  public void testChangingExistingProjects() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

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

    assertCompletionVariants(m1, LOOKUP_STRING, "test:project:1", "test:m1:1", "test:m2:1");
    assertCompletionVariants(m1, RENDERING_TEXT, "project", "m1", "m2");

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

    assertCompletionVariants(m1, LOOKUP_STRING, "test:project:1", "test:m1:1", "test:m2_new:1");
    assertCompletionVariants(m1, RENDERING_TEXT, "project", "m1", "m2_new");
  }

  @Test 
  public void testChangingExistingProjectsWithArtifactIdsRemoval() {
    VirtualFile m = createModulePom("m1",
                                    "<groupId>project-group</groupId>" +
                                    "<artifactId>m1</artifactId>" +
                                    "<version>1</version>");

    configureProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>project-group</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProjectsWithErrors(myProjectPom, m);

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "m1");

    createModulePom("m1", "");
    importProjectsWithErrors(myProjectPom, m);

    configureProjectPom("<groupId>test</groupId>" +
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

  @Test 
  public void testRemovingExistingProjects() throws IOException {
    final VirtualFile m1 = createModulePom("m1",
                                          "<groupId>project-group</groupId>" +
                                          "<artifactId>m1</artifactId>" +
                                          "<version>1</version>");

    final VirtualFile m2 = createModulePom("m2",
                                          "<groupId>project-group</groupId>" +
                                          "<artifactId>m2</artifactId>" +
                                          "<version>1</version>");

    configureProjectPom("<groupId>test</groupId>" +
                        "<artifactId>project</artifactId>" +
                        "<version>1</version>" +

                        "<dependencies>" +
                        "  <dependency>" +
                        "    <groupId>project-group</groupId>" +
                        "    <artifactId><caret></artifactId>" +
                        "  </dependency>" +
                        "</dependencies>");

    importProjectsWithErrors(myProjectPom, m1, m2);

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "m1", "m2");
    assertCompletionVariantsInclude(myProjectPom, LOOKUP_STRING, "project-group:m1:1", "project-group:m2:1");

    WriteAction.runAndWait(() -> m1.delete(null));

    configConfirmationForYesAnswer();

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "m2");
    assertCompletionVariantsInclude(myProjectPom, LOOKUP_STRING, "project-group:m2:1");
  }

  @Test 
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

  @Test 
  public void testResolutionParentPathOutsideTheProject() throws Exception {

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/org/example/1.0/example-1.0.pom");

    String relativePathUnixSeparator =
      FileUtil.getRelativePath(new File(myProjectRoot.getPath()), new File(filePath)).replaceAll("\\\\", "/");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<parent>" +
                     "  <groupId>org.example</groupId>" +
                     "  <artifactId>example</artifactId>" +
                     "  <version>1.0</version>" +
                     "  <relativePath>" + relativePathUnixSeparator + "<caret></relativePath>" +
                     "</parent>"
    );

    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  @Test 
  public void testResolveManagedDependency() throws Exception {
    configureProjectPom("<groupId>test</groupId>" +
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
    importProject();

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
    assertResolved(myProjectPom, findPsiFile(f));
  }

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
  public void testHighlightInvalidSystemScopeDependencies() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><error  descr=\"Dependency 'xxx:xxx:xxx' not found\">xxx</error></groupId>" +
                     "    <artifactId><error  descr=\"Dependency 'xxx:xxx:xxx' not found\">xxx</error></artifactId>" +
                     "    <version><error  descr=\"Dependency 'xxx:xxx:xxx' not found\">xxx</error></version>" +
                     "    <scope>system</scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  @Test 
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

  @Test 
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

  @Test 
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

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "junit-4.0.jar");
  }

  @Test 
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

  @Test 
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

    IntentionAction intentionAction = IntentionActionDelegate.unwrap(action);
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

  @Test 
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

  @Test 
  public void testTypeCompletion() {
    configureProjectPom("<groupId>test</groupId>" +
                        "<artifactId>project</artifactId>" +
                        "<version>1</version>" +

                        "<dependencies>" +
                        "  <dependency>" +
                        "    <type><caret></type>" +
                        "  </dependency>" +
                        "</dependencies>");

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "jar", "test-jar", "pom", "ear", "ejb", "ejb-client", "war", "bundle",
                             "jboss-har", "jboss-sar", "maven-plugin");
  }

  @Test 
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

  @Test 
  public void testScopeCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <scope><caret></scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "compile", "provided", "runtime", "test", "system");
  }

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
  public void testHighlightingVersionIfVersionIsWrong() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version><error>4.0.wrong</error></version>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }


  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
  public void testHighlightingCoordinatesWithClosedTags() {
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "jmock");
  }


  @Test 
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

  @Test 
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

  @Test 
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

  @Test
  public void testImportDependencyChainedProperty() throws IOException {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "   <module>m1</module>" +
                     "</modules>" +
                     "<dependencyManagement>" +
                     "    <dependencies>" +
                     "        <dependency>" +
                     "            <groupId>org.deptest</groupId>" +
                     "            <artifactId>bom-depparent</artifactId>" +
                     "            <version>1.0</version>" +
                     "            <type>pom</type>" +
                     "            <scope>import</scope>" +
                     "        </dependency>" +
                     "    </dependencies>" +
                     "</dependencyManagement>");

    createModulePom("m1", "<parent>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>project</artifactId>" +
                          "    <version>1</version>" +
                          "  </parent>" +
                          "<artifactId>m1</artifactId>" +
                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>org.example</groupId>" +
                          "    <artifactId>something</artifactId>" +
                          "  </dependency>" +
                          "</dependencies>");
    importProjectWithErrors();

    MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(myProject, myProjectPom, MavenDomProjectModel.class);

    MavenDomDependency dependency = MavenDependencyCompletionUtil.findManagedDependency(model, myProject, "org.example", "something");
    assertNotNull(dependency);
    assertEquals("42", dependency.getVersion().getStringValue());
  }
}
