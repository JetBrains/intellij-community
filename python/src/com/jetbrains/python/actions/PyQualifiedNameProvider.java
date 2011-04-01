package com.jetbrains.python.actions;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;


/**
 * User: anna
 * Date: 3/29/11
 */
public class PyQualifiedNameProvider implements QualifiedNameProvider {
  @Override
  public PsiElement adjustElementToCopy(PsiElement element) {
    return element instanceof PyClass || element instanceof PyFunction ? element : null;
  }

  @Override
  public String getQualifiedName(PsiElement element) {
    if (element instanceof PyClass) {
      return ((PyClass)element).getQualifiedName();
    }
    if (element instanceof PyFunction) {
      final PyClass containingClass = ((PyFunction)element).getContainingClass();
      if (containingClass != null) {
        return containingClass.getQualifiedName() + "#" + ((PyFunction)element).getName();
      }
    }
    return null;
  }

  @Override
  public PsiElement qualifiedNameToElement(String fqn, Project project) {
    PyClass aClass = PyClassNameIndex.findClass(fqn, project);
    if (aClass != null) {
      return aClass;
    }
    final int sharpIdx = fqn.indexOf("#");
    if (sharpIdx > -1) {
      final String className = StringUtil.getPackageName(fqn, '#');
      aClass = PyClassNameIndex.findClass(className, project);
      if (aClass != null) {
        final String memberName = StringUtil.getShortName(fqn, '#');
        final PyClass nestedClass = aClass.findNestedClass(memberName, false);
        if (nestedClass != null) return nestedClass;
        final PyFunction methodByName = aClass.findMethodByName(memberName, false);
        if (methodByName != null) return methodByName;
      }
    }
    return null;
  }

  @Override
  public void insertQualifiedName(String fqn, PsiElement element, Editor editor, Project project) {
    EditorModificationUtil.insertStringAtCaret(editor, fqn);
  }
}
