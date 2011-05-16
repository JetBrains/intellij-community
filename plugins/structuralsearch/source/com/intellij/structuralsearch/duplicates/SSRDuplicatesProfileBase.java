package com.intellij.structuralsearch.duplicates;

import com.intellij.dupLocator.DupInfo;
import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateVisitor;
import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.resultUI.*;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.dupLocator.util.DuplocatorSettingsEditor;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class SSRDuplicatesProfileBase extends DuplicatesProfile {
  @NotNull
  @Override
  public DuplocateVisitor createVisitor(@NotNull DuplocatorHashCallback collector) {
    return new SSRNodeSpecificHasher(DuplocatorSettings.getInstance(), collector, this);
  }

  public abstract int getNodeCost(@NotNull PsiElement element);

  public TokenSet getLiterals() {
    return TokenSet.EMPTY;
  }

  /*private static int getDefaultNodeCost(PsiElement element) {
    if (!(element instanceof LeafElement)) {
      return 0;
    }

    if (StructuralSearchProfileBase.containsOnlyDelimeters(element.getText())) {
      return 0;
    }

    return 1;
  }*/

  @Override
  public CodeFragmentType getType(@NotNull CodeNode node) throws InvalidatedException {
    return new FileBasedCodeFragmentType(node.getVirtualFile());
  }

  @Override
  public DuplocatorSettingsEditor createEditor() {
    return null;
  }

  @NotNull
  @Override
  public DuplicatesView createView(@NotNull Project project) {
    return new BaseDuplicatesView(project);
  }

  @Override
  public boolean isMyDuplicate(@NotNull DupInfo info, int index) {
    PsiFragment[] fragments = info.getFragmentOccurences(index);
    if (fragments.length > 0) {
      PsiElement[] elements = fragments[0].getElements();
      if (elements.length > 0) {
        Language language = elements[0].getLanguage();
        return isMyLanguage(language);
      }
    }
    return false;
  }

  @Nullable
  public PsiElementRole getRole(@NotNull PsiElement element) {
    return null;
  }

  @Nullable
  public PsiElement[] getCodeBlock(@NotNull PsiElement element) {
    return null;
  }
}
