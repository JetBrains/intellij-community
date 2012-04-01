package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyDumbAwareAnnotator implements Annotator, DumbAware {
  public static ExtensionPointName<PyAnnotator> EP_NAME = ExtensionPointName.create("Pythonid.dumbAnnotator");
  private final PyAnnotator[] myAnnotators;

  public PyDumbAwareAnnotator() {
    myAnnotators = Extensions.getExtensions(EP_NAME);
  }

  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final PsiFile file = element.getContainingFile();

    for(PyAnnotator annotator: myAnnotators) {
      if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(element, holder);
    }
  }
}
