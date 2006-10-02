package com.intellij.refactoring.rename.inplace;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;

import java.util.Map;

import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class ResolveSnapshot {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.ResolveSnapshot");

  private Map<SmartPsiElementPointer, SmartPsiElementPointer> myReferencesMap = new HashMap<SmartPsiElementPointer, SmartPsiElementPointer>();
  private Project myProject;
  private Document myDocument;

  public static ResolveSnapshot createSnapshot(PsiElement scope) {
    return new ResolveSnapshot(scope);
  }

  private ResolveSnapshot(final PsiElement scope) {
    myProject = scope.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(scope.getContainingFile());
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(myProject);
    final Map<PsiElement, SmartPsiElementPointer> pointers = new HashMap<PsiElement, SmartPsiElementPointer>();
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression refExpr) {
        if (!refExpr.isQualified()) {
          PsiElement resolved = refExpr.resolve();
          if (resolved instanceof PsiField) {
            SmartPsiElementPointer key = pointerManager.createSmartPsiElementPointer(refExpr);
            SmartPsiElementPointer value = pointers.get(resolved);
            if (value == null) {
              value = pointerManager.createSmartPsiElementPointer(resolved);
              pointers.put(resolved, value);
            }
            myReferencesMap.put(key, value);
          }
        }
        super.visitReferenceExpression(refExpr);
      }
    });
  }

  public void apply(String hidingLocalName) {
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    for (Map.Entry<SmartPsiElementPointer,SmartPsiElementPointer> entry : myReferencesMap.entrySet()) {
      qualify(entry.getKey().getElement(), entry.getValue().getElement(), hidingLocalName);
    }
  }

  private static void qualify(PsiElement referent, PsiElement referee, String hidingLocalName) {
    if (referent instanceof PsiReferenceExpression && referee instanceof PsiField) {
      PsiReferenceExpression ref = ((PsiReferenceExpression) referent);
      if (!ref.isQualified() && hidingLocalName.equals(ref.getReferenceName())) {
        PsiClass refereeClass = ((PsiField) referee).getContainingClass();
        PsiClass referentClass = PsiTreeUtil.getParentOfType(referent, PsiClass.class);
        if (refereeClass != null && referentClass != null &&
            PsiTreeUtil.isAncestor(refereeClass, referentClass, false)) {
          if (refereeClass == referentClass ||
              refereeClass.getName() != null) {  //otherwise cannot qualify anonymous referee class
            @NonNls String qualifer = refereeClass == referentClass ? "this" :
                refereeClass.getName() + ".this";
            String qualifiedRefText = qualifer + "." + ref.getText();
            PsiElementFactory elementFactory = PsiManager.getInstance(referentClass.getProject()).getElementFactory();
            try {
              PsiReferenceExpression qualifiedRef = (PsiReferenceExpression) elementFactory.createExpressionFromText(qualifiedRefText, null);
              ref.replace(qualifiedRef);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
  }
}
