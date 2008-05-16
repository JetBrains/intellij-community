package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 *  Resolves conflicts with fields in a class, when new local variable is
 *  introduced in code block
 *  @author dsl
 */
public class FieldConflictsResolver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.FieldConflictsResolver");
  private final String myName;
  private final PsiCodeBlock myScope;
  private PsiField myField;
  private List<PsiReferenceExpression> myReferenceExpressions;
  private PsiClass myQualifyingClass;

  public FieldConflictsResolver(String name, PsiCodeBlock scope) {
    myName = name;
    myScope = scope;
    if (myScope == null) return;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myScope.getProject());
    final PsiVariable oldVariable = facade.getResolveHelper().resolveReferencedVariable(myName, myScope);
    if (!(oldVariable instanceof PsiField)) return;
    myField = (PsiField) oldVariable;
    myReferenceExpressions = new ArrayList<PsiReferenceExpression>();
    for (PsiReference reference : ReferencesSearch.search(myField, new LocalSearchScope(myScope), false)) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        if (referenceExpression.getQualifierExpression() == null) {
          myReferenceExpressions.add(referenceExpression);
        }
      }
    }
    if (myField.hasModifierProperty(PsiModifier.STATIC)) {
      myQualifyingClass = myField.getContainingClass();
    }
  }

  public PsiExpression fixInitializer(PsiExpression initializer) {
    if (myField == null) return initializer;
    final PsiReferenceExpression[] replacedRef = new PsiReferenceExpression[] { null };
    initializer.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression != null) {
          qualifierExpression.accept(this);
        } else {
          final PsiElement result = expression.resolve();
          if (expression.getManager().areElementsEquivalent(result, myField)) {
            try {
              replacedRef[0] = qualifyReference(expression, myField, myQualifyingClass);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
    if (!initializer.isValid()) return replacedRef[0];
    return initializer;
  }

  public void fix() throws IncorrectOperationException {
    if (myField == null) return;
    final PsiManager manager = myScope.getManager();
    for (PsiReferenceExpression referenceExpression : myReferenceExpressions) {
      if (!referenceExpression.isValid()) continue;
      final PsiElement newlyResolved = referenceExpression.resolve();
      if (!manager.areElementsEquivalent(newlyResolved, myField)) {
        qualifyReference(referenceExpression, myField, myQualifyingClass);
      }
    }
  }


  public static PsiReferenceExpression qualifyReference(PsiReferenceExpression referenceExpression, final PsiMember member, final PsiClass qualifyingClass) throws IncorrectOperationException {
    PsiManager manager = referenceExpression.getManager();
    PsiReferenceExpression expressionFromText;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (qualifyingClass == null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
      final PsiClass containingClass = member.getContainingClass();
      if (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
        expressionFromText = (PsiReferenceExpression)factory.createExpressionFromText("A.this." + member.getName(), null);
        final PsiJavaCodeReferenceElement classQualifier = ((PsiThisExpression)expressionFromText.getQualifierExpression()).getQualifier();
        classQualifier.replace(factory.createClassReferenceElement(containingClass));
      } else {
        expressionFromText = (PsiReferenceExpression) factory.createExpressionFromText("this." + member.getName(), null);
      }
    } else {
      expressionFromText = (PsiReferenceExpression) factory.createExpressionFromText("A." + member.getName(), null);
      final PsiReferenceExpression qualifier = factory.createReferenceExpression(qualifyingClass);
      expressionFromText.getQualifierExpression().replace(qualifier);
    }
    CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
    expressionFromText = (PsiReferenceExpression) codeStyleManager.reformat(expressionFromText);
    return (PsiReferenceExpression) referenceExpression.replace(expressionFromText);
  }
}
