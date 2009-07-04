package com.jetbrains.python;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides qiuck docs for classes, methods, and functions.
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
    Lambda2<String, StringBuilder, StringBuilder> deco_name_wrapper,
    String deco_separator,
    Lambda2<String, StringBuilder, StringBuilder> func_name_wrapper,
    Lambda1<String, String> escaper
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
    Lambda2<String, StringBuilder, StringBuilder> name_wrapper
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
  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    final StringBuilder cat = new StringBuilder("<html><body><code>");
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
        if (cls != null) {
          cat.append("<small>class ").append(cls.getName()).append("</small>").append(BR);
        }
        describeFunction(fun, cat, LWrapInItalic, BR, LWrapInBold, LCombUp);
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

  // // this is a shaky mirage of another world. instead, gotta learn how to write in Jython here.

  //
  private static StringBuilder describeDeco(
    PyDecorator deco,
    final StringBuilder cat,
    final Lambda2<String, StringBuilder, StringBuilder> name_wrapper, //  wrap in tags, if need be
    final Lambda1<String, String> arg_wrapper   // add escaping, if need be
  ) {
    cat.append("@");
    name_wrapper.apply(PyUtil.getReadableRepr(deco.getCallee(), true), cat);
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
    return cat;
  }

  private static Lambda1<String, String> LCombUp = new Lambda1<String, String>() {
    public String apply(String argname) {
      return combUp(argname);
    }
  };

  private static Lambda1<String, String> LSame1 = new Lambda1<String, String>() {
    public String apply(String name) {
      return name;
    }
  };

  private static class WrapInTagLambda implements Lambda2<String, StringBuilder, StringBuilder> {
    private String start_tag, end_tag;

    WrapInTagLambda(String tag) {
      end_tag = "</" + tag + ">";
      start_tag = "<" + tag + ">";
    }

    public StringBuilder apply(String deconame, @NonNls StringBuilder cat) {
        return cat.append(start_tag).append(deconame).append(end_tag);
    }
  }

  private static Lambda2<String, StringBuilder, StringBuilder> LWrapInBold = new WrapInTagLambda("b");
  private static Lambda2<String, StringBuilder, StringBuilder> LWrapInItalic = new WrapInTagLambda("i");

  private static Lambda2<String, StringBuilder, StringBuilder> LSame2 = new Lambda2<String, StringBuilder, StringBuilder>() {
    public StringBuilder apply(String name, StringBuilder cat) {
      return cat.append(name);
    }
  };

  public static Lambda1<PyExpression, String> LReadableRepr = new Lambda1<PyExpression, String>() {
    public String apply(PyExpression arg) {
      return PyUtil.getReadableRepr(arg, true);
    }
  };

  // // from here on, a cry of desperation.
  private static interface Lambda1<A, R> {
    R apply(A arg);
  }

  private static interface Lambda2<A1, A2, R> {
    R apply(A1 arg1, A2 arg2);
  }

  private static class FP {
    @NotNull
    public static <S, R> List<R> map(@NotNull Lambda1<S, R> lambda, @NotNull List<S> source) {
      List<R> ret = new ArrayList<R>(source.size());
      for (S item : source) ret.add(lambda.apply(item));
      return ret;
    }

    public static <R> R fold(@NotNull Lambda2<R, R, R> lambda, @NotNull List<R> source, @NotNull final R unit) {
      R ret = unit;
      for (R item : source) lambda.apply(ret, item);
      return ret;
    }

    public static <R> R foldr(@NotNull Lambda2<R, R, R> lambda, @NotNull List<R> source, @NotNull final R unit) {
      R ret = unit;
      for (R item : source) lambda.apply(item, ret);
      return ret;
    }

    public static <R1, R2> List<Pair<R1, R2>> zip(List<R1> one, List<R2> two) {
      if (one.size() != two.size()) throw new IllegalArgumentException("Size of one is " + one.size() + ", of two " + two.size());
      List<Pair<R1, R2>> ret = new ArrayList<Pair<R1, R2>>(one.size());
      for (int i = 0; i < one.size(); i += 1) { // a more kosher way would be using iterators, but i don't bother now
        ret.add(new Pair<R1, R2>(one.get(i), two.get(i)));
      }
      return ret;
    }

    public static <A1, R1, R2> Lambda1<A1, R2> combine(final Lambda1<A1, R1> f, final Lambda1<R1, R2> g) {
      return new Lambda1<A1, R2>() {
        public R2 apply(A1 arg) {
          return g.apply(f.apply(arg));
        }
      };
    }

    // TODO: add slices, array wrapping %)
  }

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
