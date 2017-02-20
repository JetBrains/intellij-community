package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
final class YAMLPsiManager extends PsiTreeChangePreprocessorBase {
  public YAMLPsiManager(@NotNull PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof YAMLFile;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    while (true) {
      if (element instanceof YAMLFile) {
        return true;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        return false;
      }
      PsiElement parent = element.getParent();
      if (!(parent instanceof YAMLFile ||
            parent instanceof YAMLKeyValue ||
            parent instanceof YAMLCompoundValue ||
            parent instanceof YAMLDocument)) {
        return false;
      }
      element = parent;
    }
  }

}
