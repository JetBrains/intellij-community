package com.jetbrains.python;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.toolbox.FP;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Provides quick docs for classes, methods, and functions.
 */
public class PythonDocumentationProvider extends QuickDocumentationProvider {

  // provides ctrl+hover info
  public String getQuickNavigateInfo(final PsiElement element) {
    if (element instanceof PyFunction) {
      PyFunction func = (PyFunction)element;
      StringBuilder cat = new StringBuilder();
      PyClass cls = func.getContainingClass();
      if (cls != null) {
        String cls_name = cls.getName();
        cat.append("class ").append(cls_name).append("\n ");
        // It would be nice to have class import info here, but we don't know the ctrl+hovered reference and context
      }
      return describeFunction(func, cat, LSame2, ", ", LSame2, LSame1).toString();
    }
    else if (element instanceof PyClass) {
      PyClass cls = (PyClass)element;
      return describeClass(cls, new StringBuilder(), LSame2).toString();
    }
    return null;
  }

  private final static @NonNls String BR = "<br>";

  private static @NonNls String combUp(@NonNls String what) {
    return XmlStringUtil.escapeString(what).replace("\n", BR).replace(" ", "&nbsp;");
  }

  /**
   * Creates a HTML description of function definition.
   * @param fun the function
   * @param cat string buffer to append to
   * @param deco_name_wrapper puts a tag around decorator name
   * @param deco_separator is added between decorators
   * @param func_name_wrapper puts a tag around the function name
   * @param escaper sanitizes values that come directly from doc string or code
   * @return cat for easy chaining
   */
  private static StringBuilder describeFunction(
    PyFunction fun,
    StringBuilder cat,
    FP.Lambda2<String, StringBuilder, StringBuilder> deco_name_wrapper,
    String deco_separator,
    FP.Lambda2<String, StringBuilder, StringBuilder> func_name_wrapper,
    FP.Lambda1<String, String> escaper
  ) {
    PyDecoratorList deco_list = fun.getDecoratorList();
    if (deco_list != null) {
      for (PyDecorator deco : deco_list.getDecorators()) {
        describeDeco(deco, cat, deco_name_wrapper, escaper).append(deco_separator);
      }
    }
    cat.append("def "); //.append("<b>").append(fun.getName()).append("</b>");
    func_name_wrapper.apply(fun.getName(), cat);
    cat.append(escaper.apply(PyUtil.getReadableRepr(fun.getParameterList(), false)));
    return cat;
  }

  /**
   * Creates a HTML description of function definition.
   * @param cls the class
   * @param cat string buffer to append to
   * @return cat for easy chaining
   */
  private static StringBuilder describeClass(
    PyClass cls,
    StringBuilder cat,
    FP.Lambda2<String, StringBuilder, StringBuilder> name_wrapper
  ) {
    cat.append("class ");
    name_wrapper.apply(cls.getName(), cat);
    final PyExpression[] ancestors = cls.getSuperClassExpressions();
    if (ancestors.length > 0) {
      cat.append("(");
      join(", ", FP.map(LReadableRepr, Arrays.asList(ancestors)), cat);
      cat.append(")");
    }
    // TODO: for py3k, show decorators
    return cat;
  }


