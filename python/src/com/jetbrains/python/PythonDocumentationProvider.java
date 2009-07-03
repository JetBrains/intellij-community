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
    final StringBuffer cat = new StringBuffer("<html><body><code>");
    if (element instanceof PyDocStringOwner) {
      String docString = null;
      boolean prepended_something = false;
      PyStringLiteralExpression doc_expr = ((PyDocStringOwner) element).getDocStringExpression();
      if (doc_expr != null) docString = doc_expr.getStringValue();
      if (element instanceof PyClass) {
        PyClass cls = (PyClass)element;
        describeClass(cls, cat);
        prepended_something = true;
      }
      else if (element instanceof PyFunction) {
        PyFunction fun = (PyFunction)element;
        PyClass cls = fun.getContainingClass();
        if (cls != null) {
          cat.append("<small>class ").append(cls.getName()).append("</small>").append(BR);
        }
        describeFunction(fun, cat);
        prepended_something = true;
        boolean not_found = true;
        if (docString == null) {
          String meth_name = fun.getName();
          if (cls != null && meth_name != null ) {
            // look for inherited
            for (PyClass ancestor : cls.iterateAncestors()) {
              PyFunction inherited = ancestor.findMethodByName(meth_name);
              if (inherited != null) {
                PyStringLiteralExpression doc_elt = inherited.getDocStringExpression();
                if (doc_elt != null) {
                  String inherited_doc = doc_elt.getStringValue();
                  if (inherited_doc.length() > 1) {
                    cat
                      .append(BR).append(BR).append("</code>")
                      .append(PyBundle.message("QDOC.copied.from.$0.$1", ancestor.getName(), meth_name))
                      .append(BR).append(BR)
                      .append(inherited_doc)
                      .append("<code>")
                    ;
                    not_found = false;
                    break;
                  }
                }
              }
            }

            if (not_found) {
              // above could have not worked because inheritance is not searched down to 'object'.
              // for well-known methods, copy built-in doc string.
              // TODO: also handle predefined __xxx__ that are not part of 'object'.
              if (PyNames.UnderscoredNames.contains(meth_name)) {
                PyClassType objtype = PyBuiltinCache.getInstance(fun.getProject()).getObjectType(); // old- and new-style classes share the __xxx__ stuff
                if (objtype != null) {
                  PyClass objcls = objtype.getPyClass();
                  if (objcls != null) {
                    PyFunction obj_underscored = objcls.findMethodByName(meth_name);
                    if (obj_underscored != null) {
                      PyStringLiteralExpression predefined_doc_expr = obj_underscored.getDocStringExpression();
                      String predefined_doc = predefined_doc_expr != null? predefined_doc_expr.getStringValue() : null;
                      if (predefined_doc != null && predefined_doc.length() > 1) { // only a real-looking doc string counts
                        cat
                          .append(BR).append(BR).append("</code>")
                          .append(predefined_doc)
                          .append(BR)
                          .append(PyBundle.message("QDOC.copied.from.builtin"))
                          .append("<code>")
                          ;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      else if (element instanceof PyFile) {
        // what to prepend to a module description??
      }
      else { // not a func, not a class
        cat.append(combUp(PyUtil.getReadableRepr(element, false)));
        prepended_something = true;
      }
      cat.append("</code>");
      if (docString != null) {
        if (prepended_something) cat.append(BR).append(BR);
        cat.append(combUp(docString.trim()));
      }
      else if (! prepended_something) return null; // no doc, no prepend -> not found
      return cat.append("</body></html>").toString();
    }
    return null;
  }
}
