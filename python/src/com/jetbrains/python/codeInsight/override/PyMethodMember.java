package com.jetbrains.python.codeInsight.override;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 14, 2009
 * Time: 5:44:51 PM
 */
public class PyMethodMember extends PsiElementMemberChooserObject implements ClassMember {
  public PyMethodMember(final PyElement function) {
    super(function, function.getName(), function.getIcon(0));
  }

  public MemberChooserObject getParentNodeDelegate() {
    final PyElement element = (PyElement)getPsiElement();
    final PyClass parent = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    assert (parent != null);
    return new PyMethodMember(parent);
  }
}
