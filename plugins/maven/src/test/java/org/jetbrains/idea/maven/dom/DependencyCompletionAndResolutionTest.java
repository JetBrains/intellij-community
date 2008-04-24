package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.repository.MavenRepositoryInfo;
import org.jetbrains.idea.maven.repository.MavenRepositoryManager;
import org.jetbrains.idea.maven.repository.MavenWithDataTestFixture;

public class DependencyCompletionAndResolutionTest extends MavenCompletionAndResolutionTestCase {
  private MavenWithDataTestFixture myDataTestFixture;
  private MavenRepositoryManager myRepositoryManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDataTestFixture = new MavenWithDataTestFixture(myDir);
    myDataTestFixture.setUp();

    myRepositoryManager = MavenRepositoryManager.getInstance(myProject);
    myRepositoryManager.initIndex();
    myRepositoryManager.add(new MavenRepositoryInfo("local1", myDataTestFixture.getTestDataPath("local1"), false));
    myRepositoryManager.add(new MavenRepositoryInfo("local2", myDataTestFixture.getTestDataPath("local2"), false));
    myRepositoryManager.startUpdateAll();
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Override
  protected void tearDown() throws Exception {
    myRepositoryManager.closeIndex();
    super.tearDown();
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
    
    assertComplectionVariants(myProjectPom, "junit", "jmock");
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

    assertComplectionVariants(myProjectPom, "junit");
  }

  public void testDownNotCompleteArtifactIdOnUnknownGroup() throws Exception {
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>unknown</groupId>" +
                     "    <artifactId><caret></artifactId>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertComplectionVariants(myProjectPom);
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

    assertComplectionVariants(myProjectPom, "3.8.1", "3.8.2", "4.0");
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

    assertComplectionVariants(myProjectPom);

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

    assertComplectionVariants(myProjectPom);
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

  private void checkHighlighting() throws Throwable {
    myCodeInsightFixture.testHighlighting(true, true, true, myProjectPom);
  }
}