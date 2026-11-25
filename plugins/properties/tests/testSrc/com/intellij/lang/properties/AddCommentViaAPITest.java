package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class AddCommentViaAPITest {
  private static final String TEST_DATA_PATH = PropertiesQuickFixTestUtil.getPropertiesTestDataRoot() + "/properties/addcomment";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  private PsiParserFacade myParser;

  @BeforeEach
  void setUpParser() {
    myParser = PsiParserFacade.getInstance(quickFixFixture.get().getFixture().getProject());
  }

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = quickFixFixture.get();
    ActionHint actionHint = quickFix.configure(fileName);
    JavaCodeInsightTestFixture fixture = quickFix.getFixture();
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(fixture.getProject(), () -> {
      final int offset = fixture.getEditor().getCaretModel().getOffset();
      final PsiElement psiElement = fixture.getFile().findElementAt(offset);
      assert psiElement != null;
      final Property property = PsiTreeUtil.getParentOfType(psiElement, Property.class);

      final PsiComment comment = myParser.createLineCommentFromText(PropertiesFileType.INSTANCE, actionHint.getExpectedText());
      WriteAction.runAndWait(() -> property.getParent().addBefore(comment, property));
    }, "", ""));

    fixture.checkResultByFile("after" + fileName);
  }
}
