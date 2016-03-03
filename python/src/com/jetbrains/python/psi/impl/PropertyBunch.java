/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
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
  @NotNull
  protected abstract Maybe<MType> translate(@Nullable PyExpression ref);

  @Nullable
  public static PyCallExpression findPropertyCallSite(@Nullable PyExpression source) {
    if (source instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)source;
      PyExpression callee = call.getCallee();
      if (callee instanceof PyReferenceExpression) {
        PyReferenceExpression ref = (PyReferenceExpression)callee;
        if (ref.isQualified()) return null;
        if (PyNames.PROPERTY.equals(callee.getName())) {
          PsiFile file = source.getContainingFile();
          if (isBuiltinFile(file) || !resolvesLocally(ref)) {
            // we assume that a non-local name 'property' is a built-in name.
            // ref.resolve() is not used because we run in stub building phase where resolve() is frowned upon.
            // NOTE: this logic fails if (quite unusually) name 'property' is directly imported from builtins.
            return call;
          }
        }
      }
    }
    return null;
  }

  private static boolean isBuiltinFile(PsiFile file) {
    final String name = file.getName();
    return PyBuiltinCache.BUILTIN_FILE.equals(name) || PyBuiltinCache.BUILTIN_FILE_3K.equals(name);
  }

  /**
   * Resolve in containing file only.
   * @param ref what to resolve
   * @return true iff ref obviously resolves to a local name (maybe partially, e.g. via import).
   */
  protected static boolean resolvesLocally(@NotNull PyReferenceExpression ref) {
    final String name = ref.getName();
    if (name != null) {
      final PyResolveProcessor processor = new PyResolveProcessor(name, true);
      PyResolveUtil.scopeCrawlUp(processor, ref, name, null);
      for (PsiElement element : processor.getElements()) {
        if (element != null) {
          return true;
        }
      }
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

          if (index >= 0) {
            arg = PyUtil.peelArgument(arg);
            if (index < 3) {
              accessors [index] = arg;
            }
            else if (index == 3 && arg instanceof PyStringLiteralExpression) {
              doc = ((PyStringLiteralExpression)arg).getStringValue();
            }
          }
        }
        target.myGetter = target.translate(accessors[0]);
        target.mySetter = target.translate(accessors[1]);
        target.myDeleter = target.translate(accessors[2]);
        target.myDoc = doc;
        return true;
      }
    }
    return false;
  }
}
