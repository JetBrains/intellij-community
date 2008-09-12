package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.openapi.fileChooser.TestFileChooserFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.dom.model.MavenModel;

public class DependencyCompletionAndResolutionTest extends MavenCompletionAndResolutionWithIndicesTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testGroupIdCompletion() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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

  public void testVersionCompletion() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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

    updateProjectPom("<groupId>test</groupId>" +
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

  public void testAddingLocalProjectsIntoCompletion() throws Exception {
    updateProjectPom("<groupId>project-group</groupId>" +
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

    updateModulePom("m2",
                    "<groupId>project-group</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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

    updateModulePom("m2",
                    "<groupId>test</groupId>" +
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

    updateModulePom("m2",
                    "<groupId>test</groupId>" +
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

    updateProjectPom("<groupId>test</groupId>" +
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

    updateModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>test</groupId>" +
                    "    <artifactId><caret></artifactId>" +
                    "  </dependency>" +
                    "</dependencies>");

    assertCompletionVariants(m1, "project", "m1", "m2");

    updateModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    updateModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2_new</artifactId>" +
                    "<version>1</version>");

    importProject();

    updateModulePom("m1",
                    "<groupId>test</groupId>" +
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

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>project-group</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "m1");

    updateModulePom("m1", "");

    updateProjectPom("<groupId>test</groupId>" +
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

    updateProjectPom("<groupId>test</groupId>" +
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

    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId><caret>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(filePath);
    assertEquals(getPsiFile(f), ref.resolve());
  }

  public void testResolutionIsTypeBased() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
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

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    String filePath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom");
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(filePath);
    assertEquals(getPsiFile(f), ref.resolve());
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

    importSeveralProjects(m1, m2);

    updateModulePom("m1",
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


    PsiReference ref = getReferenceAtCaret(m1);
    assertNotNull(ref);
    assertEquals(getPsiFile(m2), ref.resolve());
  }

  public void testDoNotHighlightSystemScopeDependencies() throws Throwable {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version>xxx</version>" +
                     "    <scope>system</scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting();
  }

  public void testResolvingSystemScopeDependencies() throws Throwable {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    updateProjectPom("<groupId>test</groupId>" +
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

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    assertEquals(getPsiFile(LocalFileSystem.getInstance().findFileByPath(libPath)), ref.resolve());
    checkHighlighting();
  }

  public void testResolvingSystemScopeDependenciesWithProperties() throws Throwable {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    updateProjectPom("<groupId>test</groupId>" +
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

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    assertEquals(getPsiFile(LocalFileSystem.getInstance().findFileByPath(libPath)), ref.resolve());
    checkHighlighting();
  }

  public void testResolvingSystemScopeDependenciesFromSystemPath() throws Throwable {
    String libPath = myIndicesFixture.getRepositoryHelper().getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar");

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>xxx</artifactId>" +
                     "    <version>xxx</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath><caret>" + libPath + "</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    PsiReference ref = getReferenceAtCaret(myProjectPom);
    assertNotNull(ref);

    assertEquals(getPsiFile(LocalFileSystem.getInstance().findFileByPath(libPath)), ref.resolve());
    checkHighlighting();
  }

  public void testChooseFileIntentionForSystemDependency() throws Throwable {
    updateProjectPom("<groupId>test</groupId>" +
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
    VirtualFile libFile = LocalFileSystem.getInstance().findFileByPath(libPath);

    TestFileChooserFactory factory = new TestFileChooserFactory();
    factory.setFiles(new VirtualFile[]{libFile});
    ((ChooseFileIntentionAction)((IntentionActionWrapper)action).getDelegate()).setTestFileChooserFactory(factory);

    int prevValue = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().XML_TEXT_WRAP;
    try {
      // prevent file path from wrapping.
      CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().XML_TEXT_WRAP = CodeStyleSettings.DO_NOT_WRAP;
      myCodeInsightFixture.launchAction(action);
    }
    finally {
      CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().XML_TEXT_WRAP = prevValue;
    }

    DomFileElement<MavenModel> model =
        DomManager.getDomManager(myProject).getFileElement((XmlFile)getPsiFile(myProjectPom), MavenModel.class);
    Dependency dep = model.getRootElement().getDependencies().getDependencies().get(0);

    assertEquals(getPsiFile(libFile), dep.getSystemPath().getValue());
  }

  public void testNoChooseFileIntentionForNonSystemDependency() throws Throwable {
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <type>xxx</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(myProjectPom);
  }

  public void testScopeCompletion() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <scope><caret></scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariants(myProjectPom, "compile", "provided", "runtime", "test", "system", "import");
  }

  public void testInvalidScopeHighlighting() throws Throwable {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <scope><error>xxx</error></scope>" +
                     "  </dependency>" +
                     "</dependencies>");

    checkHighlighting(myProjectPom);
  }

  public void testDoesNotHighlightCorrectValues() throws Throwable {
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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

  public void testHandlingProperties() throws Throwable {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <dep.groupId>junit</dep.groupId>" +
                     "  <dep.artifactId>junit</dep.artifactId>" +
                     "  <dep.version>4.0</dep.version>" +
                     "</properties>");
    importProject();

    updateProjectPom("<groupId>test</groupId>" +
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
    updateProjectPom("<groupId>test</groupId>" +
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
                                    "    <groupId><error>${xxx}</error></groupId>" +
                                    "    <artifactId><error>${xxx}</error></artifactId>" +
                                    "    <version><error>${xxx}</error></version>" +
                                    "  </dependency>" +
                                    "</dependencies>");

    checkHighlighting(m);
  }

  public void testUpdateIndicesIntention() throws Throwable {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId><caret>xxx</groupId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertNotNull(getIntentionAtCaret("Update Maven Indices"));
  }
}