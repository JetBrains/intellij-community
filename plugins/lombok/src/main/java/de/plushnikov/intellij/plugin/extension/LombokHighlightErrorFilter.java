package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  public LombokHighlightErrorFilter() {
  }

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null == file) {
      return true;
    }

    Project project = file.getProject();
    if (!LombokLibraryUtil.hasLombokLibrary(project)) {
      return true;
    }

    PsiElement highlightedElement = file.findElementAt(highlightInfo.getStartOffset());
    if (null == highlightedElement) {
      return true;
    }

    // handle rest cases
    if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
      //Handling onX parameters
      if (OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)) {
        return false;
      }
    }

    return true;
  }
}
