package com.jetbrains.python;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public class PythonDocumentationProvider extends QuickDocumentationProvider {
  public String getQuickNavigateInfo(final PsiElement element) {
    return null;
  }

  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    if (element instanceof PyDocStringOwner) {
      String docString = ((PyDocStringOwner) element).getDocString();
      if (docString == null) {
        if (element instanceof PyClass) {
          PyClass cls = (PyClass)element;
          StringBuffer cat = new StringBuffer("class ");
          cat.append(cls.getName());
          final PyExpression[] ancestors = cls.getSuperClassExpressions();
          if (ancestors.length > 0) {
            cat.append("(");
            boolean is_not_first = false;
            for (PyExpression one : ancestors) {
              if (is_not_first) cat.append(", ");
              else is_not_first = true; 
              cat.append(PyUtil.getReadableRepr(one, false));
            }
            cat.append(")");
          }
          // TODO: show decorators, too.
          docString = cat.toString();
        }
        else if (element instanceof PyFunction) {
          PyFunction fun = (PyFunction)element;
          StringBuffer cat = new StringBuffer("def ");
          cat.append(fun.getName());
          cat.append(PyUtil.getReadableRepr(fun.getParameterList(), false));
          docString = cat.toString();
        }
        else docString = PyUtil.getReadableRepr(element, false);
      }
      return XmlStringUtil.escapeString(docString).replace("\n", "<br>").replace(" ", "&nbsp;");
    }
    return null;
  }
}
