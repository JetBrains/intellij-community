// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class TypeEvalContext {

  public static final class Key {
    private static final Key INSTANCE = new Key();

    private Key() {
    }
  }

  private final @NotNull TypeEvalConstraints myConstraints;

  private List<String> myTrace;
  private String myTraceIndent = "";

  private final ThreadLocal<ProcessingContext> myProcessingContext = ThreadLocal.withInitial(ProcessingContext::new);

  private final Map<PyTypedElement, PyType> myEvaluated = new HashMap<>();
  private final Map<PyCallable, PyType> myEvaluatedReturn = new HashMap<>();

  private TypeEvalContext(boolean allowDataFlow, boolean allowStubToAST, boolean allowCallContext, @Nullable PsiFile origin) {
    myConstraints = new TypeEvalConstraints(allowDataFlow, allowStubToAST, allowCallContext, origin);
  }

  @Override
  public String toString() {
    return String.format("TypeEvalContext(%b, %b, %s)", myConstraints.myAllowDataFlow, myConstraints.myAllowStubToAST,
                         myConstraints.myOrigin);
  }

  public boolean allowDataFlow(PsiElement element) {
    return myConstraints.myAllowDataFlow || inOrigin(element);
  }

  public boolean allowReturnTypes(PsiElement element) {
    return myConstraints.myAllowDataFlow || inOrigin(element);
  }

  public boolean allowCallContext(@NotNull PsiElement element) {
    return myConstraints.myAllowCallContext && inOrigin(element);
  }

  /**
   * Create a context for code completion.
   * <p/>
   * It is as detailed as {@link TypeEvalContext#userInitiated(Project, PsiFile)}, but allows inferring types based on the context in which
   * the analyzed code was called or may be called. Since this is basically guesswork, the results should be used only for code completion.
   */
  public static @NotNull TypeEvalContext codeCompletion(final @NotNull Project project, final @Nullable PsiFile origin) {
    return getContextFromCache(project, new TypeEvalContext(true, true, true, origin));
  }

  /**
   * Create the most detailed type evaluation context for user-initiated actions.
   * <p/>
   * Should be used go to definition, find usages, refactorings, documentation.
   * <p/>
   * For code completion see {@link TypeEvalContext#codeCompletion(Project, PsiFile)}.
   */
  public static TypeEvalContext userInitiated(final @NotNull Project project, final @Nullable PsiFile origin) {
    return getContextFromCache(project, new TypeEvalContext(true, true, false, origin));
  }

  /**
   * Create a type evaluation context for performing analysis operations on the specified file which is currently open in the editor,
   * without accessing stubs. For such a file, additional slow operations are allowed.
   * <p/>
   * Inspections should not create a new type evaluation context. They should re-use the context of the inspection session.
   */
  public static TypeEvalContext codeAnalysis(final @NotNull Project project, final @Nullable PsiFile origin) {
    return getContextFromCache(project, new TypeEvalContext(false, false, false, origin));
  }

  /**
   * Create the most shallow type evaluation context for code insight purposes when other more detailed contexts are not available.
   * It's use should be minimized.
   *
   * @param project pass project here to enable cache. Pass null if you do not have any project.
   *                <strong>Always</strong> do your best to pass project here: it increases performance!
   */
  public static TypeEvalContext codeInsightFallback(final @Nullable Project project) {
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
  public static TypeEvalContext deepCodeInsight(final @NotNull Project project) {
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
  private static @NotNull TypeEvalContext getContextFromCache(final @NotNull Project project, final @NotNull TypeEvalContext context) {
    return project.getService(TypeEvalContextCache.class).getContext(context);
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

  public @Nullable PyType getType(final @NotNull PyTypedElement element) {
    return RecursionManager.doPreventingRecursion(
      Pair.create(element, this),
      false,
      () -> {
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
    );
  }

  public @Nullable PyType getReturnType(final @NotNull PyCallable callable) {
    return RecursionManager.doPreventingRecursion(
      Pair.create(callable, this),
      false,
      () -> {
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
    );
  }

  /**
   * Normally, each {@link PyTypeProvider} is supposed to perform all the necessary analysis independently
   * and hence should completely isolate its state. However, on rare occasions when several type providers have to
   * recursively call each other, it might be necessary to preserve some state for subsequent calls to the same provider with
   * the same instance of {@link TypeEvalContext}. Each {@link TypeEvalContext} instance contains an associated thread-local
   * {@link ProcessingContext} that can be used for such caching. Should be used with discretion.
   *
   * @return a thread-local instance of {@link ProcessingContext} bound to this {@link TypeEvalContext} instance
   */
  @ApiStatus.Experimental
  public @NotNull ProcessingContext getProcessingContext() {
    return myProcessingContext.get();
  }

  private static void assertValid(@Nullable PyType result, @NotNull PyTypedElement element) {
    if (result != null) {
      result.assertValid(element.toString());
    }
  }

  public boolean maySwitchToAST(@NotNull PsiElement element) {
    return myConstraints.myAllowStubToAST || inOrigin(element);
  }

  public @Nullable PsiFile getOrigin() {
    return myConstraints.myOrigin;
  }

  /**
   * @return context constraints (see {@link TypeEvalConstraints}
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

  private boolean inOrigin(@NotNull PsiElement element) {
    return myConstraints.myOrigin == element.getContainingFile() || myConstraints.myOrigin == getContextFile(element);
  }

  private static PsiFile getContextFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    PsiElement context = file.getContext();
    if (context == null) {
      return file;
    }
    else {
      return getContextFile(context);
    }
  }
}
