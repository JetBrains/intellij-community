package com.jetbrains.python.codeInsight.override;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;

import javax.swing.*;

/**
 * @author Alexey.Ivanov
 */
public class PyMethodMember extends PsiElementMemberChooserObject implements ClassMember {
  private final String myFullName;
  private static String buildNameFor(final PyElement element) {
    if (element instanceof PyFunction) {
      return element.getName() + ((PyFunction)element).getParameterList().getText();
    }
    if (element instanceof PyClass && PyNames.FAKE_OLD_BASE.equals(element.getName())) {
      return "<old-style class>";
    }
    return element.getName();
  }

  public PyMethodMember(final PyElement element) {
    super(element, trimUnderscores(buildNameFor(element)), element.getIcon(0));
    myFullName = buildNameFor(element);
  }

  private static String trimUnderscores(String s) {
    return StringUtil.trimStart(StringUtil.trimStart(s, "_"), "_");
  }

  public MemberChooserObject getParentNodeDelegate() {
    final PyElement element = (PyElement)getPsiElement();
    final PyClass parent = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    assert (parent != null);
    return new PyMethodMember(parent);
  }

  @Override
  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    component.append(myFullName, getTextAttributes(tree));
    component.setIcon(getPsiElement().getIcon(0));
  }
}
