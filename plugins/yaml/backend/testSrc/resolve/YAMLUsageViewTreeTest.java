// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.resolve;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTextUtil;

import java.util.*;
import java.util.stream.Collectors;

public class YAMLUsageViewTreeTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/resolve/data/";
  }

  public void testAnchorsAndAliases() {
    doTest();
  }

  private void doTest() {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
    String answerFilePath = getTestDataPath() + testName + ".usage.txt";
    String result = getSerializedResolveResults();
    assertSameLinesWithFile(answerFilePath, result);
  }

  @NotNull
  private String getSerializedResolveResults() {
    PsiFile file = myFixture.getFile();
    List<PsiReference> referenceList = getReferencesInFile(file);
    Collection<PsiElement> elements = referenceList.stream()
      .map(ref -> ref.resolve())
      .filter(e -> e != null)
      .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    Editor editor = myFixture.getEditor();
    StringBuilder sb = new StringBuilder();

    for (PsiElement element : elements) {
      String usage = myFixture.getUsageViewTreeTextRepresentation(element);

      sb
        .append("- element: '")
        .append(element.getText())
        .append("'\n")
        .append("  position: '")
        .append(editor.offsetToLogicalPosition(element.getTextOffset()))
        .append("'\n")
        .append("  view: |")
        .append('\n')
        .append(YAMLTextUtil.indentText(usage, 4))
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
      public void visitElement(@NotNull PsiElement element) {
        referencesList.addAll(Arrays.asList(element.getReferences()));
        element.acceptChildren(this);
      }
    });

    Collections.sort(referencesList,
                     Comparator.comparingInt(o -> o.getElement().getTextRange().getStartOffset()));

    return referencesList;
  }
}
