package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Something that describes a property, with all related accessors.
 * <br/>
 * User: dcheryasov
 * Date: Jun 3, 2010 2:07:48 PM
 */
public abstract class PropertyBunch<MType> {

  protected Maybe<MType> myGetter;
  protected Maybe<MType> mySetter;
  protected Maybe<MType> myDeleter;
  protected String myDoc;
  protected PyTargetExpression mySite;

  @NotNull
  public Maybe<MType> getGetter() {
    return myGetter;
  }

  @NotNull
  public Maybe<MType> getSetter() {
    return mySetter;
  }

  @NotNull
  public Maybe<MType> getDeleter() {
    return myDeleter;
  }

  @Nullable
  public String getDoc() {
    return myDoc;
  }


  /**
   * @param ref a reference as an argument in property() call
   * @return value we want to store (resolved callable, name, etc)
   */
  protected abstract @Nullable MType translate(@NotNull PyReferenceExpression ref);


  @Nullable
  protected static PyCallExpression findPropertyCallSite(PyExpression source) {
    if (source instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)source;
      PyExpression callee = call.getCallee();
      if (callee instanceof PyReferenceExpression) {
        PyReferenceExpression ref = (PyReferenceExpression)callee;
        if (ref.getQualifier() != null) return null;
        if ("property".equals(callee.getName()) && !resolvesLocally(ref)) {
          // we assume that a non-local name 'property' is a built-in name.
          // ref.resolve() is not used because we run in stub building phase where resolve() is frowned upon.
          // NOTE: this logic fails if (quite unusually) name 'property' is directly imported from builtins.
          return call;
        }
      }
    }
    return null;
  }

  /**
   * Resolve in containing file only.
   * @param ref what to resolve
   * @return true iff ref obviously resolves to a local name (maybe partially, e.g. via import).
   */
  protected static boolean resolvesLocally(@NotNull PyReferenceExpression ref) {
    final String name = ref.getName();
    if (name != null) {
      PsiElement outermost_context = ref;
      PsiElement seeker = ref;
      do {
        seeker = PsiTreeUtil.getParentOfType(seeker, PyFunction.class);
        if (seeker != null) outermost_context = seeker;
      } while (seeker != null);
      final ResolveProcessor processor = new ResolveProcessor(name);
      PyResolveUtil.treeCrawlUp(processor, true, outermost_context);
      return (processor.getResult() != null || processor.getDefiners().size() > 0);
    }
    return false;
  }

  /**
   * Tries to form a bunch from data available at a possible property() call site.
   * @param source should be a PyCallExpression (if not, null is immediately returned).
   * @param target what to fill with data (return type contravariance prevents us from creating it inside).
   * @return true if target was successfully filled. 
   */
  protected static <MType> boolean fillFromCall(PyExpression source, PropertyBunch<MType> target) {
    PyCallExpression call = findPropertyCallSite(source);
    if (call != null) {
      PyArgumentList arglist = call.getArgumentList();
      if (arglist != null) {
        PyArgumentList.AnalysisResult analysis = PyCallExpressionHelper.analyzeBuiltinCall(call);
        if (analysis != null) {
          PyCallExpression.PyMarkedCallee marked_callee = analysis.getMarkedCallee();
          if (marked_callee != null) {
            PyParameter[] params = marked_callee.getCallable().getParameterList().getParameters();
            List<Maybe<MType>> accessors = new ArrayList<Maybe<MType>>(3);
            final Maybe<MType> unknown = new Maybe<MType>();
            accessors.add(null);
            accessors.add(null);
            accessors.add(null); // 3 times
            final int offset = marked_callee.getImplicitOffset();
            // NOTE: we could find mapped parameters by name, but this won't be any shorter :(
            for (Map.Entry<PyExpression, PyNamedParameter> entry : analysis.getPlainMappedParams().entrySet()) {
              PyNamedParameter param = entry.getValue();
              int n = ArrayUtil.indexOf(params, param) - offset;
              if (n >= 0) {
                if (n < 3) {
                  // accessors
                  accessors.set(n, unknown); // definitely filled
                  PyExpression expr = PyUtil.peelArgument(entry.getKey());
                  if (expr instanceof PyReferenceExpression) {
                    PyReferenceExpression arg_ref = (PyReferenceExpression)expr;
                    if (arg_ref.getQualifier() == null) accessors.set(n, new Maybe<MType>(target.translate(arg_ref)));
                  }
                }
                else if (n == 3) {
                  // doc
                  PyExpression expr = PyUtil.peelArgument(entry.getKey());
                  if (expr instanceof PyStringLiteralExpression) {
                    target.myDoc = ((PyStringLiteralExpression)expr).getStringValue();
                  }
                }
              }
            }
            for (PyNamedParameter param : analysis.getKwdMappedParams()) {
              // can't extract values, but values are present
              int n = ArrayUtil.indexOf(params, param) - offset;
              if (n >= 0 && n < 3) accessors.set(n, unknown);
            }
            for (PyParameter param : analysis.getTupleMappedParams()) {
              // can't extract values, but values are present
              int n = ArrayUtil.indexOf(params, param) - offset;
              if (n >= 0 && n < 3) accessors.set(n, unknown);
            }
            // something could have been not set; this means None was implicitly passed
            for (int i = 0; i < 3; i += 1) if (accessors.get(i) == null) accessors.set(i, new Maybe<MType>(null));
            target.myGetter = accessors.get(0);
            target.mySetter = accessors.get(1);
            target.myDeleter = accessors.get(2);
            return true;
          }
        }
      }    }
    return false;
  }

}
