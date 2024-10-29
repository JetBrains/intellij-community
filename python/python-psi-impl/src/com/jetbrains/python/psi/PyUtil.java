// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.google.common.collect.Maps;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.completion.OverwriteEqualsInsertHandler;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.mlcompletion.PyCompletionMlElementInfo;
import com.jetbrains.python.codeInsight.mlcompletion.PyCompletionMlElementKind;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PySetuptoolsNamespaceIndex;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiStubSuppressor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static com.jetbrains.python.ast.PyAstFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.ast.PyAstFunction.Modifier.STATICMETHOD;

/**
 * Assorted utility methods for Python code insight.
 *
 * These methods don't depend on the Python runtime.
 *
 * @see PyPsiUtils for utilities used in Python PSI API
 * @see PyUiUtil for UI-related utilities for Python (available in PythonCore plugin)
 */
public final class PyUtil {

  private static final boolean VERBOSE_MODE = System.getenv().get("_PYCHARM_VERBOSE_MODE") != null;

  private PyUtil() {
  }

  /**
   * Flattens the representation of every element in targets, and puts all results together.
   * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
   * Typical usage: {@code flattenedParensAndTuples(some_tuple.getExpressions())}.
   *
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  @NotNull
  public static List<PyExpression> flattenedParensAndTuples(PyExpression... targets) {
    //noinspection unchecked,rawtypes
    return (List)PyUtilCore.flattenedParensAndTuples(targets);
  }

  @NotNull
  public static List<PyExpression> flattenedParensAndLists(PyExpression... targets) {
    //noinspection unchecked
    return (List)PyUtilCore.flattenedParensAndLists(targets);
  }

  @NotNull
  public static List<PyExpression> flattenedParensAndStars(PyExpression... targets) {
    //noinspection unchecked,rawtypes
    return (List)PyUtilCore.flattenedParensAndStars(targets);
  }

  /**
   * Produce a reasonable representation of a PSI element, good for debugging.
   *
   * @param elt      element to represent; nulls and invalid nodes are ok.
   * @param cutAtEOL if true, representation stops at nearest EOL inside the element.
   * @return the representation.
   */
  @NotNull
  @NlsSafe
  public static String getReadableRepr(PsiElement elt, final boolean cutAtEOL) {
    if (elt == null) return "null!";
    ASTNode node = elt.getNode();
    if (node == null) {
      return "null";
    }
    else {
      String s = node.getText();
      int cut_pos;
      if (cutAtEOL) {
        cut_pos = s.indexOf('\n');
      }
      else {
        cut_pos = -1;
      }
      if (cut_pos < 0) cut_pos = s.length();
      return s.substring(0, Math.min(cut_pos, s.length()));
    }
  }

  @Nullable
  public static PyClass getContainingClassOrSelf(final PsiElement element) {
    PsiElement current = element;
    while (current != null && !(current instanceof PyClass)) {
      current = current.getParent();
    }
    return (PyClass)current;
  }

  /**
   * @param element for which to obtain the file
   * @return PyFile, or null, if there's no containing file, or it is not a PyFile.
   */
  @Nullable
  public static PyFile getContainingPyFile(PyElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile instanceof PyFile ? (PyFile)containingFile : null;
  }

  /**
   * Returns a quoted string representation, or "null".
   */
  @NonNls
  public static String nvl(Object s) {
    if (s != null) {
      return "'" + s.toString() + "'";
    }
    else {
      return "null";
    }
  }

  /**
   * Adds an item into a comma-separated list in a PSI tree. E.g. can turn "foo, bar" into "foo, bar, baz", adding commas as needed.
   *
   * @param parent     the element to represent the list; we're adding a child to it.
   * @param newItem    the element we're inserting (the "baz" in the example).
   * @param beforeThis node to mark the insertion point inside the list; must belong to a child of target. Set to null to add first element.
   * @param isFirst    true if we don't need a comma before the element we're adding.
   * @param isLast     true if we don't need a comma after the element we're adding.
   */
  public static void addListNode(PsiElement parent, PsiElement newItem, ASTNode beforeThis,
                                 boolean isFirst, boolean isLast, boolean addWhitespace) {
    ASTNode node = parent.getNode();
    assert node != null;
    ASTNode itemNode = newItem.getNode();
    assert itemNode != null;
    Project project = parent.getProject();
    PyElementGenerator gen = PyElementGenerator.getInstance(project);
    if (!isFirst) node.addChild(gen.createComma(), beforeThis);
    node.addChild(itemNode, beforeThis);
    if (!isLast) node.addChild(gen.createComma(), beforeThis);
    if (addWhitespace) node.addChild(ASTFactory.whitespace(" "), beforeThis);
  }

  // TODO: move to a more proper place?

  /**
   * Determine the type of a special attribute. Currently supported: {@code __class__} and {@code __dict__}.
   *
   * @param ref reference to a possible attribute; only qualified references make sense.
   * @return type, or null (if type cannot be determined, reference is not to a known attribute, etc.)
   */
  @Nullable
  public static PyType getSpecialAttributeType(@Nullable PyReferenceExpression ref, TypeEvalContext context) {
    if (ref != null) {
      PyExpression qualifier = ref.getQualifier();
      if (qualifier != null) {
        String attr_name = ref.getReferencedName();
        if (PyNames.__CLASS__.equals(attr_name)) {
          PyType qualifierType = context.getType(qualifier);
          if (qualifierType instanceof PyClassType) {
            return new PyClassTypeImpl(((PyClassType)qualifierType).getPyClass(), true); // always as class, never instance
          }
        }
        else if (PyNames.DUNDER_DICT.equals(attr_name)) {
          PyType qualifierType = context.getType(qualifier);
          if (qualifierType instanceof PyClassType && ((PyClassType)qualifierType).isDefinition()) {
            return PyBuiltinCache.getInstance(ref).getDictType();
          }
        }
      }
    }
    return null;
  }

  /**
   * Makes sure that 'thing' is not null; else throws an {@link IncorrectOperationException}.
   *
   * @param thing what we check.
   * @return thing, if not null.
   */
  @NotNull
  public static <T> T sure(T thing) {
    if (thing == null) throw new IncorrectOperationException();
    return thing;
  }

  /**
   * Makes sure that the 'thing' is true; else throws an {@link IncorrectOperationException}.
   *
   * @param thing what we check.
   */
  public static void sure(boolean thing) {
    if (!thing) throw new IncorrectOperationException();
  }

  public static boolean isAttribute(PyTargetExpression ex) {
    return isInstanceAttribute(ex) || isClassAttribute(ex);
  }

