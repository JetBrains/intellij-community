// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.resolve;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class YAMLResolveTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/resolve/data/";
  }

  public void testAnchorsAndAliases() {
    doTest();
  }

  private void doTest() {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
    String answerFilePath = getTestDataPath() + testName + ".resolve.txt";
    String result = getSerializedResolveResults();
    assertSameLinesWithFile(answerFilePath, result);
  }

  @NotNull
  private String getSerializedResolveResults() {
    PsiFile file = myFixture.getFile();
    List<PsiReference> referenceList = getReferencesInFile(file);
    Editor editor = myFixture.getEditor();
    StringBuilder sb = new StringBuilder();

    for (PsiReference reference : referenceList) {
      PsiElement targetElement = reference.resolve();
      PsiElement sourceElement = reference.getElement();
      TextRange referenceRange = reference.getRangeInElement();
      String sourceElementText = sourceElement.getText();
      int sourceElementOffset = sourceElement.getNode().getStartOffset();
      ASTNode targetElementNode = targetElement.getNode();

      sb
        .append("'")
        .append(referenceRange.subSequence(sourceElementText))
        .append("'")
        .append(" at ")
        .append(editor.offsetToLogicalPosition(referenceRange.getStartOffset() + sourceElementOffset))
        .append(" => ")
        .append('\n')
        .append("  ")
        .append(PsiUtilCore.getElementType(targetElementNode))
        .append(" at ")
        .append(editor.offsetToLogicalPosition(targetElementNode.getStartOffset()))
        .append('\n')
      ;
    }
    return sb.toString();
  }

  @NotNull
  private static List<PsiReference> getReferencesInFile(@NotNull PsiFile file) {
    final List<PsiReference> referencesList = new ArrayList<>();

    file.accept(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        referencesList.addAll(Arrays.asList(element.getReferences()));
        element.acceptChildren(this);
      }
    });

    Collections.sort(referencesList,
                     Comparator.comparingInt(o -> o.getElement().getTextRange().getStartOffset()));

    return referencesList;
  }
}
