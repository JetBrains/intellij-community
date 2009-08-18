package com.jetbrains.python.codeInsight.override;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 14, 2009
 * Time: 5:44:51 PM
 */
public class PyMethodMember extends PsiElementMemberChooserObject implements ClassMember {
  public PyMethodMember(final PsiElement psiElement, final String text, @Nullable final Icon icon) {
    super(psiElement, text, icon);
  }

  public MemberChooserObject getParentNodeDelegate() {
    final PyElement element = (PyElement)getPsiElement();
    final PyClass parent = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    assert (parent != null);
    return new PyMethodMember(parent, parent.getName(), parent.getIcon(0));
  }
}
