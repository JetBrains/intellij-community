// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public sealed class TypeEvalContext {

  /**
   * This class ensures that only {@link TypeEvalContext} instances can directly invoke
   * {@link PyTypedElement#getType(TypeEvalContext, Key)} and everybody else has to
   * access its result though {@link #getType(PyTypedElement)} or {@link #getReturnType(PyCallable)}.
   * Hence, the inferred type information cannot bypass caching in {@link TypeEvalContext}.
   */
  public static final class Key {
    private static final Key INSTANCE = new Key();

    private Key() {
    }
  }

  private final @NotNull TypeEvalConstraints myConstraints;

  private List<String> myTrace;
  private String myTraceIndent = "";

  private final ThreadLocal<ProcessingContext> myProcessingContext = ThreadLocal.withInitial(ProcessingContext::new);

  protected final Map<PyTypedElement, PyType> myEvaluated = createMap();
  protected final Map<PyCallable, PyType> myEvaluatedReturn = createMap();
  protected final Map<Pair<PyExpression, Object>, PyType> contextTypeCache = createMap();
  /**
   * AssumptionContext invariant requires that if type is in the map, 
   * it's dependencies are also in the map, so we can't use softValueMap.
   * Temporary solution until we know assumeType works as expected.
   * @see TypeEvalContext#assumeType(PyTypedElement, PyType, Function) 
   */
  private static <T> Map<T, PyType> createMap() {
    if (Registry.is("python.use.better.control.flow.type.inference")) {
      return new ConcurrentHashMap<>();
    }
    return CollectionFactory.createConcurrentSoftValueMap();
  }

  private TypeEvalContext(boolean allowDataFlow, boolean allowStubToAST, boolean allowCallContext, @Nullable PsiFile origin) {
    myConstraints = new TypeEvalConstraints(allowDataFlow, allowStubToAST, allowCallContext, origin);
  }

  private TypeEvalContext(@NotNull TypeEvalConstraints constraints) {
    myConstraints = constraints;
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
    return getContextFromCache(project, buildCodeAnalysisContext(origin));
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

  private static TypeEvalContext buildCodeAnalysisContext(@Nullable PsiFile origin) {
    if (Registry.is("python.optimized.type.eval.context")) {
      return new OptimizedTypeEvalContext(false, false, false, origin);
    }
    return new TypeEvalContext(false, false, false, origin);
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

  @ApiStatus.Internal
  public <R> @Nullable R assumeType(@NotNull PyTypedElement element, @Nullable PyType type, @NotNull Function<TypeEvalContext, R> func) {
    if (getKnownType(element) != null) {
      // Temporary solution, as overwriting known type might introduce inconsistencies with its dependencies.
      return null;
    }
    AssumptionContext context = new AssumptionContext(this, element, type);
    R result = null;
    try {
      result = func.apply(context);
    }
    finally {
      element.getManager().dropResolveCaches();
    }
    return result;
  }

  @ApiStatus.Internal
  public boolean hasAssumptions() {
    return this instanceof AssumptionContext;
  }

  protected @Nullable PyType getKnownType(final @NotNull PyTypedElement element) {
    if (element instanceof PyInstantTypeProvider) {
      return element.getType(this, Key.INSTANCE);
    }
    final PyType cachedType = myEvaluated.get(element);
    if (cachedType != null) {
      assertValid(cachedType, element);
      return cachedType;
    }
    return null;
  }

  protected @Nullable PyType getKnownReturnType(final @NotNull PyCallable callable) {
    final PyType cachedType = myEvaluatedReturn.get(callable);
    if (cachedType != null) {
      assertValid(cachedType, callable);
      return cachedType;
    }
    return null;
  }

  private static boolean isLibraryElement(@NotNull PsiElement element) {
    VirtualFile vFile = element.getContainingFile().getOriginalFile().getVirtualFile();
    return vFile != null && ("pyi".equals(vFile.getExtension()) || ProjectFileIndex.getInstance(element.getProject()).isInLibrary(vFile));
  }

  private @NotNull TypeEvalContext getLibraryContext(@NotNull Project project) {
    TypeEvalConstraints constraints = new TypeEvalConstraints(myConstraints.myAllowDataFlow,
                                                              myConstraints.myAllowStubToAST,
                                                              myConstraints.myAllowCallContext,
                                                              // code completion will always have a new PsiFile, use original file instead
                                                              myConstraints.myOrigin != null ? myConstraints.myOrigin.getOriginalFile() : null);
    return project.getService(TypeEvalContextCache.class).getLibraryContext(new LibraryTypeEvalContext(constraints));
  }

  /**
   * If true the element's type will be calculated and stored in the long-life context bounded to the PyLibraryModificationTracker.
   */
  protected boolean canDelegateToLibraryContext(PyTypedElement element) {
    return Registry.is("python.use.separated.libraries.type.cache") && isLibraryElement(element);
  }

  public @Nullable PyType getType(final @NotNull PyTypedElement element) {
    if (canDelegateToLibraryContext(element)) {
      var context = getLibraryContext(element.getProject());
      return context.getType(element);
    }

    final PyType knownType = getKnownType(element);
    if (knownType != null) {
      return knownType == PyNullType.INSTANCE ? null : knownType;
    }
    return RecursionManager.doPreventingRecursion(
      Pair.create(element, this),
      false,
      () -> {
        PyType type = element.getType(this, Key.INSTANCE);
        assertValid(type, element);
        myEvaluated.put(element, type == null ? PyNullType.INSTANCE : type);
        return type;
      }
    );
  }

  public @Nullable PyType getReturnType(final @NotNull PyCallable callable) {
    if (canDelegateToLibraryContext(callable)) {
      var context = getLibraryContext(callable.getProject());
      return context.getReturnType(callable);
    }

    final PyType knownReturnType = getKnownReturnType(callable);
    if (knownReturnType != null) {
      return knownReturnType == PyNullType.INSTANCE ? null : knownReturnType;
    }
    return RecursionManager.doPreventingRecursion(
      Pair.create(callable, this),
      false,
      () -> {
        final PyType type = callable.getReturnType(this, Key.INSTANCE);
        assertValid(type, callable);
        myEvaluatedReturn.put(callable, type == null ? PyNullType.INSTANCE : type);
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

  @ApiStatus.Internal
  public @NotNull Map<Pair<PyExpression, Object>, PyType> getContextTypeCache() {
    return contextTypeCache;
  }

  /**
   * @return context constraints (see {@link TypeEvalConstraints}
   */
  @ApiStatus.Internal
  @NotNull
  public TypeEvalConstraints getConstraints() {
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

  private static class PyNullType implements PyType {
    private PyNullType() {}

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                                      @Nullable PyExpression location,
                                                                      @NotNull AccessDirection direction,
                                                                      @NotNull PyResolveContext resolveContext) {
      return List.of();
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public @Nullable String getName() {
      return "null";
    }

    @Override
    public boolean isBuiltin() {
      return false;
    }

    @Override
    public void assertValid(String message) {
    }

    private static final PyNullType INSTANCE = new PyNullType();
  }

  private static final class AssumptionContext extends TypeEvalContext {
    @NotNull final TypeEvalContext myParent;

    private AssumptionContext(@NotNull TypeEvalContext parent, @NotNull PyTypedElement element, @Nullable PyType type) {
      super(parent.myConstraints);
      myParent = parent;
      myEvaluated.put(element, type == null ? PyNullType.INSTANCE : type);
    }

    @Override
    protected @Nullable PyType getKnownType(@NotNull PyTypedElement element) {
      final PyType knownType = super.getKnownType(element);
      if (knownType != null) {
        return knownType;
      }
      return myParent.getKnownType(element);
    }

    @Override
    protected @Nullable PyType getKnownReturnType(@NotNull PyCallable callable) {
      final PyType knownReturnType = super.getKnownReturnType(callable);
      if (knownReturnType != null) {
        return knownReturnType;
      }
      return myParent.getKnownReturnType(callable);
    }

    @Override
    public void trace(String message, Object... args) {
      myParent.trace(message, args);
    }

    @Override
    public void traceIndent() {
      myParent.traceIndent();
    }

    @Override
    public void traceUnindent() {
      myParent.traceUnindent();
    }

    @Override
    public boolean equals(Object o) {
      // Otherwise, it can be equal to other AssumptionContext with same constraints
      return this == o;
    }
  }

  final static class LibraryTypeEvalContext extends TypeEvalContext {
    private LibraryTypeEvalContext(@NotNull TypeEvalConstraints constraints) {
      super(constraints);
    }

    @Override
    protected boolean canDelegateToLibraryContext(PyTypedElement element) {
      // It's already the library-context.
      return false;
    }
  }

  final static class OptimizedTypeEvalContext extends TypeEvalContext {
    private volatile TypeEvalContext codeInsightFallback;

    OptimizedTypeEvalContext(boolean allowDataFlow, boolean allowStubToAST, boolean allowCallContext, @Nullable PsiFile origin) {
      super(allowDataFlow, allowStubToAST, allowCallContext, origin);
    }

    private boolean shouldSwitchToFallbackContext(PsiElement element) {
      PsiFile file = element.getContainingFile();
      if (file instanceof PyExpressionCodeFragment codeFragment) {
        PsiElement context = codeFragment.getContext();
        if (context != null) {
          file = context.getContainingFile();
        }
      }
      TypeEvalConstraints constraints = getConstraints();
      return constraints.myOrigin != null && file != constraints.myOrigin && (file instanceof PyFile) &&
             !constraints.myAllowDataFlow && !constraints.myAllowStubToAST && !constraints.myAllowCallContext;
    }

    private TypeEvalContext getFallbackContext(Project project) {
      if (codeInsightFallback == null) {
        codeInsightFallback = codeInsightFallback(project);
      }
      return codeInsightFallback;
    }

    @Override
    protected @Nullable PyType getKnownType(@NotNull PyTypedElement element) {
      if (shouldSwitchToFallbackContext(element)) {
        return getFallbackContext(element.getProject()).getKnownType(element);
      }
      return super.getKnownType(element);
    }

    @Override
    protected @Nullable PyType getKnownReturnType(@NotNull PyCallable callable) {
      if (shouldSwitchToFallbackContext(callable)) {
        return getFallbackContext(callable.getProject()).getKnownReturnType(callable);
      }
      return super.getKnownReturnType(callable);
    }

    @Override
    public @Nullable PyType getType(@NotNull PyTypedElement element) {
      if (shouldSwitchToFallbackContext(element)) {
        return getFallbackContext(element.getProject()).getType(element);
      }
      return super.getType(element);
    }

    @Override
    public @Nullable PyType getReturnType(@NotNull PyCallable callable) {
      if (shouldSwitchToFallbackContext(callable)) {
        return getFallbackContext(callable.getProject()).getReturnType(callable);
      }
      return super.getReturnType(callable);
    }
  }
}
