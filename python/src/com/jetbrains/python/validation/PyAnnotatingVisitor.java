package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyAnnotatingVisitor implements Annotator {
  private static final Logger LOGGER = Logger.getInstance(PyAnnotatingVisitor.class.getName());

  private final List<PyAnnotator> myAnnotators = new ArrayList<PyAnnotator>();

  private final Class[] ANNOTATOR_CLASSES = new Class[] {
    AssignTargetAnnotator.class,
    ParameterListAnnotator.class,
    HighlightingAnnotator.class,
    ReturnAnnotator.class,
    TryExceptAnnotator.class,
    BreakContinueAnnotator.class,
    GlobalAnnotator.class,
    ImportAnnotator.class,
    StringLiteralQuotesAnnotator.class,
    PyBuiltinAnnotator.class,
   UnsupportedFeatures.class
  };

  public PyAnnotatingVisitor() {
    for (Class cls : ANNOTATOR_CLASSES) {
      PyAnnotator annotator;
      try {
        annotator = (PyAnnotator)cls.newInstance();
      }
      catch (InstantiationException e) {
        LOGGER.error(e);
        continue;
      }
      catch (IllegalAccessException e) {
        LOGGER.error(e);
        continue;
      }
      myAnnotators.add(annotator);
    }
  }

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    final PsiFile file = psiElement.getContainingFile();
    for(PyAnnotator annotator: myAnnotators) {
      if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(psiElement, holder);
    }
  }
}