  // provides ctrl+Q doc
  public String generateDoc(PsiElement element, final PsiElement originalElement) {
    final StringBuilder cat = new StringBuilder("<html><body>");
    // here the ^Q target is already resolved; the resolved element may point to intermediate assignments
    boolean reassignment_marked = false;
    if (element instanceof PyTargetExpression) {
      if (! reassignment_marked) {
        LWrapInSmall.enclose(cat, "Assigned to ", element.getText(), BR);
        reassignment_marked = true;
      }
      element = PyUtil.findAssignedValue((PyTargetExpression)element);
    }
    if (element instanceof PyReferenceExpression) {
      if (! reassignment_marked) {
        LWrapInSmall.enclose(cat, "Assigned to ", element.getText(), BR);
        reassignment_marked = true;
      }
      element = PyUtil.followAssignmentsChain((PyReferenceExpression)element);
    }
    // it may be a call to decorator
    if (element instanceof PyCallExpression) {
      Pair<String, PyFunction> wrap_info = PyCallExpressionHelper.interpretAsStaticmethodOrClassmethodWrappingCall(
        (PyCallExpression)element, originalElement
      );
      if (wrap_info != null) {
        String wrapper_name = wrap_info.getFirst();
        PyFunction wrapped_func = wrap_info.getSecond();
        LWrapInSmall.enclose(cat, "Wrapped in <code>", wrapper_name, "</code>", BR); // NOTE: abstraction fail :(
        element = wrapped_func;
      }

    }
    // now element may contain a doc string
    cat.append("<code>");
    if (element instanceof PyDocStringOwner) {
      String docString = null;
      boolean prepended_something = false;
      PyStringLiteralExpression doc_expr = ((PyDocStringOwner) element).getDocStringExpression();
      if (doc_expr != null) docString = doc_expr.getStringValue();
      if (element instanceof PyClass) {
        PyClass cls = (PyClass)element;
        describeClass(cls, cat, LWrapInBold);
        prepended_something = true;
      }
      else if (element instanceof PyFunction) {
        PyFunction fun = (PyFunction)element;
        PyClass cls = fun.getContainingClass();
        if (cls != null) LWrapInSmall.enclose(cat, "class ", cls.getName(), BR);
        describeFunction(fun, cat, LWrapInItalic, BR, LWrapInBold, LCombUp);
        prepended_something = true;
        boolean not_found = true;
        if (docString == null) {
          String meth_name = fun.getName();
          if (cls != null && meth_name != null ) {
            // look for inherited
            for (PyClass ancestor : cls.iterateAncestors()) {
              PyFunction inherited = ancestor.findMethodByName(meth_name, false);
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
                PyClassType objtype = PyBuiltinCache.getInstance(fun).getObjectType(); // old- and new-style classes share the __xxx__ stuff
                if (objtype != null) {
                  PyClass objcls = objtype.getPyClass();
                  if (objcls != null) {
                    PyFunction obj_underscored = objcls.findMethodByName(meth_name, false);
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

  // // this is a shaky mirage of another world. instead, gotta learn how to write in Jython here.

  //
  private static StringBuilder describeDeco(
    PyDecorator deco,
    final StringBuilder cat,
    final FP.Lambda2<String, StringBuilder, StringBuilder> name_wrapper, //  wrap in tags, if need be
    final FP.Lambda1<String, String> arg_wrapper   // add escaping, if need be
  ) {
    cat.append("@");
    name_wrapper.apply(PyUtil.getReadableRepr(deco.getCallee(), true), cat);
    if (deco.hasArgumentList()) {
      PyArgumentList arglist = deco.getArgumentList();
      if (arglist != null) {
        List<String> argnames = FP.map(
          FP.combine(LReadableRepr, arg_wrapper),
          Arrays.asList(arglist.getArguments())
        );

        cat.append("(");
        join(", ", argnames, cat);
        cat.append(")");
      }
    }
    return cat;
  }

  private static FP.Lambda1<String, String> LCombUp = new FP.Lambda1<String, String>() {
    public String apply(String argname) {
      return combUp(argname);
    }
  };

  private static FP.Lambda1<String, String> LSame1 = new FP.Lambda1<String, String>() {
    public String apply(String name) {
      return name;
    }
  };

  private static class TagWrapper implements FP.Lambda2<String, StringBuilder, StringBuilder> {
    private String start_tag, end_tag;

    TagWrapper(String tag) {
      end_tag = "</" + tag + ">";
      start_tag = "<" + tag + ">";
    }

    public StringBuilder apply(String content, @NonNls StringBuilder cat) {
        return cat.append(start_tag).append(content).append(end_tag);
    }

    public StringBuilder enclose(@NonNls StringBuilder cat, String... contents) {
      cat.append(start_tag);
      for (String item : contents) cat.append(item);
      cat.append(end_tag);
      return cat;
    }
  }

  private static TagWrapper LWrapInBold = new TagWrapper("b");
  private static TagWrapper LWrapInItalic = new TagWrapper("i");
  private static TagWrapper LWrapInSmall = new TagWrapper("small");

  private static FP.Lambda2<String, StringBuilder, StringBuilder> LSame2 = new FP.Lambda2<String, StringBuilder, StringBuilder>() {
    public StringBuilder apply(String name, StringBuilder cat) {
      return cat.append(name);
    }
  };

  public static FP.Lambda1<PyExpression, String> LReadableRepr = new FP.Lambda1<PyExpression, String>() {
    public String apply(PyExpression arg) {
      return PyUtil.getReadableRepr(arg, true);
    }
  };

  private static StringBuilder join(String delimiter, List list, StringBuilder cat) {
    boolean is_next = false;
    for (Object item : list) {
      if (is_next) cat.append(delimiter);
      else is_next = true;
      cat.append(item.toString());
    }
    return cat;
  }

  private static String join(String delimiter, List list) {
    return join(delimiter, list, new StringBuilder()).toString();
  }
}
