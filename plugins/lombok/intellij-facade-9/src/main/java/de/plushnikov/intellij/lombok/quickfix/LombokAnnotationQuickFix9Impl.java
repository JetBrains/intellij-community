package de.plushnikov.intellij.lombok.quickfix;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Plushnikov Michail
 */
public class LombokAnnotationQuickFix9Impl extends AddAnnotationFix implements LombokQuickFix {
  private static final Logger LOG = Logger.getInstance("#" + LombokAnnotationQuickFix9Impl.class.getName());

  private final String               myAnnotation;
  private final PsiModifierListOwner myModifierListOwner;
  private final PsiNameValuePair[]   myPairs;

  public LombokAnnotationQuickFix9Impl(@NotNull String annotationFQN, @NotNull PsiModifierListOwner modifierListOwner, @NotNull PsiNameValuePair[] values, @NotNull String... annotationsToRemove) {
    super(annotationFQN, modifierListOwner, annotationsToRemove);
    myAnnotation = annotationFQN;
    myModifierListOwner = modifierListOwner;
    myPairs = values;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiModifierList modifierList = myModifierListOwner.getModifierList();
    LOG.assertTrue(modifierList != null);
    if (modifierList.findAnnotation(myAnnotation) != null) {
      return;
    }

    final PsiFile containingFile = myModifierListOwner.getContainingFile();
    if (!CodeInsightUtilBase.preparePsiElementForWrite(containingFile)) {
      return;
    }
    for (String fqn : getAnnotationsToRemove()) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(myModifierListOwner, fqn);
      if (annotation != null) {
        annotation.delete();
      }
    }

    PsiAnnotation inserted = modifierList.addAnnotation(myAnnotation);
    for (PsiNameValuePair pair : myPairs) {
      inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
    }
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
    if (containingFile != file) {
      UndoUtil.markPsiFileForUndo(file);
    }
  }
}
