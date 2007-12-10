package com.intellij.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ClassHidesImportedClassUsageInfo extends ResolvableCollisionUsageInfo {
  private final PsiClass myHiddenClass;
  private PsiJavaCodeReferenceElement myCollisionReference;

  public ClassHidesImportedClassUsageInfo(PsiJavaCodeReferenceElement collisionReference, PsiClass renamedClass, PsiClass hiddenClass) {
    super(collisionReference, renamedClass);
    myHiddenClass = hiddenClass;
    myCollisionReference = collisionReference;
  }

  private boolean isResolvable() {
    return myHiddenClass.getQualifiedName() != null;
  }

  public void resolveCollision() throws IncorrectOperationException {
    final PsiManager manager = myCollisionReference.getManager();
    if(manager == null) return;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    if (!isResolvable()) return;
    final String qName = myHiddenClass.getQualifiedName();
    if (myCollisionReference instanceof PsiReferenceExpression) {
      myCollisionReference.replace(factory.createExpressionFromText(qName, myCollisionReference));
    } else {
      myCollisionReference.replace(factory.createFQClassNameReferenceElement(qName, myCollisionReference.getResolveScope()));
    }
  }
}
