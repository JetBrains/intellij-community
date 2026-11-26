package com.intellij.java.lomboktest;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.DefaultAnnotationParamInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.bugs.ObjectEqualityInspection;
import com.siyeh.ig.bugs.ObjectToStringInspection;
import com.siyeh.ig.style.FieldMayBeFinalInspection;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokDaemonAnalyzerFixture;

@TestApplication
public class LombokHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lombokDaemonAnalyzerFixture(projectFixture, "/highlighting/", () -> List.of(
    new ObjectToStringInspection(),
    new ObjectEqualityInspection(),
    new DataFlowInspection(),
    new DefUseInspection(),
    new FieldMayBeFinalInspection(),
    new FieldCanBeLocalInspection(),
    new DefaultAnnotationParamInspection()
  ));

  @Test
  public void testLombokBasics() { doTest(); }

  @Test
  public void testLombokStaticVars() {
    doTest();
  }

  @Test
  public void testLombokEqualsAndHashCode() {
    doTest();
  }

  @Test
  public void testGetterLazyVariableNotInitialized() {
    doTest();
  }

  @Test
  public void testGetterLazyInvocationProduceNPE() {
    doTest();
  }

  @Test
  public void testFieldNameConstantsExample() {
    doTest();
  }

  @Test
  public void testBuilderWithDefaultRedundantInitializer() {
    doTest();
  }

  @Test
  public void testBuilderWithDefaultReinitializeInConstructor() {
    doTest();
  }

  @Test
  public void testValueSealedInterface() {
    doTest();
  }

  @Test
  public void testBuilderJacksonized() {
    // shouldn't throw any exceptions (like StackOverflowError)
    doTest();
  }

  @Test
  public void testBuilderWithPredefinedBuilderClassMethods() {
    doTest();
  }

  @Test
  public void testOnXExample() {
    doTest();
  }

  @Test
  public void testLombokNullable() {
    doTest();
  }

  @Test
  public void testStaticConstructorGenericsInference() {
    // Enable UncheckedWarningLocalInspection to detect regression that manifest itself with following warning:
    // Unchecked assignment: 'WithLombok' to 'WithLombok<java.lang.String>'
    daemonFixture.get().getFixture().enableInspections(new UncheckedWarningLocalInspection());
    doTest();
  }

  @Test
  public void testLombokJavadocDeclaration() {
    daemonFixture.get().getFixture().enableInspections(new JavadocDeclarationInspection());
    doTest();
  }

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection(true));
    setWarningLevel(new ObjectEqualityInspection());
  }

  private void setWarningLevel(LocalInspectionTool inspection) {
    final HighlightDisplayKey displayKey = HighlightDisplayKey.find(inspection.getShortName());
    Project project = daemonFixture.get().getFixture().getProject();
    final InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
    currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project);
  }

  private void doTest() {
    LightDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(fixture.getFixture().getTestName(false) + ".java", true, false);
  }
}
