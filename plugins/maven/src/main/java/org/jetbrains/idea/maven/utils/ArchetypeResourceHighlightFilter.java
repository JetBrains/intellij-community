package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class ArchetypeResourceHighlightFilter extends ProblemHighlightFilter {

  @Override
  public boolean shouldHighlight(@NotNull PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getOriginalFile().getVirtualFile();

    do {
      if (virtualFile == null) return true;

      if (virtualFile.getName().equals("archetype-resources")) {
        if (virtualFile.getPath().endsWith("src/main/resources/archetype-resources")) {
          return false;
        }
      }

      virtualFile = virtualFile.getParent();
    } while (true);
  }
}
