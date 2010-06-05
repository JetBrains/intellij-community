package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
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
      if (callee instanceof PyReferenceExpression && "property".equals(callee.getName())) {
        PsiElement resolved = ((PyReferenceExpression)callee).getReference().resolve();
        if (resolved != null && PyBuiltinCache.getInstance(source).hasInBuiltins(resolved)) {
          return call;
        }
      }
    }
    return null;
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
        PyArgumentList.AnalysisResult analysis = arglist.analyzeCall();
        PyCallExpression.PyMarkedCallee marked_callee = analysis.getMarkedCallee();
        if (marked_callee != null) {
          PyParameter[] params = marked_callee.getCallable().getParameterList().getParameters();
          List<Maybe<MType>> accessors = new ArrayList<Maybe<MType>>(3);
          final Maybe<MType> unknown = new Maybe<MType>();
          accessors.add(null); accessors.add(null); accessors.add(null); // 3 times
          final int offset = marked_callee.getImplicitOffset();
          for (Map.Entry<PyExpression, PyNamedParameter> entry: analysis.getPlainMappedParams().entrySet()) {
            PyNamedParameter param = entry.getValue();
            int n = ArrayUtil.indexOf(params, param) - offset;
            if (n >= 0) {
              if (n < 3) {
                // accessors
                accessors.set(n, unknown); // definitely filled
                PyExpression expr = entry.getKey();
                if (expr instanceof PyReferenceExpression) {
                  PyReferenceExpression arg_ref = (PyReferenceExpression)expr;
                  if (arg_ref.getQualifier() == null) accessors.set(n, new Maybe<MType>(target.translate(arg_ref)));
                }
              }
              else if (n == 3) {
                // doc
                PyExpression expr = entry.getKey();
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
          for (int i=0; i < 3; i+=1) if (accessors.get(i) == null) accessors.set(i, new Maybe<MType>(null));
          target.myGetter = accessors.get(0);
          target.mySetter = accessors.get(1);
          target.myDeleter = accessors.get(2);
        }
        return true;
      }
    }
    return false;
  }

}
