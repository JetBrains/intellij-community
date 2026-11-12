// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lomboktest;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@NotNullByDefault
@TestApplication
public class LombokExtensionMethodsCandidateInfoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/candidateInfo/");

  @Test
  public void testExtensionMethodWrongArgumentCount() {
    PsiFile file = getConfigureByFile();

    ReadAction.runBlocking(() -> {
      Set<PsiMethod> actualTargetMethods = getCandidateTargetMethods(file);
      Set<PsiMethod> expectedTargetMethods = Set.copyOf(getStaticMethods(file));

      assertEquals(expectedTargetMethods, actualTargetMethods);
    });
  }

  @Test
  public void testExtensionMethodOverloadByReceiverType() {
    PsiFile file = getConfigureByFile();

    ReadAction.runBlocking(() -> {
      Set<PsiMethod> actualTargetMethods = getCandidateTargetMethods(file);
      Set<PsiMethod> expectedTargetMethod = Set.of(getStaticMethods(file).get(1));

      assertEquals(expectedTargetMethod, actualTargetMethods);
    });
  }

  private Set<PsiMethod> getCandidateTargetMethods(@UnknownNullability PsiFile file) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiMethodCallExpression callExpression = getMethodCallAtCaret(file);
    CandidateInfo[] candidates = PsiResolveHelper.getInstance(myFixture.getProject()).getReferencedMethodCandidates(callExpression, false);
    return Arrays.stream(candidates)
      .map(c -> assertInstanceOf(PsiExtensionMethod.class, c.getElement()))
      .map(extensionMethod -> extensionMethod.getTargetMethod())
      .collect(Collectors.toSet());
  }

  private @UnknownNullability PsiFile getConfigureByFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return myFixture.configureByFile(myFixture.getTestName(false) + ".java");
  }

  private static List<PsiMethod> getStaticMethods(@UnknownNullability PsiFile file) {
    return PsiTreeUtil.findChildrenOfType(file, PsiMethod.class).stream()
      .filter(method -> method.hasModifierProperty(PsiModifier.STATIC))
      .toList();
  }

  private PsiMethodCallExpression getMethodCallAtCaret(PsiFile file) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return assertInstanceOf(PsiMethodCallExpression.class, PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class));
  }
}
