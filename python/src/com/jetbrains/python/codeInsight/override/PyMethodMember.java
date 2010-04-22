package com.jetbrains.python.codeInsight.override;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 14, 2009
 * Time: 5:44:51 PM
 */
public class PyMethodMember extends PsiElementMemberChooserObject implements ClassMember {
  private static String buildNameFor(final PyElement element) {
    if (element instanceof PyFunction) {
      return element.getName() + ((PyFunction)element).getParameterList().getText();
    }
    return element.getName();
  }

  public PyMethodMember(final PyElement element) {
    super(element, buildNameFor(element), element.getIcon(0));
  }

  public MemberChooserObject getParentNodeDelegate() {
    final PyElement element = (PyElement)getPsiElement();
    final PyClass parent = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    assert (parent != null);
    return new PyMethodMember(parent);
  }
}