  public static boolean isInstanceAttribute(PyExpression target) {
    if (!(target instanceof PyTargetExpression)) {
      return false;
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(target);
    if (owner instanceof PyFunction method) {
      if (method.getContainingClass() != null) {
        if (method.getStub() != null) {
          return true;
        }
        final PyParameter[] params = method.getParameterList().getParameters();
        if (params.length > 0) {
          final PyTargetExpression targetExpr = (PyTargetExpression)target;
          final PyExpression qualifier = targetExpr.getQualifier();
          return qualifier != null && qualifier.getText().equals(params[0].getName());
        }
      }
    }
    return false;
  }

  public static boolean isClassAttribute(PsiElement element) {
    return element instanceof PyTargetExpression && ScopeUtil.getScopeOwner(element) instanceof PyClass;
  }

  public static boolean hasIfNameEqualsMain(@NotNull PyFile file) {
    final PyIfStatement dunderMain = SyntaxTraverser.psiApi()
      .children(file)
      .filterMap(psi -> psi instanceof PyIfStatement ? ((PyIfStatement)psi) : null)
      .find(ifStatement -> isIfNameEqualsMain(ifStatement));
    return dunderMain != null;
  }

  public static boolean isIfNameEqualsMain(PyIfStatement ifStatement) {
    final PyExpression condition = ifStatement.getIfPart().getCondition();
    return isNameEqualsMain(condition);
  }

  private static boolean isNameEqualsMain(PyExpression condition) {
    if (condition instanceof PyParenthesizedExpression) {
      return isNameEqualsMain(((PyParenthesizedExpression)condition).getContainedExpression());
    }
    if (condition instanceof PyBinaryExpression binaryExpression) {
      if (binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
        return isNameEqualsMain(binaryExpression.getLeftExpression()) || isNameEqualsMain(binaryExpression.getRightExpression());
      }
      if (binaryExpression.getRightExpression() instanceof PyStringLiteralExpression rhs) {
        return binaryExpression.getOperator() == PyTokenTypes.EQEQ &&
               binaryExpression.getLeftExpression().getText().equals(PyNames.NAME) &&
               rhs.getStringValue().equals("__main__");
      }
    }
    return false;
  }

  /**
   * Searches for a method wrapping given element.
   *
   * @param start element presumably inside a method
   * @param deep  if true, allow 'start' to be inside functions nested in a method; else, 'start' must be directly inside a method.
   * @return if not 'deep', [0] is the method and [1] is the class; if 'deep', first several elements may be the nested functions,
   * the last but one is the method, and the last is the class.
   */
  @Nullable
  public static List<PsiElement> searchForWrappingMethod(PsiElement start, boolean deep) {
    PsiElement seeker = start;
    List<PsiElement> ret = new ArrayList<>(2);
    while (seeker != null) {
      PyFunction func = PsiTreeUtil.getParentOfType(seeker, PyFunction.class, true, PyClass.class);
      if (func != null) {
        PyClass cls = func.getContainingClass();
        if (cls != null) {
          ret.add(func);
          ret.add(cls);
          return ret;
        }
        else if (deep) {
          ret.add(func);
          seeker = func;
        }
        else {
          return null; // no immediate class
        }
      }
      else {
        return null; // no function
      }
    }
    return null;
  }

  public static boolean inSameFile(@NotNull PsiElement e1, @NotNull PsiElement e2) {
    final PsiFile f1 = e1.getContainingFile();
    final PsiFile f2 = e2.getContainingFile();
    if (f1 == null || f2 == null) {
      return false;
    }
    return f1 == f2;
  }

  public static boolean isTopLevel(@NotNull PsiElement element) {
    return PyUtilCore.isTopLevel(element);
  }

  public static void deletePycFiles(String pyFilePath) {
    if (pyFilePath.endsWith(PyNames.DOT_PY)) {
      List<File> filesToDelete = new ArrayList<>();
      File pyc = new File(pyFilePath + "c");
      if (pyc.exists()) {
        filesToDelete.add(pyc);
      }
      File pyo = new File(pyFilePath + "o");
      if (pyo.exists()) {
        filesToDelete.add(pyo);
      }
      final File file = new File(pyFilePath);
      File pycache = new File(file.getParentFile(), PyNames.PYCACHE);
      if (pycache.isDirectory()) {
        final String shortName = FileUtilRt.getNameWithoutExtension(file.getName());
        Collections.addAll(filesToDelete, pycache.listFiles(pathname -> {
          if (!FileUtilRt.extensionEquals(pathname.getName(), "pyc")) return false;
          String nameWithMagic = FileUtilRt.getNameWithoutExtension(pathname.getName());
          return FileUtilRt.getNameWithoutExtension(nameWithMagic).equals(shortName);
        }));
      }
      FileUtil.asyncDelete(filesToDelete);
    }
  }

  public static String getElementNameWithoutExtension(PsiNamedElement psiNamedElement) {
    return psiNamedElement instanceof PyFile
           ? FileUtilRt.getNameWithoutExtension(((PyFile)psiNamedElement).getName())
           : psiNamedElement.getName();
  }

  public static boolean hasUnresolvedAncestors(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    for (PyClassLikeType type : cls.getAncestorTypes(context)) {
      if (type == null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static AccessDirection getPropertyAccessDirection(@NotNull PyFunction function) {
    final Property property = function.getProperty();
    if (property != null) {
      if (property.getGetter().valueOrNull() == function) {
        return AccessDirection.READ;
      }
      if (property.getSetter().valueOrNull() == function) {
        return AccessDirection.WRITE;
      }
      else if (property.getDeleter().valueOrNull() == function) {
        return AccessDirection.DELETE;
      }
    }
    return AccessDirection.READ;
  }

  public static void removeQualifier(@NotNull final PyReferenceExpression element) {
    final PyExpression qualifier = element.getQualifier();
    if (qualifier == null) return;

    if (qualifier instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)qualifier).getCallee();
      if (callee instanceof PyReferenceExpression) {
        final PyExpression calleeQualifier = ((PyReferenceExpression)callee).getQualifier();
        if (calleeQualifier != null) {
          qualifier.replace(calleeQualifier);
          return;
        }
      }
    }
    final PsiElement dot = PyPsiUtils.getNextNonWhitespaceSibling(qualifier);
    if (dot != null) dot.delete();
    qualifier.delete();
  }

  public static boolean isOwnScopeComprehension(@NotNull PyComprehensionElement comprehension) {
    final boolean isAtLeast30 = !LanguageLevel.forElement(comprehension).isPython2();
    final boolean isListComprehension = comprehension instanceof PyListCompExpression;
    return !isListComprehension || isAtLeast30;
  }

  public static ASTNode createNewName(PyElement element, String name) {
    return PyElementGenerator.getInstance(element.getProject()).createNameIdentifier(name, LanguageLevel.forElement(element));
  }

  /**
   * Finds element declaration by resolving its references top the top but not further than file (to prevent un-stubbing)
   *
   * @param elementToResolve element to resolve
   * @return its declaration
   */
  @NotNull
  public static PsiElement resolveToTheTop(@NotNull final PsiElement elementToResolve) {
    PsiElement currentElement = elementToResolve;
    final Set<PsiElement> checkedElements = new HashSet<>(); // To prevent PY-20553
    while (true) {
      final PsiReference reference = currentElement.getReference();
      if (reference == null) {
        break;
      }
      final PsiElement resolve = reference.resolve();
      if (resolve == null || checkedElements.contains(resolve) || resolve.equals(currentElement) || !inSameFile(resolve, currentElement)) {
        break;
      }
      currentElement = resolve;
      checkedElements.add(resolve);
    }
    return currentElement;
  }

  /**
   * Note that returned list may contain {@code null} items, e.g. for unresolved import elements, originally wrapped
   * in {@link com.jetbrains.python.psi.resolve.ImportedResolveResult}.
   */
  @NotNull
  public static List<PsiElement> multiResolveTopPriority(@NotNull PsiElement element, @NotNull PyResolveContext resolveContext) {
    if (element instanceof PyReferenceOwner) {
      final PsiPolyVariantReference ref = ((PyReferenceOwner)element).getReference(resolveContext);
      return filterTopPriorityResults(ref.multiResolve(false));
    }
    else {
      final PsiReference reference = element.getReference();
      return reference != null ? Collections.singletonList(reference.resolve()) : Collections.emptyList();
    }
  }

  @NotNull
  public static List<PsiElement> multiResolveTopPriority(@NotNull PsiPolyVariantReference reference) {
    return filterTopPriorityResults(reference.multiResolve(false));
  }

  @NotNull
  public static List<PsiElement> filterTopPriorityResults(ResolveResult @NotNull [] resolveResults) {
    if (resolveResults.length == 0) return Collections.emptyList();

    final int maxRate = getMaxRate(Arrays.asList(resolveResults));
    return StreamEx
      .of(resolveResults)
      .filter(resolveResult -> getRate(resolveResult) >= maxRate)
      .map(ResolveResult::getElement)
      .nonNull()
      .toList();
  }

  @NotNull
  public static <E extends ResolveResult> List<E> filterTopPriorityResults(@NotNull List<? extends E> resolveResults) {
    if (resolveResults.isEmpty()) return Collections.emptyList();

    final int maxRate = getMaxRate(resolveResults);
    return ContainerUtil.filter(resolveResults, resolveResult -> getRate(resolveResult) >= maxRate);
  }

  public static @NotNull <E extends ResolveResult> List<PsiElement> filterTopPriorityElements(@NotNull List<? extends E> resolveResults) {
    return ContainerUtil.mapNotNull(filterTopPriorityResults(resolveResults), ResolveResult::getElement);
  }

  private static int getMaxRate(@NotNull List<? extends ResolveResult> resolveResults) {
    return resolveResults
      .stream()
      .mapToInt(PyUtil::getRate)
      .max()
      .orElse(Integer.MIN_VALUE);
  }

  private static int getRate(@NotNull ResolveResult resolveResult) {
    return resolveResult instanceof RatedResolveResult ? ((RatedResolveResult)resolveResult).getRate() : RatedResolveResult.RATE_NORMAL;
  }

  /**
   * Gets class init method
   *
   * @param pyClass class where to find init
   * @return class init method if any
   */
  @Nullable
  public static PyFunction getInitMethod(@NotNull final PyClass pyClass) {
    return pyClass.findMethodByName(PyNames.INIT, false, null);
  }

  /**
   * Clone of C# "as" operator.
   * Checks if expression has correct type and casts it if it has. Returns null otherwise.
   * It saves coder from "instanceof / cast" chains.
   *
   * @param expression expression to check
   * @param clazz      class to cast
   * @param <T>        class to cast
   * @return expression casted to appropriate type (if could be casted). Null otherwise.
   */
  @Nullable
  public static <T> T as(@Nullable final Object expression, @NotNull final Class<T> clazz) {
    return ObjectUtils.tryCast(expression, clazz);
  }

  // TODO: Move to PsiElement?

  /**
   * Searches for references injected to element with certain type
   *
   * @param element       element to search injected references for
   * @param expectedClass expected type of element reference resolved to
   * @param <T>           expected type of element reference resolved to
   * @return resolved element if found or null if not found
   */
  @Nullable
  public static <T extends PsiElement> T findReference(@NotNull final PsiElement element, @NotNull final Class<T> expectedClass) {
    for (final PsiReference reference : element.getReferences()) {
      final T result = as(reference.resolve(), expectedClass);
      if (result != null) {
        return result;
      }
    }
    return null;
  }


  /**
   * Converts collection to list of certain type
   *
   * @param expression   expression of collection type
   * @param elementClass expected element type
   * @param <T>          expected element type
   * @return list of elements of expected element type
   */
  @NotNull
  public static <T> List<T> asList(@Nullable final Collection<?> expression, @NotNull final Class<? extends T> elementClass) {
    if ((expression == null) || expression.isEmpty()) {
      return Collections.emptyList();
    }
    final List<T> result = new ArrayList<>();
    for (final Object element : expression) {
      final T toAdd = as(element, elementClass);
      if (toAdd != null) {
        result.add(toAdd);
      }
    }
    return result;
  }

  /**
   * Calculates and caches value based on param. Think about it as about map with param as key which flushes on each psi modification.
   * <p>
   * For nullable function see {@link #getNullableParameterizedCachedValue(PsiElement, Object, NullableFunction)}.
   * <p>
   * This function is used instead of {@link CachedValuesManager#createParameterizedCachedValue(ParameterizedCachedValueProvider, boolean)}
   * because parameter is not used as key there but used only for first calculation. Hence this should have functional dependency on element.
   *
   * @param element place to store cache
   * @param param   param to be used as key
   * @param f       function to produce value for key
   * @param <T>     value type
   * @param <P>     key type
   */
  @NotNull
  public static <T, P> T getParameterizedCachedValue(@NotNull PsiElement element, @Nullable P param, @NotNull Function<P, @NotNull T> f) {
    final T result = getNullableParameterizedCachedValue(element, param, f);
    assert result != null;
    return result;
  }

  /**
   * Same as {@link #getParameterizedCachedValue(PsiElement, Object, Function)} but allows nulls.
   */
  @Nullable
  public static <T, P> T getNullableParameterizedCachedValue(@NotNull PsiElement element,
                                                             @Nullable P param,
                                                             @NotNull Function<P, @Nullable T> f) {
    final CachedValuesManager manager = CachedValuesManager.getManager(element.getProject());
    final Map<Optional<P>, Optional<T>> cache = CachedValuesManager.getCachedValue(element, manager.getKeyForClass(f.getClass()), () -> {
      // concurrent hash map is a null-hostile collection
      return CachedValueProvider.Result.create(Maps.newConcurrentMap(), PsiModificationTracker.MODIFICATION_COUNT);
    });
    // Don't use ConcurrentHashMap#computeIfAbsent(), it blocks if the function tries to update the cache recursively for the same key
    // during computation. We can accept here that some values will be computed several times due to non-atomic updates.
    final Optional<P> wrappedParam = Optional.ofNullable(param);
    Optional<T> value = cache.get(wrappedParam);
    if (value == null) {
      value = Optional.ofNullable(f.fun(param));
      cache.put(wrappedParam, value);
    }
    return value.orElse(null);
  }

  /**
   * This method is allowed to be called from any thread, but in general you should not set {@code modal=true} if you're calling it
   * from the write action, because in this case {@code function} will be executed right in the current thread (presumably EDT)
   * without any progress whatsoever to avoid possible deadlock.
   *
   * @see com.intellij.openapi.application.impl.ApplicationImpl#runProcessWithProgressSynchronously(Runnable, String, boolean, boolean, Project, JComponent, String)
   */
  public static void runWithProgress(@Nullable Project project, @Nls(capitalization = Nls.Capitalization.Title) @NotNull String title,
                                     boolean modal, boolean canBeCancelled, @NotNull final Consumer<? super ProgressIndicator> function) {
    if (modal) {
      ProgressManager.getInstance().run(new Task.Modal(project, title, canBeCancelled) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          function.consume(indicator);
        }
      });
    }
    else {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, title, canBeCancelled) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          function.consume(indicator);
        }
      });
    }
  }

  /**
   * Executes code only if <pre>_PYCHARM_VERBOSE_MODE</pre> is set in env (which should be done for debug purposes only)
   *
   * @param runnable code to call
   */
  public static void verboseOnly(@NotNull final Runnable runnable) {
    if (VERBOSE_MODE) {
      runnable.run();
    }
  }

  public static boolean isPy2ReservedWord(@NotNull PyReferenceExpression node) {
    if (LanguageLevel.forElement(node).isPython2()) {
      if (!node.isQualified()) {
        final String name = node.getName();
        if (PyNames.NONE.equals(name) || PyNames.FALSE.equals(name) || PyNames.TRUE.equals(name)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Retrieves the document from {@link PsiDocumentManager} using the anchor PSI element and, if it's not null,
   * passes it to the consumer function.
   * <p>
   * The document is first released from pending PSI operations and then committed after the function has been applied
   * in a {@code try/finally} block, so that subsequent operations on PSI could be performed.
   *
   * @see PsiDocumentManager#doPostponedOperationsAndUnblockDocument(Document)
   * @see PsiDocumentManager#commitDocument(Document)
   * @see #updateDocumentUnblockedAndCommitted(PsiElement, Function)
   */
  public static void updateDocumentUnblockedAndCommitted(@NotNull PsiElement anchor, @NotNull Consumer<? super Document> consumer) {
    PyUtilCore.updateDocumentUnblockedAndCommitted(anchor, consumer);
  }

  @Nullable
  public static <T> T updateDocumentUnblockedAndCommitted(@NotNull PsiElement anchor, @NotNull Function<? super Document, ? extends T> func) {
    return PyUtilCore.updateDocumentUnblockedAndCommitted(anchor, func);
  }

  @Nullable
  public static PyType getReturnTypeToAnalyzeAsCallType(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    if (isInitMethod(function)) {
      final PyClass cls = function.getContainingClass();
      if (cls != null) {
        PyType providedClassType = getGenericTypeForClass(context, cls);
        if (providedClassType != null) return providedClassType;

        final PyInstantiableType classType = as(context.getType(cls), PyInstantiableType.class);
        if (classType != null) {
          return classType.toInstance();
        }
      }
    }

    return context.getReturnType(function);
  }

  public static @Nullable PyType getGenericTypeForClass(@NotNull TypeEvalContext context, PyClass cls) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final PyType providedClassType = provider.getGenericType(cls, context);
      if (providedClassType != null) {
        return providedClassType;
      }
    }
    return null;
  }

  /**
   * Create a new expressions fragment from the given text, setting the specified element as its context,
   * and return the contained expression of the first expression statement in it.
   *
   * @param expressionText text of the expression
   * @param context        context element used to resolve symbols in the expression
   * @return instance of {@link PyExpression} as described
   * @see PyExpressionCodeFragment
   */
  @Nullable
  public static PyExpression createExpressionFromFragment(@NotNull String expressionText, @NotNull PsiElement context) {
    final PyExpressionCodeFragmentImpl codeFragment =
      new PyExpressionCodeFragmentImpl(context.getProject(), "dummy.py", expressionText, false);
    codeFragment.setContext(context);
    final PyExpressionStatement statement = as(codeFragment.getFirstChild(), PyExpressionStatement.class);
    return statement != null ? statement.getExpression() : null;
  }

  public static boolean isRoot(PsiFileSystemItem directory) {
    if (directory == null) return true;
    VirtualFile vFile = directory.getVirtualFile();
    if (vFile == null) return true;
    Project project = directory.getProject();
    return isRoot(vFile, project);
  }

  public static boolean isRoot(@NotNull VirtualFile directory, @NotNull Project project) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    return Comparing.equal(fileIndex.getClassRootForFile(directory), directory) ||
           Comparing.equal(fileIndex.getContentRootForFile(directory), directory) ||
           Comparing.equal(fileIndex.getSourceRootForFile(directory), directory);
  }

  /**
   * Checks whether {@param file} representing Python module or package can be imported into {@param file}.
   */
  public static boolean isImportable(PsiFile targetFile, @NotNull PsiFileSystemItem file) {
    PsiDirectory parent = (PsiDirectory)file.getParent();
    return parent != null && file != targetFile &&
           (isRoot(parent) ||
            parent == targetFile.getParent() ||
            isPackage(parent, false, null));
  }

  @NotNull
  public static Collection<String> collectUsedNames(@Nullable final PsiElement scope) {
    if (!(scope instanceof PyClass) && !(scope instanceof PyFile) && !(scope instanceof PyFunction)) {
      return Collections.emptyList();
    }
    final Set<String> variables = new HashSet<>() {
      @Override
      public boolean add(String s) {
        return s != null && super.add(s);
      }
    };
    scope.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyTargetExpression(@NotNull final PyTargetExpression node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyNamedParameter(@NotNull final PyNamedParameter node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
        if (!node.isQualified()) {
          variables.add(node.getReferencedName());
        }
        else {
          super.visitPyReferenceExpression(node);
        }
      }

      @Override
      public void visitPyFunction(@NotNull final PyFunction node) {
        variables.add(node.getName());
      }

      @Override
      public void visitPyClass(@NotNull final PyClass node) {
        variables.add(node.getName());
      }
    });
    return variables;
  }

  /**
   * If argument is a PsiDirectory, turn it into a PsiFile that points to __init__.py in that directory.
   * If there's no __init__.py there, null is returned, there's no point to resolve to a dir which is not a package.
   * Alas, resolve() and multiResolve() can't return anything but a PyFile or PsiFileImpl.isPsiUpToDate() would fail.
   * This is because isPsiUpToDate() relies on identity of objects returned by FileViewProvider.getPsi().
   * If we ever need to exactly tell a dir from __init__.py, that logic has to change.
   *
   * @param target a resolve candidate.
   * @return a PsiFile if target was a PsiDirectory, or null, or target unchanged.
   */
  @Nullable
  public static PsiElement turnDirIntoInit(@Nullable PsiElement target) {
    if (target instanceof PsiDirectory dir) {
      final PsiFile initStub = dir.findFile(PyNames.INIT_DOT_PYI);
      if (initStub != null && !PyiStubSuppressor.isIgnoredStub(initStub)) {
        return initStub;
      }
      final PsiFile initFile = dir.findFile(PyNames.INIT_DOT_PY);
      if (initFile != null) {
        return initFile; // ResolveImportUtil will extract directory part as needed, everyone else are better off with a file.
      }
      else {
        return null;
      } // dir without __init__.py does not resolve
    }
    else {
      return target;
    } // don't touch non-dirs
  }

  @Nullable
  public static PsiElement turnDirIntoInitPy(@Nullable PsiElement target) {
    if (!(target instanceof PsiDirectory psiDirectory)) return target;
    return psiDirectory.findFile(PyNames.INIT_DOT_PY);
  }

  @Nullable
  public static PsiElement turnDirIntoInitPyi(@Nullable PsiElement target) {
    if (!(target instanceof PsiDirectory psiDirectory)) return target;
    final PsiFile initStub = psiDirectory.findFile(PyNames.INIT_DOT_PYI);
    if (initStub != null && !PyiStubSuppressor.isIgnoredStub(initStub)) {
      return initStub;
    }
    return null;
  }

  /**
   * If directory is a PsiDirectory, that is also a valid Python package, return PsiFile that points to __init__.py,
   * if such file exists, or directory itself (i.e. namespace package). Otherwise, return {@code null}.
   * Unlike {@link #turnDirIntoInit(PsiElement)} this function handles namespace packages and
   * accepts only PsiDirectories as target.
   *
   * @param directory directory to check
   * @param anchor    optional PSI element to determine language level as for {@link #isPackage(PsiDirectory, PsiElement)}
   * @return PsiFile or PsiDirectory, if target is a Python package and {@code null} null otherwise
   */
  @Nullable
  public static PsiElement getPackageElement(@NotNull PsiDirectory directory, @Nullable PsiElement anchor) {
    if (isPackage(directory, anchor)) {
      final PsiElement init = turnDirIntoInit(directory);
      if (init != null) {
        return init;
      }
      return directory;
    }
    return null;
  }

  /**
   * If target is a Python module named __init__.py file, return its directory. Otherwise return target unchanged.
   *
   * @param target PSI element to check
   * @return PsiDirectory or target unchanged
   */
  @Contract("null -> null")
  @Nullable
  public static PsiElement turnInitIntoDir(@Nullable PsiElement target) {
    if (target instanceof PyFile && isPackage((PsiFile)target)) {
      return ((PsiFile)target).getContainingDirectory();
    }
    return target;
  }

  /**
   * @see #isPackage(PsiDirectory, boolean, PsiElement)
   */
  public static boolean isPackage(@NotNull PsiDirectory directory, @Nullable PsiElement anchor) {
    return isPackage(directory, true, anchor);
  }

  /**
   * Checks that given PsiDirectory can be treated as Python package, i.e. it's either contains __init__.py or it's a namespace package
   * (effectively any directory in Python 3.3 and above). Setuptools namespace packages can be checked as well, but it requires access to
   * {@link PySetuptoolsNamespaceIndex} and may slow things down during update of project indexes.
   * Also note that this method does not check that directory itself and its parents have valid importable names,
   * use {@link PyNames#isIdentifier(String)} for this purpose.
   *
   * @param directory               PSI directory to check
   * @param checkSetupToolsPackages whether setuptools namespace packages should be considered as well
   * @param anchor                  optional anchor element to determine language level
   * @return whether given directory is Python package
   * @see PyNames#isIdentifier(String)
   */
  public static boolean isPackage(@NotNull PsiDirectory directory, boolean checkSetupToolsPackages, @Nullable PsiElement anchor) {
    if (isExplicitPackage(directory)) return true;
    @NotNull PsiElement element = anchor != null ? anchor : directory;
    final LanguageLevel level = LanguageLevel.forElement(element);
    if (!level.isPython2()) {
      return true;
    }
    return checkSetupToolsPackages && isSetuptoolsNamespacePackage(directory);
  }

  public static boolean isPackage(@NotNull PsiFile file) {
    for (PyCustomPackageIdentifier customPackageIdentifier : PyCustomPackageIdentifier.EP_NAME.getExtensions()) {
      if (customPackageIdentifier.isPackageFile(file)) {
        return true;
      }
    }
    return PyNames.INIT_DOT_PY.equals(file.getName());
  }

  public static boolean isPackage(@NotNull PsiFileSystemItem anchor, @Nullable PsiElement location) {
    return anchor instanceof PsiFile ? isPackage((PsiFile)anchor) :
           anchor instanceof PsiDirectory && isPackage((PsiDirectory)anchor, location);
  }

  public static boolean isCustomPackage(@NotNull PsiDirectory directory) {
    for (PyCustomPackageIdentifier customPackageIdentifier : PyCustomPackageIdentifier.EP_NAME.getExtensions()) {
      if (customPackageIdentifier.isPackage(directory)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isExplicitPackage(@NotNull PsiDirectory directory) {
    return isOrdinaryPackage(directory) || isCustomPackage(directory);
  }

  private static boolean isSetuptoolsNamespacePackage(@NotNull PsiDirectory directory) {
    final String packagePath = getPackagePath(directory);
    return packagePath != null && !PySetuptoolsNamespaceIndex.find(packagePath, directory.getProject()).isEmpty();
  }

  @Nullable
  private static String getPackagePath(@NotNull PsiDirectory directory) {
    final QualifiedName name = QualifiedNameFinder.findShortestImportableQName(directory);
    return name != null ? name.toString() : null;
  }

  /**
   * Counts initial underscores of an identifier.
   *
   * @param name identifier
   * @return 0 if null or no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  public static int getInitialUnderscores(@Nullable String name) {
    return PyUtilCore.getInitialUnderscores(name);
  }

  /**
   * @return true iff the name looks like a class-private one, starting with two underscores but not ending with two underscores.
   */
  public static boolean isClassPrivateName(@NotNull String name) {
    return name.startsWith("__") && !name.endsWith("__");
  }

  /**
   * Constructs new lookup element for completion of keyword argument with equals sign appended.
   *
   * @param name name of the parameter
   * @param settingsAnchor file to check code style settings and surround equals sign with spaces if necessary
   * @return lookup element
   */
  @NotNull
  public static LookupElement createNamedParameterLookup(@NotNull String name, @NotNull PsiFile settingsAnchor, boolean addEquals) {
    final String suffix;
    if (addEquals) {
      if (PythonCodeStyleService.getInstance().isSpaceAroundEqInKeywordArgument(settingsAnchor)) {
        suffix = " = ";
      }
      else {
        suffix = "=";
      }
    } else {
      suffix = "";
    }
    LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(name + suffix).withIcon(
      IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter));
    lookupElementBuilder = lookupElementBuilder.withInsertHandler(OverwriteEqualsInsertHandler.INSTANCE);
    lookupElementBuilder.putUserData(PyCompletionMlElementInfo.Companion.getKey(), PyCompletionMlElementKind.NAMED_ARG.asInfo());
    return PrioritizedLookupElement.withGrouping(lookupElementBuilder, 1);
  }

  @NotNull
  public static LookupElement createNamedParameterLookup(@NotNull String name, @NotNull PsiFile settingsAnchor) {
    return createNamedParameterLookup(name, settingsAnchor, true);
  }

  /**
   * Peels argument expression of parentheses and of keyword argument wrapper
   *
   * @param expr an item of getArguments() array
   * @return expression actually passed as argument
   */
  @Nullable
  public static PyExpression peelArgument(PyExpression expr) {
    while (expr instanceof PyParenthesizedExpression) expr = ((PyParenthesizedExpression)expr).getContainedExpression();
    if (expr instanceof PyKeywordArgument) expr = ((PyKeywordArgument)expr).getValueExpression();
    return expr;
  }

  public static String getFirstParameterName(PyFunction container) {
    String selfName = PyNames.CANONICAL_SELF;
    if (container != null) {
      final PyParameter[] params = container.getParameterList().getParameters();
      if (params.length > 0) {
        final PyNamedParameter named = params[0].getAsNamed();
        if (named != null) {
          selfName = named.getName();
        }
      }
    }
    return selfName;
  }

  @RequiresEdt
  public static void addSourceRoots(@NotNull Module module, @NotNull Collection<VirtualFile> roots) {
    if (roots.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(
      () -> {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        for (VirtualFile root : roots) {
          boolean added = false;
          for (ContentEntry entry : model.getContentEntries()) {
            final VirtualFile file = entry.getFile();
            if (file != null && VfsUtilCore.isAncestor(file, root, true)) {
              entry.addSourceFolder(root.getUrl(), JavaSourceRootType.SOURCE, true);
              added = true;
            }
          }

          if (!added) {
            model.addContentEntry(root).addSourceFolder(root.getUrl(), JavaSourceRootType.SOURCE, true);
          }
        }
        model.commit();
      }
    );
  }

  @RequiresEdt
  public static void removeSourceRoots(@NotNull Module module, @NotNull Collection<VirtualFile> roots) {
    if (roots.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(
      () -> {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        for (ContentEntry entry : model.getContentEntries()) {
          for (SourceFolder folder : entry.getSourceFolders()) {
            if (roots.contains(folder.getFile())) {
              entry.removeSourceFolder(folder);
            }
          }

          if (roots.contains(entry.getFile()) && entry.getSourceFolders().length == 0) {
            model.removeContentEntry(entry);
          }
        }
        model.commit();
      }
    );
  }

  /**
   * @return Source roots <strong>and</strong> content roots for element's project
   */
  @NotNull
  public static Collection<VirtualFile> getSourceRoots(@NotNull PsiElement foothold) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(foothold);
    if (module != null) {
      return getSourceRoots(module);
    }
    return Collections.emptyList();
  }

  /**
   * @return Source roots <strong>and</strong> content roots for module
   */
  @NotNull
  public static Collection<VirtualFile> getSourceRoots(@NotNull Module module) {
    final Set<VirtualFile> result = new LinkedHashSet<>();
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    Collections.addAll(result, manager.getSourceRoots());
    Collections.addAll(result, manager.getContentRoots());
    return result;
  }

  @Nullable
  public static VirtualFile findInRoots(Module module, String path) {
    if (module != null) {
      for (VirtualFile root : getSourceRoots(module)) {
        VirtualFile file = root.findFileByRelativePath(path);
        if (file != null) {
          return file;
        }
      }
    }
    return null;
  }

  @Nullable
  public static List<String> strListValue(PyExpression value) {
    return PyUtilCore.strListValue(value);
  }

  @NotNull
  public static Map<String, PyExpression> dictValue(@NotNull PyDictLiteralExpression dict) {
    Map<String, PyExpression> result = Maps.newLinkedHashMap();
    for (PyKeyValueExpression keyValue : dict.getElements()) {
      PyExpression key = keyValue.getKey();
      PyExpression value = keyValue.getValue();
      if (key instanceof PyStringLiteralExpression) {
        result.put(((PyStringLiteralExpression)key).getStringValue(), value);
      }
    }
    return result;
  }

  /**
   * @param what     thing to search for
   * @param variants things to search among
   * @return true iff what.equals() one of the variants.
   */
  public static <T> boolean among(@NotNull T what, T... variants) {
    for (T s : variants) {
      if (what.equals(s)) return true;
    }
    return false;
  }

  @Nullable
  public static String getKeywordArgumentString(PyCallExpression expr, String keyword) {
    return PyPsiUtils.strValue(expr.getKeywordArgument(keyword));
  }

  public static boolean isExceptionClass(PyClass pyClass) {
    if (isBaseException(pyClass.getQualifiedName())) {
      return true;
    }
    for (PyClassLikeType type : pyClass.getAncestorTypes(TypeEvalContext.codeInsightFallback(pyClass.getProject()))) {
      if (type != null && isBaseException(type.getClassQName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBaseException(String name) {
    return name != null && (name.contains("BaseException") || name.startsWith("exceptions."));
  }

  public static final class MethodFlags {

    private final boolean myIsStaticMethod;
    private final boolean myIsMetaclassMethod;
    private final boolean myIsSpecialMetaclassMethod;
    private final boolean myIsClassMethod;

    /**
     * @return true iff the method belongs to a metaclass (an ancestor of 'type').
     */
    public boolean isMetaclassMethod() {
      return myIsMetaclassMethod;
    }

    /**
     * @return iff isMetaclassMethod and the method is either __init__ or __call__.
     */
    public boolean isSpecialMetaclassMethod() {
      return myIsSpecialMetaclassMethod;
    }

    public boolean isStaticMethod() {
      return myIsStaticMethod;
    }

    public boolean isClassMethod() {
      return myIsClassMethod;
    }

    private MethodFlags(boolean isClassMethod, boolean isStaticMethod, boolean isMetaclassMethod, boolean isSpecialMetaclassMethod) {
      myIsClassMethod = isClassMethod;
      myIsStaticMethod = isStaticMethod;
      myIsMetaclassMethod = isMetaclassMethod;
      myIsSpecialMetaclassMethod = isSpecialMetaclassMethod;
    }

    /**
     * @param node a function
     * @return a new flags object, or null if the function is not a method
     */
    @Nullable
    public static MethodFlags of(@NotNull PyFunction node) {
      PyClass cls = node.getContainingClass();
      if (cls != null) {
        PyFunction.Modifier modifier = node.getModifier();
        boolean isMetaclassMethod = false;
        PyClass type_cls = PyBuiltinCache.getInstance(node).getClass("type");
        for (PyClass ancestor_cls : cls.getAncestorClasses(null)) {
          if (ancestor_cls == type_cls) {
            isMetaclassMethod = true;
            break;
          }
        }
        final String method_name = node.getName();
        boolean isSpecialMetaclassMethod = isMetaclassMethod && method_name != null && among(method_name, PyNames.INIT, "__call__");
        return new MethodFlags(modifier == CLASSMETHOD, modifier == STATICMETHOD, isMetaclassMethod, isSpecialMetaclassMethod);
      }
      return null;
    }

    //TODO: Doc
    public boolean isInstanceMethod() {
      return !(myIsClassMethod || myIsStaticMethod);
    }
  }

  public static boolean isSuperCall(@NotNull PyCallExpression node) {
    PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
    if (klass == null) return false;
    PyExpression callee = node.getCallee();
    if (callee == null) return false;
    String name = callee.getName();
    if (PyNames.SUPER.equals(name)) {
      PsiReference reference = callee.getReference();
      if (reference == null) return false;
      PsiElement resolved = reference.resolve();
      PyBuiltinCache cache = PyBuiltinCache.getInstance(node);
      if (resolved != null && cache.isBuiltin(resolved)) {
        PyExpression[] args = node.getArguments();
        if (args.length > 0) {
          String firstArg = args[0].getText();
          if (firstArg.equals(klass.getName()) || firstArg.equals(PyNames.CANONICAL_SELF + "." + PyNames.__CLASS__)) {
            return true;
          }
          for (PyClass s : klass.getAncestorClasses(null)) {
            if (firstArg.equals(s.getName())) {
              return true;
            }
          }
        }
        else {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static PsiElement findPrevAtOffset(PsiFile psiFile, int caretOffset, @NotNull Class<? extends PsiElement> @NotNull ... toSkip) {
    PsiElement element;
    if (caretOffset < 0) {
      return null;
    }
    int lineStartOffset = 0;
    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (document != null) {
      int lineNumber = document.getLineNumber(caretOffset);
      lineStartOffset = document.getLineStartOffset(lineNumber);
    }
    do {
      caretOffset--;
      element = psiFile.findElementAt(caretOffset);
    }
    while (caretOffset >= lineStartOffset && PsiTreeUtil.instanceOf(element, toSkip));
    return PsiTreeUtil.instanceOf(element, toSkip) ? null : element;
  }

  @Nullable
  public static PsiElement findNonWhitespaceAtOffset(PsiFile psiFile, int caretOffset) {
    PsiElement element = findNextAtOffset(psiFile, caretOffset, PsiWhiteSpace.class);
    if (element == null) {
      element = findPrevAtOffset(psiFile, caretOffset - 1, PsiWhiteSpace.class);
    }
    return element;
  }

  @Nullable
  public static PsiElement findElementAtOffset(PsiFile psiFile, int caretOffset) {
    PsiElement element = findPrevAtOffset(psiFile, caretOffset);
    if (element == null) {
      element = findNextAtOffset(psiFile, caretOffset);
    }
    return element;
  }

  @Nullable
  public static PsiElement findNextAtOffset(@NotNull final PsiFile psiFile, int caretOffset, @NotNull Class<? extends PsiElement> @NotNull ... toSkip) {
    PsiElement element = psiFile.findElementAt(caretOffset);
    if (element == null) {
      return null;
    }

    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    int lineEndOffset = 0;
    if (document != null) {
      int lineNumber = document.getLineNumber(caretOffset);
      lineEndOffset = document.getLineEndOffset(lineNumber);
    }
    while (caretOffset < lineEndOffset && PsiTreeUtil.instanceOf(element, toSkip)) {
      caretOffset++;
      element = psiFile.findElementAt(caretOffset);
    }
    return PsiTreeUtil.instanceOf(element, toSkip) ? null : element;
  }


  public static boolean isSignatureCompatibleTo(@NotNull PyCallable callable, @NotNull PyCallable otherCallable,
                                                @NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = callable.getParameters(context);
    final List<PyCallableParameter> otherParameters = otherCallable.getParameters(context);
    final int optionalCount = optionalParametersCount(parameters);
    final int otherOptionalCount = optionalParametersCount(otherParameters);
    final int requiredCount = requiredParametersCount(callable, parameters);
    final int otherRequiredCount = requiredParametersCount(otherCallable, otherParameters);
    if (hasPositionalContainer(otherParameters) || hasKeywordContainer(otherParameters)) {
      if (otherParameters.size() == specialParametersCount(otherCallable, otherParameters)) {
        return true;
      }
    }
    if (hasPositionalContainer(parameters) || hasKeywordContainer(parameters)) {
      return requiredCount <= otherRequiredCount;
    }
    return requiredCount <= otherRequiredCount &&
           optionalCount >= otherOptionalCount &&
           namedParametersCount(parameters) >= namedParametersCount(otherParameters);
  }

  private static int optionalParametersCount(@NotNull List<PyCallableParameter> parameters) {
    int n = 0;
    for (PyCallableParameter parameter : parameters) {
      if (parameter.hasDefaultValue()) {
        n++;
      }
    }
    return n;
  }

  private static int requiredParametersCount(@NotNull PyCallable callable, @NotNull List<PyCallableParameter> parameters) {
    return namedParametersCount(parameters) - optionalParametersCount(parameters) - specialParametersCount(callable, parameters);
  }

  private static int specialParametersCount(@NotNull PyCallable callable, @NotNull List<PyCallableParameter> parameters) {
    int n = 0;
    if (hasPositionalContainer(parameters)) {
      n++;
    }
    if (hasKeywordContainer(parameters)) {
      n++;
    }
    if (isFirstParameterSpecial(callable, parameters)) {
      n++;
    }
    return n;
  }

  private static boolean hasPositionalContainer(@NotNull List<PyCallableParameter> parameters) {
    for (PyCallableParameter parameter : parameters) {
      if (parameter.isPositionalContainer()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasKeywordContainer(@NotNull List<PyCallableParameter> parameters) {
    for (PyCallableParameter parameter : parameters) {
      if (parameter.isKeywordContainer()) {
        return true;
      }
    }
    return false;
  }

  private static int namedParametersCount(@NotNull List<PyCallableParameter> parameters) {
    return ContainerUtil.count(parameters, p -> p.getParameter() instanceof PyNamedParameter);
  }

  private static boolean isFirstParameterSpecial(@NotNull PyCallable callable, @NotNull List<PyCallableParameter> parameters) {
    final PyFunction method = callable.asMethod();
    if (method != null) {
      return isNewMethod(method) || method.getModifier() != STATICMETHOD;
    }
    else {
      final PyCallableParameter first = ContainerUtil.getFirstItem(parameters);
      return first != null && PyNames.CANONICAL_SELF.equals(first.getName());
    }
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__}.
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#isInitOrNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  @Contract("null -> false")
  public static boolean isInitMethod(@Nullable PsiElement element) {
    final PyFunction function = as(element, PyFunction.class);
    return function != null && PyNames.INIT.equals(function.getName()) && function.getContainingClass() != null;
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isInitOrNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  @Contract("null -> false")
  public static boolean isNewMethod(@Nullable PsiElement element) {
    return PyUtilCore.isNewMethod(element);
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__} or {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  @Contract("null -> false")
  public static boolean isInitOrNewMethod(@Nullable PsiElement element) {
    return PyUtilCore.isInitOrNewMethod(element);
  }

  /**
   * @return containing class for a method named {@code __init__} or {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#isInitOrNewMethod(PsiElement)
   */
  @Nullable
  @Contract("null -> null")
  public static PyClass turnConstructorIntoClass(@Nullable PyFunction function) {
    return isInitOrNewMethod(function) ? function.getContainingClass() : null;
  }

  public static boolean isStarImportableFrom(@NotNull String name, @NotNull PyFile file) {
    final List<String> dunderAll = file.getDunderAll();
    return dunderAll != null ? dunderAll.contains(name) : !name.startsWith("_");
  }

  public static boolean isObjectClass(@NotNull PyClass cls) {
    String qualifiedName = cls.getQualifiedName();
    return PyNames.OBJECT.equals(qualifiedName) || (qualifiedName == null && PyNames.OBJECT.equals(cls.getName()));
  }

  @Nullable
  public static PyType getReturnTypeOfMember(@NotNull PyType type,
                                             @NotNull String memberName,
                                             @Nullable PyExpression location,
                                             @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
    final List<? extends RatedResolveResult> resolveResults = type.resolveMember(memberName, location, AccessDirection.READ,
                                                                                 resolveContext);

    if (resolveResults != null) {
      final List<PyType> types = new ArrayList<>();

      for (RatedResolveResult resolveResult : resolveResults) {
        final PyType returnType = getReturnType(resolveResult.getElement(), context);

        if (returnType != null) {
          types.add(returnType);
        }
      }

      return PyUnionType.union(types);
    }

    return null;
  }

  @Nullable
  private static PyType getReturnType(@Nullable PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);

      return getReturnType(type, context);
    }

    return null;
  }

  @Nullable
  private static PyType getReturnType(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type instanceof PyCallableType) {
      return ((PyCallableType)type).getReturnType(context);
    }

    if (type instanceof PyUnionType) {
      return PyUnionType.toNonWeakType(((PyUnionType)type).map(member -> getReturnType(member, context)));
    }

    return null;
  }

  public static boolean isEmptyFunction(@NotNull PyFunction function) {
    final PyStatementList statementList = function.getStatementList();
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length == 0) {
      return true;
    }
    else if (statements.length == 1) {
      if (isStringLiteral(statements[0]) || isPassOrRaiseOrEmptyReturnOrEllipsis(statements[0])) {
        return true;
      }
    }
    else if (statements.length == 2) {
      if (isStringLiteral(statements[0]) && (isPassOrRaiseOrEmptyReturnOrEllipsis(statements[1]))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPassOrRaiseOrEmptyReturnOrEllipsis(PyStatement stmt) {
    if (stmt instanceof PyPassStatement || stmt instanceof PyRaiseStatement) {
      return true;
    }
    if (stmt instanceof PyReturnStatement && ((PyReturnStatement)stmt).getExpression() == null) {
      return true;
    }
    if (stmt instanceof PyExpressionStatement) {
      final PyExpression expression = ((PyExpressionStatement)stmt).getExpression();
      if (expression instanceof PyNoneLiteralExpression && ((PyNoneLiteralExpression)expression).isEllipsis()) {
        return true;
      }
    }
    return false;
  }

  public static boolean isStringLiteral(@Nullable PyStatement stmt) {
    return PyUtilCore.isStringLiteral(stmt);
  }

  @Nullable
  public static PyLoopStatement getCorrespondingLoop(@NotNull PsiElement breakOrContinue) {
    return (PyLoopStatement)PyUtilCore.getCorrespondingLoop(breakOrContinue);
  }

  public static boolean isForbiddenMutableDefault(@Nullable PyTypedElement value, @NotNull TypeEvalContext context) {
    if (value == null) return false;

    Set<PyClass> pyClasses = PyTypeUtil.toStream(context.getType(value))
      .select(PyClassType.class)
      .filter(clsType -> !clsType.isDefinition())
      .map(PyClassType::getPyClass)
      .toSet();

    if (!pyClasses.isEmpty()) {
      PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(value);
      Set<PyClass> forbiddenClasses = StreamEx
        .of(builtinCache.getListType(), builtinCache.getSetType(), builtinCache.getDictType())
        .nonNull()
        .map(PyClassType::getPyClass)
        .toSet();

      return StreamEx.of(pyClasses)
        .flatMap(pyClass -> StreamEx.of(pyClass).append(pyClass.getAncestorClasses(context)))
        .anyMatch(forbiddenClasses::contains);
    }

    return false;
  }

  public static void addDecorator(@NotNull PyFunction function, @NotNull String decorator) {
    final PyDecoratorList currentDecorators = function.getDecoratorList();

    final List<String> decoTexts = new ArrayList<>();
    decoTexts.add(decorator);
    if (currentDecorators != null) {
      for (PyDecorator deco : currentDecorators.getDecorators()) {
        decoTexts.add(deco.getText());
      }
    }

    final PyElementGenerator generator = PyElementGenerator.getInstance(function.getProject());
    final PyDecoratorList newDecorators = generator.createDecoratorList(ArrayUtilRt.toStringArray(decoTexts));

    if (currentDecorators != null) {
      currentDecorators.replace(newDecorators);
    }
    else {
      function.addBefore(newDecorators, function.getFirstChild());
    }
  }

  public static boolean isOrdinaryPackage(@NotNull PsiDirectory directory) {
    return directory.findFile(PyNames.INIT_DOT_PY) != null;
  }

  public static boolean isNoinspectionComment(@NotNull PsiComment comment) {
    Pattern suppressPattern = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);
    return suppressPattern.matcher(comment.getText()).find();
  }

  /**
   * This helper class allows to collect various information about AST nodes composing {@link PyStringLiteralExpression}.
   */
  public static final class StringNodeInfo {
    private final ASTNode myNode;
    private final String myPrefix;
    private final String myQuote;
    private final TextRange myContentRange;

    public StringNodeInfo(@NotNull ASTNode node) {
      final IElementType nodeType = node.getElementType();
      // TODO Migrate to newer PyStringElement API
      if (!PyTokenTypes.STRING_NODES.contains(nodeType) && nodeType != PyElementTypes.FSTRING_NODE) {
        throw new IllegalArgumentException("Node must be valid Python string literal token, but " + nodeType + " was given");
      }
      myNode = node;
      final String nodeText = node.getText();
      final int prefixLength = PyStringLiteralUtil.getPrefixLength(nodeText);
      myPrefix = nodeText.substring(0, prefixLength);
      myContentRange = PyStringLiteralUtil.getContentRange(nodeText);
      myQuote = nodeText.substring(prefixLength, myContentRange.getStartOffset());
    }

    public StringNodeInfo(@NotNull PsiElement element) {
      this(element.getNode());
    }

    @NotNull
    public ASTNode getNode() {
      return myNode;
    }

    /**
     * @return string prefix, e.g. "UR", "b" etc.
     */
    @NotNull
    public String getPrefix() {
      return myPrefix;
    }

    /**
     * @return content of the string node between quotes
     */
    @NotNull
    public String getContent() {
      return myContentRange.substring(myNode.getText());
    }

    /**
     * @return <em>relative</em> range of the content (excluding prefix and quotes)
     * @see #getAbsoluteContentRange()
     */
    @NotNull
    public TextRange getContentRange() {
      return myContentRange;
    }

    /**
     * @return <em>absolute</em> content range that accounts offset of the {@link #getNode() node} in the document
     */
    @NotNull
    public TextRange getAbsoluteContentRange() {
      return getContentRange().shiftRight(myNode.getStartOffset());
    }

    /**
     * @return the first character of {@link #getQuote()}
     */
    public char getSingleQuote() {
      return myQuote.charAt(0);
    }

    @NotNull
    public String getQuote() {
      return myQuote;
    }

    public boolean isTripleQuoted() {
      return myQuote.length() == 3;
    }

    /**
     * @return true if string literal ends with starting quote
     */
    public boolean isTerminated() {
      final String text = myNode.getText();
      return text.length() - myPrefix.length() >= myQuote.length() * 2 && text.endsWith(myQuote);
    }

    /**
     * @return true if given string node contains "u" or "U" prefix
     */
    public boolean isUnicode() {
      return PyStringLiteralUtil.isUnicodePrefix(myPrefix);
    }

    /**
     * @return true if given string node contains "r" or "R" prefix
     */
    public boolean isRaw() {
      return PyStringLiteralUtil.isRawPrefix(myPrefix);
    }

    /**
     * @return true if given string node contains "b" or "B" prefix
     */
    public boolean isBytes() {
      return PyStringLiteralUtil.isBytesPrefix(myPrefix);
    }

    /**
     * @return true if given string node contains "f" or "F" prefix
     */
    public boolean isFormatted() {
      return PyStringLiteralUtil.isFormattedPrefix(myPrefix);
    }

    /**
     * @return true if other string node has the same decorations, i.e. quotes and prefix
     */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      StringNodeInfo info = (StringNodeInfo)o;

      return getQuote().equals(info.getQuote()) &&
             isRaw() == info.isRaw() &&
             isUnicode() == info.isUnicode() &&
             isBytes() == info.isBytes();
    }
  }

  public static final class IterHelper {  // TODO: rename sanely
    private IterHelper() {}

    @Nullable
    public static PsiNamedElement findName(Iterable<? extends PsiNamedElement> it, String name) {
      PsiNamedElement ret = null;
      for (PsiNamedElement elt : it) {
        if (elt != null) {
          // qualified refs don't match by last name, and we're not checking FQNs here
          if (elt instanceof PyQualifiedExpression && ((PyQualifiedExpression)elt).isQualified()) continue;
          if (name.equals(elt.getName())) { // plain name matches
            ret = elt;
            break;
          }
        }
      }
      return ret;
    }
  }
}
