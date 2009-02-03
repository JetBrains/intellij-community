/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;

public class ReplaceWithSubtypeUsageInfo extends FixableUsageInfo {
  public static final Logger LOG = Logger.getInstance("#" + ReplaceWithSubtypeUsageInfo.class.getName());
  private final PsiTypeElement myTypeElement;
  private final PsiClassType myTargetClassType;
  private final PsiType myOriginalType;
  private String myConflict;

  public ReplaceWithSubtypeUsageInfo(PsiTypeElement typeElement, PsiClassType classType, final PsiClass[] targetClasses) {
    super(typeElement);
    myTypeElement = typeElement;
    myTargetClassType = classType;
    myOriginalType = myTypeElement.getType();
    if (targetClasses.length > 1) {
      myConflict = typeElement.getText() + " can be replaced with any of " + StringUtil.join(targetClasses, new Function<PsiClass, String>() {
        public String fun(final PsiClass psiClass) {
          return psiClass.getQualifiedName();
        }
      }, ", ") ;
    }
  }

  public void fixUsage() throws IncorrectOperationException {
    if (myTypeElement.isValid()) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myTypeElement.getProject()).getElementFactory();
      myTypeElement.replace(elementFactory.createTypeElement(myTargetClassType));
    }
  }

  @Override
  public String getConflictMessage() {
    if (!TypeConversionUtil.isAssignable(myOriginalType, myTargetClassType)) {
      final String conflict = "No consistent substitution found for " +
                              getElement().getText() +
                              ". Expected \'" +
                              myOriginalType.getPresentableText() +
                              "\' but found \'" +
                              myTargetClassType.getPresentableText() +
                              "\'.";
      if (myConflict == null) {
        myConflict = conflict;
      } else {
        myConflict += "\n" + conflict;
      }
    }
    return myConflict;
  }
}
