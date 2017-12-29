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
package com.jetbrains.python.psi.types;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyTypedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class TypeEvalContext {

  public static class Key {
    private static final Key INSTANCE = new Key();

    private Key() {
    }
  }

  @NotNull
  private final TypeEvalConstraints myConstraints;

  private List<String> myTrace;
  private String myTraceIndent = "";

  private final Map<PyTypedElement, PyType> myEvaluated = new HashMap<>();
  private final Map<PyCallable, PyType> myEvaluatedReturn = new HashMap<>();
  private final ThreadLocal<Set<PyTypedElement>> myEvaluating = new ThreadLocal<Set<PyTypedElement>>() {
    @Override
    protected Set<PyTypedElement> initialValue() {
      return new HashSet<>();
    }
  };
  private final ThreadLocal<Set<PyCallable>> myEvaluatingReturn = new ThreadLocal<Set<PyCallable>>() {
    @Override
    protected Set<PyCallable> initialValue() {
      return new HashSet<>();
    }
  };

  private TypeEvalContext(boolean allowDataFlow, boolean allowStubToAST, boolean allowCallContext, @Nullable PsiFile origin) {
    myConstraints = new TypeEvalConstraints(allowDataFlow, allowStubToAST, allowCallContext, origin);
  }

  @Override
  public String toString() {
    return String.format("TypeEvalContext(%b, %b, %s)", myConstraints.myAllowDataFlow, myConstraints.myAllowStubToAST,
                         myConstraints.myOrigin);
  }

  public boolean allowDataFlow(PsiElement element) {
    return myConstraints.myAllowDataFlow || element.getContainingFile() == myConstraints.myOrigin;
  }

  public boolean allowReturnTypes(PsiElement element) {
    return myConstraints.myAllowDataFlow || element.getContainingFile() == myConstraints.myOrigin;
  }

  public boolean allowCallContext(@NotNull PsiElement element) {
    return myConstraints.myAllowCallContext && element.getContainingFile() == myConstraints.myOrigin;
  }

  /**
   * Create a context for code completion.
   * <p/>
   * It is as detailed as {@link TypeEvalContext#userInitiated(Project, PsiFile)}, but allows inferring types based on the context in which
   * the analyzed code was called or may be called. Since this is basically guesswork, the results should be used only for code completion.
   */
  @NotNull
  public static TypeEvalContext codeCompletion(@NotNull final Project project, @Nullable final PsiFile origin) {
    return getContextFromCache(project, new TypeEvalContext(true, true, true, origin));
  }

  /**
   * Create the most detailed type evaluation context for user-initiated actions.
   * <p/>
   * Should be used go to definition, find usages, refactorings, documentation.
   * <p/>
   * For code completion see {@link TypeEvalContext#codeCompletion(Project, PsiFile)}.
   */
  public static TypeEvalContext userInitiated(@NotNull final Project project, @Nullable final PsiFile origin) {
    return getContextFromCache(project, new TypeEvalContext(true, true, false, origin));
  }

  /**
   * Create a type evaluation context for performing analysis operations on the specified file which is currently open in the editor,
   * without accessing stubs. For such a file, additional slow operations are allowed.
   * <p/>
   * Inspections should not create a new type evaluation context. They should re-use the context of the inspection session.
   */
  public static TypeEvalContext codeAnalysis(@NotNull final Project project, @Nullable final PsiFile origin) {
    return getContextFromCache(project, new TypeEvalContext(false, false, false, origin));
  }

  /**
   * Create the most shallow type evaluation context for code insight purposes when other more detailed contexts are not available.
   * It's use should be minimized.
   *
   * @param project pass project here to enable cache. Pass null if you do not have any project.
   *                <strong>Always</strong> do your best to pass project here: it increases performance!
   */
  public static TypeEvalContext codeInsightFallback(@Nullable final Project project) {
    final TypeEvalContext anchor = new TypeEvalContext(false, false, false, null);
    if (project != null) {
      return getContextFromCache(project, anchor);
    }
    return anchor;
  }

  /**
   * Create a type evaluation context for deeper and slower code insight.
   * <p/>
   * Should be used only when normal code insight context is not enough for getting good results.
   */
  public static TypeEvalContext deepCodeInsight(@NotNull final Project project) {
    return getContextFromCache(project, new TypeEvalContext(false, true, false, null));
  }

  /**
   * Moves context through cache returning one from cache (if exists).
   *
   * @param project current project
   * @param context context to fetch from cache
   * @return context to use
   * @see TypeEvalContextCache#getContext(TypeEvalContext)
   */
  @NotNull
  private static TypeEvalContext getContextFromCache(@NotNull final Project project, @NotNull final TypeEvalContext context) {
    return ServiceManager.getService(project, TypeEvalContextCache.class).getContext(context);
  }

  public TypeEvalContext withTracing() {
    if (myTrace == null) {
      myTrace = new ArrayList<>();
    }
    return this;
  }

  public void trace(String message, Object... args) {
    if (myTrace != null) {
      myTrace.add(myTraceIndent + String.format(message, args));
    }
  }

  public void traceIndent() {
    if (myTrace != null) {
      myTraceIndent += "  ";
    }
  }

  public void traceUnindent() {
    if (myTrace != null && myTraceIndent.length() >= 2) {
      myTraceIndent = myTraceIndent.substring(0, myTraceIndent.length() - 2);
    }
  }

  public String printTrace() {
    return StringUtil.join(myTrace, "\n");
  }

  public boolean tracing() {
    return myTrace != null;
  }

  @Nullable
  public PyType getType(@NotNull final PyTypedElement element) {
    final Set<PyTypedElement> evaluating = myEvaluating.get();
    if (evaluating.contains(element)) {
      return null;
    }
    evaluating.add(element);
    try {
      synchronized (myEvaluated) {
        if (myEvaluated.containsKey(element)) {
          final PyType type = myEvaluated.get(element);
          assertValid(type, element);
          return type;
        }
      }
      final PyType type = element.getType(this, Key.INSTANCE);
      assertValid(type, element);
      synchronized (myEvaluated) {
        myEvaluated.put(element, type);
      }
      return type;
    }
    finally {
      evaluating.remove(element);
    }
  }

  @Nullable
  public PyType getReturnType(@NotNull final PyCallable callable) {
    final Set<PyCallable> evaluating = myEvaluatingReturn.get();
    if (evaluating.contains(callable)) {
      return null;
    }
    evaluating.add(callable);
    try {
      synchronized (myEvaluatedReturn) {
        if (myEvaluatedReturn.containsKey(callable)) {
          final PyType type = myEvaluatedReturn.get(callable);
          assertValid(type, callable);
          return type;
        }
      }
      final PyType type = callable.getReturnType(this, Key.INSTANCE);
      assertValid(type, callable);
      synchronized (myEvaluatedReturn) {
        myEvaluatedReturn.put(callable, type);
      }
      return type;
    }
    finally {
      evaluating.remove(callable);
    }
  }

  private static void assertValid(@Nullable PyType result, @NotNull PyTypedElement element) {
    if (result != null) {
      result.assertValid(element.toString());
    }
  }

  public boolean maySwitchToAST(@NotNull PsiElement element) {
    return myConstraints.myAllowStubToAST || myConstraints.myOrigin == element.getContainingFile();
  }

  @Nullable
  public PsiFile getOrigin() {
    return myConstraints.myOrigin;
  }

  /**
   * @return context constraints (see {@link com.jetbrains.python.psi.types.TypeEvalConstraints}
   */
  @NotNull
  TypeEvalConstraints getConstraints() {
    return myConstraints;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TypeEvalContext context = (TypeEvalContext)o;

    return myConstraints.equals(context.myConstraints);
  }

  @Override
  public int hashCode() {
    return myConstraints.hashCode();
  }
}
