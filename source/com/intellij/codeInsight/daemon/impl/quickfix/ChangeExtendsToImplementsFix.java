/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;

import java.text.MessageFormat;

/**
 * changes 'class a extends b' to 'class a implements b' or vice versa
 */
public class ChangeExtendsToImplementsFix extends ExtendsListFix {
  public ChangeExtendsToImplementsFix(PsiClass aClass, PsiClassType classToExtendFrom) {
    super(aClass, classToExtendFrom, true);
  }

  public String getText() {
    final String text = MessageFormat.format("Change ''{0} {2}'' to ''{1} {2}''",
        new Object[]{
          (myClass.isInterface() == myClassToExtendFrom.isInterface() ? "implements" : "extends"),
          (myClass.isInterface() == myClassToExtendFrom.isInterface() ? "extends" : "implements"),
          myClassToExtendFrom.getQualifiedName(),
        });
    return text;
  }
}