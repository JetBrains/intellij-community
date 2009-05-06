package com.jetbrains.python;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;

/**
 * Provides qiuck docs for classes, methods, and functions.
 */
public class PythonDocumentationProvider extends QuickDocumentationProvider {
  public String getQuickNavigateInfo(final PsiElement element) {
    return null;
  }

  private final static String BR = "<br>";

  private static String combUp(String what) {
    return XmlStringUtil.escapeString(what).replace("\n", BR).replace(" ", "&nbsp;");
  }

  /**
   * Creates a HTML description of function definition.
   * @param fun the function
   * @param cat string buffer to append to
   * @return cat for easy chaining
   */
  private static StringBuffer describeFunction(PyFunction fun, StringBuffer cat) {
    PyDecoratorList deco_list = fun.getDecoratorList();
    if (deco_list != null) {
      for (PyDecorator deco : deco_list.getDecorators()) {
        cat.append("@").append("<i>").append(PyUtil.getReadableRepr(deco.getCallee(), true)).append("</i>");
        PyArgumentList arglist = deco.getArgumentList();
        boolean is_next = false;
        if (arglist != null) {
          cat.append("(");
          for (PyExpression arg : arglist.getArguments()) {
            if (is_next) cat.append(", ");
            else is_next = true;
            cat.append(combUp(PyUtil.getReadableRepr(arg, true)));
          }
          cat.append(")");
        }
        cat.append(BR);
      }
    }
    cat.append("def ").append("<b>").append(fun.getName()).append("</b>");
    cat.append(combUp(PyUtil.getReadableRepr(fun.getParameterList(), false)));
    return cat;
  }

  /**
   * Creates a HTML description of function definition.
   * @param cls the class
   * @param cat string buffer to append to
   * @return cat for easy chaining
   */
  private static StringBuffer describeClass(PyClass cls, StringBuffer cat) {
    cat.append("class ");
    cat.append("<b>").append(cls.getName()).append("</b>");
    final PyExpression[] ancestors = cls.getSuperClassExpressions();
    if (ancestors.length > 0) {
      cat.append("(");
      boolean is_next = false;
      for (PyExpression one : ancestors) {
        if (is_next) cat.append(", ");
        else is_next = true;
        cat.append(combUp(PyUtil.getReadableRepr(one, false)));
      }
      cat.append(")");
    }
    // TODO: for py3k, show decorators
    return cat;
  }


  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    StringBuffer cat = new StringBuffer("<html><body><code>");
    if (element instanceof PyDocStringOwner) {
      String docString = ((PyDocStringOwner) element).getDocString();
      if (element instanceof PyClass) {
        PyClass cls = (PyClass)element;
        describeClass(cls, cat);
      }
      else if (element instanceof PyFunction) {
        PyFunction fun = (PyFunction)element;
        PyClass cls = fun.getContainingClass();
        if (cls != null) {
          cat.append("<small>class ").append(cls.getName()).append("</small>").append(BR);
        }
        describeFunction(fun, cat);
        if (docString == null) {
          // for well-known methods, copy built-in doc string
          // TODO: also handle predefined __xxx__ that are not part of 'object'.
          String meth_name = fun.getName();
          if (cls != null && meth_name != null && PyNames.UnderscoredNames.contains(meth_name)) {
            PyClassType objtype = PyBuiltinCache.getInstance(fun.getProject()).getObjectType(); // old- and new-style classes share the __xxx__ stuff
            if (objtype != null) {
              PyClass objcls = objtype.getPyClass();
              if (objcls != null) {
                PyFunction obj_underscored = objcls.findMethodByName(meth_name);
                if (obj_underscored != null) {
                  String predefined_doc = obj_underscored.getDocString();
                  if (predefined_doc != null && predefined_doc.length() > 1) { // only a real-looking doc string counts
                    cat
                      .append(BR).append(BR)
                      .append(predefined_doc)
                      .append(BR)
                      .append("</code><small>(copied from built-in description)</small><code>")
                    ;
                  }
                }
              }
            }
          }
        }
      }
      else { // not a func, not a class
        cat.append(combUp(PyUtil.getReadableRepr(element, false)));
      }
      cat.append("</code>");
      if (docString != null) {
        cat.append(BR).append(BR).append(combUp(docString));
      }
      return cat.append("</body></html>").toString();
    }
    return null;
  }
}
