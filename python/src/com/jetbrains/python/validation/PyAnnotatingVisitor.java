package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class PyAnnotatingVisitor implements Annotator {
  private static final Logger LOGGER = Logger.getInstance(PyAnnotatingVisitor.class.getName());
  private final List<PyAnnotator> myAnnotators = new ArrayList<PyAnnotator>();

  public PyAnnotatingVisitor() {
    for (Class<? extends PyAnnotator> cls : ((PythonLanguage)PythonFileType.INSTANCE.getLanguage()).getAnnotators()) {
      PyAnnotator annotator;
      try {
        annotator = cls.newInstance();
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
    for(PyAnnotator annotator: myAnnotators) {
      annotator.annotateElement(psiElement, holder);
    }
  }
}
