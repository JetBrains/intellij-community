// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lomboktest;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@NotNullByDefault
public class LombokExtensionMethodsCandidateInfoTest extends AbstractLombokLightCodeInsightTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/candidateInfo/";
  }

  public void testExtensionMethodWrongArgumentCount() {
    PsiFile file = getConfigureByFile();

    Set<PsiMethod> actualTargetMethods = getCandidateTargetMethods(file);
    Set<PsiMethod> expectedTargetMethods = Set.copyOf(getStaticMethods(file));

    assertEquals(expectedTargetMethods, actualTargetMethods);
  }

  public void testExtensionMethodOverloadByReceiverType() {
    PsiFile file = getConfigureByFile();

    Set<PsiMethod> actualTargetMethods = getCandidateTargetMethods(file);
    Set<PsiMethod> expectedTargetMethod = Set.of(getStaticMethods(file).get(1));

    assertEquals(expectedTargetMethod, actualTargetMethods);
  }

  private Set<PsiMethod> getCandidateTargetMethods(@UnknownNullability PsiFile file) {
    PsiMethodCallExpression callExpression = getMethodCallAtCaret(file);
    CandidateInfo[] candidates = PsiResolveHelper.getInstance(getProject()).getReferencedMethodCandidates(callExpression, false);
    return Arrays.stream(candidates)
      .map(c -> assertInstanceOf(c.getElement(), PsiExtensionMethod.class))
      .map(extensionMethod -> extensionMethod.getTargetMethod())
      .collect(Collectors.toSet());
  }

  private @UnknownNullability PsiFile getConfigureByFile() {
    return myFixture.configureByFile(getTestName(false) + ".java");
  }

  private static List<PsiMethod> getStaticMethods(@UnknownNullability PsiFile file) {
    return PsiTreeUtil.findChildrenOfType(file, PsiMethod.class).stream()
      .filter(method -> method.hasModifierProperty(PsiModifier.STATIC))
      .toList();
  }

  private PsiMethodCallExpression getMethodCallAtCaret(PsiFile file) {
    return assertInstanceOf(
      PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class),
      PsiMethodCallExpression.class
    );
  }
}
