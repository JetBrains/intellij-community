package org.jetbrains.yaml.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
final class YAMLPsiManager extends PsiTreeChangePreprocessorBase {
  public YAMLPsiManager(@NotNull Project project) {
    super(project);
  }

  @Override
  protected boolean isInsideCodeBlock(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return false;
    }

    if (element == null || element.getParent() == null) {
      return true;
    }

    while (true) {
      if (element instanceof YAMLFile) {
        return false;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        return true;
      }
      PsiElement parent = element.getParent();
      if (!(parent instanceof YAMLFile ||
            parent instanceof YAMLKeyValue ||
            parent instanceof YAMLCompoundValue ||
            parent instanceof YAMLDocument)) {
        return true;
      }
      element = parent;
    }
  }

  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (!(event.getFile() instanceof YAMLFile)) return;
    super.treeChanged(event);
  }
}
