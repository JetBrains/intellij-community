package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public static PyCallExpression findPropertyCallSite(@Nullable PyExpression source) {
    if (source instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)source;
      PyExpression callee = call.getCallee();
      if (callee instanceof PyReferenceExpression) {
        PyReferenceExpression ref = (PyReferenceExpression)callee;
        if (ref.getQualifier() != null) return null;
        boolean is_inside_builtins = false;
        PsiFile psifile = source.getContainingFile();
        is_inside_builtins = psifile != null && psifile.getUserData(PyBuiltinCache.MARKER_KEY) != null;
        if (PyNames.PROPERTY.equals(callee.getName()) && (is_inside_builtins || !resolvesLocally(ref))) {
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
        PyExpression[] accessors = new PyExpression[3];
        String doc = null;
        int position = 0;
        String[] keywords = new String[] { "fget", "fset", "fdel", "doc" };
        for (PyExpression arg: arglist.getArguments()) {
          int index = -1;
          if (arg instanceof PyKeywordArgument) {
            String keyword = ((PyKeywordArgument)arg).getKeyword();
            index = ArrayUtil.indexOf(keywords, keyword);
            if (index < 0) {
              continue;
            }
            position = -1;
          }
          else if (position >= 0) {
            index = position;
            position++;
          }

          arg = PyUtil.peelArgument(arg);
          if (index < 3) {
            accessors [index] = arg;
          }
          else if (index == 3 && arg instanceof PyStringLiteralExpression) {
            doc = ((PyStringLiteralExpression)arg).getStringValue();
          }
        }
        target.myGetter = translateIfSet(target, accessors [0]);
        target.mySetter = translateIfSet(target, accessors [1]);
        target.myDeleter = translateIfSet(target, accessors [2]);
        target.myDoc = doc;
        return true;
      }
    }
    return false;
  }

  private static <MType> Maybe<MType> translateIfSet(PropertyBunch<MType> target, PyExpression accessor) {
    // TODO[yole] I don't quite understand this subtle distinction (why an accessor defined with lambda must be treated as defined=false)
    if (accessor != null && !(accessor instanceof PyReferenceExpression)) {
      return new Maybe<MType>();
    }
    return new Maybe<MType>(accessor == null ? null : target.translate((PyReferenceExpression) accessor));
  }
}
