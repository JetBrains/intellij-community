/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.completion.OverwriteEqualsInsertHandler;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.magicLiteral.PyMagicLiteralTools;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PySetuptoolsNamespaceIndex;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.classes.PyDependenciesComparator;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHelper;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.sdk.PythonSdkType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.jetbrains.python.psi.PyFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;

public class PyUtil {
  private PyUtil() {
  }

  @NotNull
  public static <T extends PyElement> T[] getAllChildrenOfType(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    List<T> result = new SmartList<>();
    for (PsiElement child : element.getChildren()) {
      if (instanceOf(child, aClass)) {
        //noinspection unchecked
        result.add((T)child);
      }
      else {
        ContainerUtil.addAll(result, getAllChildrenOfType(child, aClass));
      }
    }
    return ArrayUtil.toObjectArray(result, aClass);
  }

  /**
   * @see PyUtil#flattenedParensAndTuples
   */
  protected static List<PyExpression> unfoldParentheses(PyExpression[] targets, List<PyExpression> receiver,
                                                        boolean unfoldListLiterals, boolean unfoldStarExpressions) {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (PyExpression exp : targets) {
      if (exp instanceof PyParenthesizedExpression) {
        final PyParenthesizedExpression parenExpr = (PyParenthesizedExpression)exp;
        unfoldParentheses(new PyExpression[]{parenExpr.getContainedExpression()}, receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyTupleExpression) {
        final PyTupleExpression tupleExpr = (PyTupleExpression)exp;
        unfoldParentheses(tupleExpr.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyListLiteralExpression && unfoldListLiterals) {
        final PyListLiteralExpression listLiteral = (PyListLiteralExpression)exp;
        unfoldParentheses(listLiteral.getElements(), receiver, true, unfoldStarExpressions);
      }
      else if (exp instanceof PyStarExpression && unfoldStarExpressions) {
        unfoldParentheses(new PyExpression[]{((PyStarExpression)exp).getExpression()}, receiver, unfoldListLiterals, true);
      }
      else if (exp != null) {
        receiver.add(exp);
      }
    }
    return receiver;
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
    return unfoldParentheses(targets, new ArrayList<>(targets.length), false, false);
  }

  @NotNull
  public static List<PyExpression> flattenedParensAndLists(PyExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), true, true);
  }

  @NotNull
  public static List<PyExpression> flattenedParensAndStars(PyExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), false, true);
  }

  // Poor man's filter
  // TODO: move to a saner place

  public static boolean instanceOf(Object obj, Class... possibleClasses) {
    if (obj == null || possibleClasses == null) return false;
    for (Class cls : possibleClasses) {
      if (cls.isInstance(obj)) return true;
    }
    return false;
  }


  /**
   * Produce a reasonable representation of a PSI element, good for debugging.
   *
   * @param elt      element to represent; nulls and invalid nodes are ok.
   * @param cutAtEOL if true, representation stops at nearest EOL inside the element.
   * @return the representation.
   */
  @NotNull
  @NonNls
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
   * Shows an information balloon in a reasonable place at the top right of the window.
   *
   * @param project     our project
   * @param message     the text, HTML markup allowed
   * @param messageType message type, changes the icon and the background.
   */
  // TODO: move to a better place
  public static void showBalloon(Project project, String message, MessageType messageType) {
    // ripped from com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier
    final JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
    if (frame == null) return;
    final JComponent component = frame.getRootPane();
    if (component == null) return;
    final Rectangle rect = component.getVisibleRect();
    final Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
    final RelativePoint point = new RelativePoint(component, p);

    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType.getDefaultIcon(), messageType.getPopupBackground(), null)
      .setShowCallout(false).setCloseButtonEnabled(true)
      .createBalloon().show(point, Balloon.Position.atLeft);
  }

  @NonNls
  /**
   * Returns a quoted string representation, or "null".
   */
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
    if (!FileModificationService.getInstance().preparePsiElementForWrite(parent)) {
      return;
    }
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

  /**
   * Collects superclasses of a class all the way up the inheritance chain. The order is <i>not</i> necessarily the MRO.
   */
  @NotNull
  public static List<PyClass> getAllSuperClasses(@NotNull PyClass pyClass) {
    List<PyClass> superClasses = new ArrayList<>();
    for (PyClass ancestor : pyClass.getAncestorClasses(null)) {
      if (!PyNames.TYPES_INSTANCE_TYPE.equals(ancestor.getQualifiedName())) {
        superClasses.add(ancestor);
      }
    }
    return superClasses;
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
        else if (PyNames.DICT.equals(attr_name)) {
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
    if (owner instanceof PyFunction) {
      final PyFunction method = (PyFunction)owner;
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

  public static boolean isIfNameEqualsMain(PyIfStatement ifStatement) {
    final PyExpression condition = ifStatement.getIfPart().getCondition();
    return isNameEqualsMain(condition);
  }

  private static boolean isNameEqualsMain(PyExpression condition) {
    if (condition instanceof PyParenthesizedExpression) {
      return isNameEqualsMain(((PyParenthesizedExpression)condition).getContainedExpression());
    }
    if (condition instanceof PyBinaryExpression) {
      PyBinaryExpression binaryExpression = (PyBinaryExpression)condition;
      if (binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
        return isNameEqualsMain(binaryExpression.getLeftExpression()) || isNameEqualsMain(binaryExpression.getRightExpression());
      }
      final PyExpression rhs = binaryExpression.getRightExpression();
      return binaryExpression.getOperator() == PyTokenTypes.EQEQ &&
             binaryExpression.getLeftExpression().getText().equals(PyNames.NAME) &&
             rhs != null && rhs.getText().contains("__main__");
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

  public static boolean onSameLine(@NotNull PsiElement e1, @NotNull PsiElement e2) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(e1.getProject());
    final Document document = documentManager.getDocument(e1.getContainingFile());
    if (document == null || document != documentManager.getDocument(e2.getContainingFile())) {
      return false;
    }
    return document.getLineNumber(e1.getTextOffset()) == document.getLineNumber(e2.getTextOffset());
  }

  public static boolean isTopLevel(@NotNull PsiElement element) {
    if (element instanceof StubBasedPsiElement) {
      final StubElement stub = ((StubBasedPsiElement)element).getStub();
      if (stub != null) {
        final StubElement parentStub = stub.getParentStub();
        if (parentStub != null) {
          return parentStub.getPsi() instanceof PsiFile;
        }
      }
    }
    return ScopeUtil.getScopeOwner(element) instanceof PsiFile;
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
        final String shortName = FileUtil.getNameWithoutExtension(file);
        Collections.addAll(filesToDelete, pycache.listFiles(pathname -> {
          if (!FileUtilRt.extensionEquals(pathname.getName(), "pyc")) return false;
          String nameWithMagic = FileUtil.getNameWithoutExtension(pathname);
          return FileUtil.getNameWithoutExtension(nameWithMagic).equals(shortName);
        }));
      }
      FileUtil.asyncDelete(filesToDelete);
    }
  }

  public static String getElementNameWithoutExtension(PsiNamedElement psiNamedElement) {
    return psiNamedElement instanceof PyFile
           ? FileUtil.getNameWithoutExtension(((PyFile)psiNamedElement).getName())
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

  /**
   * Returns string that represents element in string search.
   *
   * @param element element to search
   * @return string that represents element
   */
  @NotNull
  public static String computeElementNameForStringSearch(@NotNull final PsiElement element) {
    if (element instanceof PyFile) {
      return FileUtil.getNameWithoutExtension(((PyFile)element).getName());
    }
    if (element instanceof PsiDirectory) {
      return ((PsiDirectory)element).getName();
    }
    // Magic literals are always represented by their string values
    if ((element instanceof PyStringLiteralExpression) && PyMagicLiteralTools.isMagicLiteral(element)) {
      final String name = ((StringLiteralExpression)element).getStringValue();
      if (name != null) {
        return name;
      }
    }
    if (element instanceof PyElement) {
      final String name = ((PyElement)element).getName();
      if (name != null) {
        return name;
      }
    }
    return element.getNode() != null ? element.getNode().getText() : element.getText();
  }

  public static boolean isOwnScopeComprehension(@NotNull PyComprehensionElement comprehension) {
    final boolean isAtLeast30 = LanguageLevel.forElement(comprehension).isAtLeast(LanguageLevel.PYTHON30);
    final boolean isListComprehension = comprehension instanceof PyListCompExpression;
    return !isListComprehension || isAtLeast30;
  }

  public static boolean hasCustomDecorators(@NotNull PyDecoratable decoratable) {
    return PyKnownDecoratorUtil.hasNonBuiltinDecorator(decoratable, TypeEvalContext.codeInsightFallback(null));
  }

  public static boolean isDecoratedAsAbstract(@NotNull final PyDecoratable decoratable) {
    return PyKnownDecoratorUtil.hasAbstractDecorator(decoratable, TypeEvalContext.codeInsightFallback(null));
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
      if ((resolve == null) ||  checkedElements.contains(resolve) || resolve.equals(currentElement) || !inSameFile(resolve, currentElement)) {
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
  public static List<PsiElement> filterTopPriorityResults(@NotNull ResolveResult[] resolveResults) {
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
  public static <E extends ResolveResult> List<E> filterTopPriorityResults(@NotNull List<E> resolveResults) {
    if (resolveResults.isEmpty()) return Collections.emptyList();

    final int maxRate = getMaxRate(resolveResults);
    return ContainerUtil.filter(resolveResults, resolveResult -> getRate(resolveResult) >= maxRate);
  }

  private static int getMaxRate(@NotNull List<? extends ResolveResult> resolveResults) {
    return resolveResults
      .stream()
      .mapToInt(PyUtil::getRate)
      .max()
      .orElse(Integer.MIN_VALUE);
  }

  private static int getRate(@NotNull ResolveResult resolveResult) {
    return resolveResult instanceof RatedResolveResult ? ((RatedResolveResult)resolveResult).getRate() : 0;
  }

  /**
   * Gets class init method
   *
   * @param pyClass class where to find init
   * @return class init method if any
   */
  @Nullable
  public static PyFunction getInitMethod(@NotNull final PyClass pyClass) {
    return pyClass.findMethodByName(PyNames.INIT, false,  null);
  }

  /**
   * Returns Python language level for a virtual file.
   *
   * @see {@link LanguageLevel#forElement}
   */
  @NotNull
  public static LanguageLevel getLanguageLevelForVirtualFile(@NotNull Project project,
                                                             @NotNull VirtualFile virtualFile) {
    if (virtualFile instanceof VirtualFileWindow) {
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
    }

    // Most of the cases should be handled by this one, PyLanguageLevelPusher pushes folders only
    final VirtualFile folder = virtualFile.getParent();
    if (folder != null) {
      final LanguageLevel folderLevel = folder.getUserData(LanguageLevel.KEY);
      if (folderLevel != null) {
        return folderLevel;
      }
      final LanguageLevel fileLevel = PythonLanguageLevelPusher.getFileLanguageLevel(project, virtualFile);
      if (fileLevel != null) {
        return fileLevel;
      }
    }
    else {
      // However this allows us to setup language level per file manually
      // in case when it is LightVirtualFile
      final LanguageLevel level = virtualFile.getUserData(LanguageLevel.KEY);
      if (level != null) return level;

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final LanguageLevel languageLevel = LanguageLevel.FORCE_LANGUAGE_LEVEL;
        if (languageLevel != null) {
          return languageLevel;
        }
      }
    }
    return guessLanguageLevelWithCaching(project);
  }

  public static void invalidateLanguageLevelCache(@NotNull Project project) {
    project.putUserData(PythonLanguageLevelPusher.PYTHON_LANGUAGE_LEVEL, null);
  }

  @NotNull
  public static LanguageLevel guessLanguageLevelWithCaching(@NotNull Project project) {
    LanguageLevel languageLevel = project.getUserData(PythonLanguageLevelPusher.PYTHON_LANGUAGE_LEVEL);
    if (languageLevel == null) {
      languageLevel = guessLanguageLevel(project);
      project.putUserData(PythonLanguageLevelPusher.PYTHON_LANGUAGE_LEVEL, languageLevel);
    }

    return languageLevel;
  }

  @NotNull
  public static LanguageLevel guessLanguageLevel(@NotNull Project project) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    if (moduleManager != null) {
      LanguageLevel maxLevel = null;
      for (Module projectModule : moduleManager.getModules()) {
        final Sdk sdk = PythonSdkType.findPythonSdk(projectModule);
        if (sdk != null) {
          final LanguageLevel level = PythonSdkType.getLanguageLevelForSdk(sdk);
          if (maxLevel == null || maxLevel.isOlderThan(level)) {
            maxLevel = level;
          }
        }
      }
      if (maxLevel != null) {
        return maxLevel;
      }
    }
    return LanguageLevel.getDefault();
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
  @SuppressWarnings("unchecked")
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
  public static <T> List<T> asList(@Nullable final Collection<?> expression, @NotNull final Class<T> elementClass) {
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
   * Force re-highlighting in all open editors that belong to specified project.
   */
  public static void rehighlightOpenEditors(final @NotNull Project project) {
    ApplicationManager.getApplication().runWriteAction(() -> {

      for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
        if (editor instanceof EditorEx && editor.getProject() == project) {
          final VirtualFile vFile = ((EditorEx)editor).getVirtualFile();
          if (vFile != null) {
            final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, vFile);
            ((EditorEx)editor).setHighlighter(highlighter);
          }
        }
      }
    });
  }

  /**
   * Calculates and caches value based on param. Think about it as about map with param as key which flushes on each psi modification.
   *
   * For nullable function see {@link #getNullableParameterizedCachedValue(PsiElement, Object, NullableFunction)}.
   *
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
  public static <T, P> T getParameterizedCachedValue(@NotNull PsiElement element, @Nullable P param, @NotNull NotNullFunction<P, T> f) {
    final T result = getNullableParameterizedCachedValue(element, param, f);
    assert result != null;
    return result;
  }

  /**
   * Same as {@link #getParameterizedCachedValue(PsiElement, Object, NotNullFunction)} but allows nulls.
   */
  @Nullable
  public static <T, P> T getNullableParameterizedCachedValue(@NotNull PsiElement element,
                                                             @Nullable P param,
                                                             @NotNull NullableFunction<P, T> f) {
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
   * @see ApplicationImpl#runProcessWithProgressSynchronously(Runnable, String, boolean, Project, JComponent, String)
   */
  public static void runWithProgress(@Nullable Project project, @Nls(capitalization = Nls.Capitalization.Title) @NotNull String title,
                                     boolean modal, boolean canBeCancelled, @NotNull final Consumer<ProgressIndicator> function) {
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
   * @param runnable code to call
   */
  public static void verboseOnly(@NotNull final Runnable runnable) {
    if (System.getenv().get("_PYCHARM_VERBOSE_MODE") != null) {
      runnable.run();
    }
  }

  /**
   * Returns the line comment that immediately precedes statement list of the given compound statement. Python parser ensures
   * that it follows the statement header, i.e. it's directly after the colon, not on its own line.
   */
  @Nullable
  public static PsiComment getCommentOnHeaderLine(@NotNull PyStatementListContainer container) {
    final PyStatementList statementList = container.getStatementList();
    return as(PyPsiUtils.getPrevNonWhitespaceSibling(statementList), PsiComment.class);
  }

  public static boolean isPy2ReservedWord(@NotNull PyReferenceExpression node) {
    if (LanguageLevel.forElement(node).isOlderThan(LanguageLevel.PYTHON30)) {
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
  public static void updateDocumentUnblockedAndCommitted(@NotNull PsiElement anchor, @NotNull Consumer<Document> consumer) {
    updateDocumentUnblockedAndCommitted(anchor, document -> {
      consumer.consume(document);
      return null;
    });
  }

  @Nullable
  public static <T> T updateDocumentUnblockedAndCommitted(@NotNull PsiElement anchor, @NotNull Function<Document, T> func) {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(anchor.getProject());
    final Document document = manager.getDocument(anchor.getContainingFile());
    if (document != null) {
      manager.doPostponedOperationsAndUnblockDocument(document);
      try {
        return func.fun(document);
      }
      finally {
        manager.commitDocument(document);
      }
    }
    return null;
  }

  @Nullable
  public static PyType getReturnTypeToAnalyzeAsCallType(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    if (isInit(function)) {
      final PyClass cls = function.getContainingClass();
      if (cls != null) {
        for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
          final PyType providedClassType = provider.getGenericType(cls, context);
          if (providedClassType != null) {
            return providedClassType;
          }
        }

        final PyInstantiableType classType = as(context.getType(cls), PyInstantiableType.class);
        if (classType != null) {
          return classType.toInstance();
        }
      }
    }

    return context.getReturnType(function);
  }

  /**
   * Create a new expressions fragment from the given text, setting the specified element as its context,
   * and return the contained expression of the first expression statement in it.
   *
   * @param expressionText text of the expression
   * @param context        context element used to resolve symbols in the expression
   * @return instance of {@link PyExpression} as described
   *
   * @see PyExpressionCodeFragment
   */
  @Nullable
  public static PyExpression createExpressionFromFragment(@NotNull String expressionText, @NotNull PsiElement context) {
    final PyExpressionCodeFragmentImpl codeFragment = new PyExpressionCodeFragmentImpl(context.getProject(), "dummy.py", expressionText, false);
    codeFragment.setContext(context);
    final PyExpressionStatement statement = as(codeFragment.getFirstChild(), PyExpressionStatement.class);
    return statement != null ? statement.getExpression() : null;
  }

  public static class KnownDecoratorProviderHolder {
    public static final PyKnownDecoratorProvider[] KNOWN_DECORATOR_PROVIDERS = Extensions.getExtensions(PyKnownDecoratorProvider.EP_NAME);

    private KnownDecoratorProviderHolder() {
    }
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
    if (target instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)target;
      final PsiFile initStub = dir.findFile(PyNames.INIT_DOT_PYI);
      if (initStub != null) {
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

  /**
   * If directory is a PsiDirectory, that is also a valid Python package, return PsiFile that points to __init__.py,
   * if such file exists, or directory itself (i.e. namespace package). Otherwise, return {@code null}.
   * Unlike {@link #turnDirIntoInit(PsiElement)} this function handles namespace packages and
   * accepts only PsiDirectories as target.
   *
   * @param directory directory to check
   * @param anchor optional PSI element to determine language level as for {@link #isPackage(PsiDirectory, PsiElement)}
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
   * @param target PSI element to check
   * @return PsiDirectory or target unchanged
   */
  @Contract("null -> null; !null -> !null")
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
   * @param directory PSI directory to check
   * @param checkSetupToolsPackages whether setuptools namespace packages should be considered as well
   * @param anchor    optional anchor element to determine language level
   * @return whether given directory is Python package
   *
   * @see PyNames#isIdentifier(String)
   */
  public static boolean isPackage(@NotNull PsiDirectory directory, boolean checkSetupToolsPackages, @Nullable PsiElement anchor) {
    for (PyCustomPackageIdentifier customPackageIdentifier : PyCustomPackageIdentifier.EP_NAME.getExtensions()) {
      if (customPackageIdentifier.isPackage(directory)) {
        return true;
      }
    }
    if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
      return true;
    }
    final LanguageLevel level = anchor != null ?
                                LanguageLevel.forElement(anchor) :
                                getLanguageLevelForVirtualFile(directory.getProject(), directory.getVirtualFile());
    if (level.isAtLeast(LanguageLevel.PYTHON33)) {
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
   * @return 0 if no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  public static int getInitialUnderscores(String name) {
    if (name == null) {
      return 0;
    }
    int underscores = 0;
    if (name.startsWith("__")) {
      underscores = 2;
    }
    else if (name.startsWith("_")) underscores = 1;
    return underscores;
  }

  /**
   * @param name
   * @return true iff the name looks like a class-private one, starting with two underscores but not ending with two underscores.
   */
  public static boolean isClassPrivateName(@NotNull String name) {
    return name.startsWith("__") && !name.endsWith("__");
  }

  public static boolean isSpecialName(@NotNull String name) {
    return name.length() > 4 && name.startsWith("__") && name.endsWith("__");
  }

  /**
   * Constructs new lookup element for completion of keyword argument with equals sign appended.
   *
   * @param name    name of the parameter
   * @param project project instance to check code style settings and surround equals sign with spaces if necessary
   * @return lookup element
   */
  @NotNull
  public static LookupElement createNamedParameterLookup(@NotNull String name, @Nullable Project project) {
    final String suffix;
    if (CodeStyleSettingsManager.getSettings(project).getCustomSettings(PyCodeStyleSettings.class).SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT) {
      suffix = " = ";
    }
    else {
      suffix = "=";
    }
    LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(name + suffix).withIcon(PlatformIcons.PARAMETER_ICON);
    lookupElementBuilder = lookupElementBuilder.withInsertHandler(OverwriteEqualsInsertHandler.INSTANCE);
    return PrioritizedLookupElement.withGrouping(lookupElementBuilder, 1);
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
  public static List<String> getStringListFromTargetExpression(PyTargetExpression attr) {
    return strListValue(attr.findAssignedValue());
  }

  @Nullable
  public static List<String> strListValue(PyExpression value) {
    while (value instanceof PyParenthesizedExpression) {
      value = ((PyParenthesizedExpression)value).getContainedExpression();
    }
    if (value instanceof PySequenceExpression) {
      final PyExpression[] elements = ((PySequenceExpression)value).getElements();
      List<String> result = new ArrayList<>(elements.length);
      for (PyExpression element : elements) {
        if (!(element instanceof PyStringLiteralExpression)) {
          return null;
        }
        result.add(((PyStringLiteralExpression)element).getStringValue());
      }
      return result;
    }
    return null;
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

  public static class MethodFlags {

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

  @NotNull
  public static PyFile getOrCreateFile(String path, Project project) {
    final VirtualFile vfile = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
    final PsiFile psi;
    if (vfile == null) {
      final File file = new File(path);
      try {
        final VirtualFile baseDir = project.getBaseDir();
        final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(project);
        final FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
        final Properties properties = fileTemplateManager.getDefaultProperties();
        properties.setProperty("NAME", FileUtil.getNameWithoutExtension(file.getName()));
        final String content = (template != null) ? template.getText(properties) : null;
        psi = PyExtractSuperclassHelper.placeFile(project,
                                                  StringUtil.notNullize(
                                                    file.getParent(),
                                                    baseDir != null ? baseDir
                                                      .getPath() : "."
                                                  ),
                                                  file.getName(),
                                                  content
        );
      }
      catch (IOException e) {
        throw new IncorrectOperationException(String.format("Cannot create file '%s'", path), (Throwable)e);
      }
    }
    else {
      psi = PsiManager.getInstance(project).findFile(vfile);
    }
    if (!(psi instanceof PyFile)) {
      throw new IncorrectOperationException(PyBundle.message(
        "refactoring.move.module.members.error.cannot.place.elements.into.nonpython.file"));
    }
    return (PyFile)psi;
  }

  @Nullable
  public static PsiElement findPrevAtOffset(PsiFile psiFile, int caretOffset, Class... toSkip) {
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
    while (caretOffset >= lineStartOffset && instanceOf(element, toSkip));
    return instanceOf(element, toSkip) ? null : element;
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
  public static PsiElement findNextAtOffset(@NotNull final PsiFile psiFile, int caretOffset, Class... toSkip) {
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
    while (caretOffset < lineEndOffset && instanceOf(element, toSkip)) {
      caretOffset++;
      element = psiFile.findElementAt(caretOffset);
    }
    return instanceOf(element, toSkip) ? null : element;
  }

  /**
   * Adds element to statement list to the correct place according to its dependencies.
   *
   * @param element       to insert
   * @param statementList where element should be inserted
   * @return inserted element
   */
  public static <T extends PyElement> T addElementToStatementList(@NotNull final T element,
                                                                  @NotNull final PyStatementList statementList) {
    PsiElement before = null;
    PsiElement after = null;
    for (final PyStatement statement : statementList.getStatements()) {
      if (PyDependenciesComparator.depends(element, statement)) {
        after = statement;
      }
      else if (PyDependenciesComparator.depends(statement, element)) {
        before = statement;
      }
    }
    final PsiElement result;
    if (after != null) {

      result = statementList.addAfter(element, after);
    }
    else if (before != null) {
      result = statementList.addBefore(element, before);
    }
    else {
      result = addElementToStatementList(element, statementList, true);
    }
    @SuppressWarnings("unchecked") // Inserted element can't have different type
    final T resultCasted = (T)result;
    return resultCasted;
  }


  /**
   * Inserts specified element into the statement list either at the beginning or at its end. If new element is going to be
   * inserted at the beginning, any preceding docstrings and/or calls to super methods will be skipped.
   * Moreover if statement list previously didn't contain any statements, explicit new line and indentation will be inserted in
   * front of it.
   *
   * @param element        element to insert
   * @param statementList  statement list
   * @param toTheBeginning whether to insert element at the beginning or at the end of the statement list
   * @return actually inserted element as for {@link PsiElement#add(PsiElement)}
   */
  @NotNull
  public static PsiElement addElementToStatementList(@NotNull PsiElement element,
                                                     @NotNull PyStatementList statementList,
                                                     boolean toTheBeginning) {
    final PsiElement prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(statementList);
    // If statement list is on the same line as previous element (supposedly colon), move its only statement on the next line
    if (prevElem != null && onSameLine(statementList, prevElem)) {
      final PsiDocumentManager manager = PsiDocumentManager.getInstance(statementList.getProject());
      final Document document = manager.getDocument(statementList.getContainingFile());
      if (document != null) {
        final PyStatementListContainer container = (PyStatementListContainer)statementList.getParent();
        manager.doPostponedOperationsAndUnblockDocument(document);
        final String indentation = "\n" + PyIndentUtil.getElementIndent(statementList);
        // If statement list was empty initially, we need to add some anchor statement ("pass"), so that preceding new line was not
        // parsed as following entire StatementListContainer (e.g. function). It's going to be replaced anyway.
        final String text = statementList.getStatements().length == 0 ? indentation + PyNames.PASS : indentation;
        document.insertString(statementList.getTextRange().getStartOffset(), text);
        manager.commitDocument(document);
        statementList = container.getStatementList();
      }
    }
    final PsiElement firstChild = statementList.getFirstChild();
    if (firstChild == statementList.getLastChild() && firstChild instanceof PyPassStatement) {
      element = firstChild.replace(element);
    }
    else {
      final PyStatement[] statements = statementList.getStatements();
      if (toTheBeginning && statements.length > 0) {
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(statementList, PyDocStringOwner.class);
        PyStatement anchor = statements[0];
        if (docStringOwner != null && anchor instanceof PyExpressionStatement &&
            ((PyExpressionStatement)anchor).getExpression() == docStringOwner.getDocStringExpression()) {
          final PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
          if (next == null) {
            return statementList.addAfter(element, anchor);
          }
          anchor = next;
        }
        while (anchor instanceof PyExpressionStatement) {
          final PyExpression expression = ((PyExpressionStatement)anchor).getExpression();
          if (expression instanceof PyCallExpression) {
            final PyExpression callee = ((PyCallExpression)expression).getCallee();
            if ((isSuperCall((PyCallExpression)expression) || (callee != null && PyNames.INIT.equals(callee.getName())))) {
              final PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
              if (next == null) {
                return statementList.addAfter(element, anchor);
              }
              anchor = next;
              continue;
            }
          }
          break;
        }
        element = statementList.addBefore(element, anchor);
      }
      else {
        element = statementList.add(element);
      }
    }
    return element;
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
    return requiredCount <= otherRequiredCount && parameters.size() >= otherParameters.size() && optionalCount >= otherOptionalCount;
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
    return parameters.size() - optionalParametersCount(parameters) - specialParametersCount(callable, parameters);
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

  private static boolean isFirstParameterSpecial(@NotNull PyCallable callable, @NotNull List<PyCallableParameter> parameters) {
    final PyFunction method = callable.asMethod();
    if (method != null) {
      return PyNames.NEW.equals(method.getName()) || method.getModifier() != STATICMETHOD;
    } else {
      final PyCallableParameter first = ContainerUtil.getFirstItem(parameters);
      return first != null && PyNames.CANONICAL_SELF.equals(first.getName());
    }
  }

  public static boolean isInit(@NotNull final PyFunction function) {
    return PyNames.INIT.equals(function.getName());
  }

  /**
   * Filters out {@link PyMemberInfo}
   * that should not be displayed in this refactoring (like object)
   *
   * @param pyMemberInfos collection to sort
   * @return sorted collection
   */
  @NotNull
  public static Collection<PyMemberInfo<PyElement>> filterOutObject(@NotNull final Collection<PyMemberInfo<PyElement>> pyMemberInfos) {
    return Collections2.filter(pyMemberInfos, new ObjectPredicate(false));
  }

  public static boolean isStarImportableFrom(@NotNull String name, @NotNull PyFile file) {
    final List<String> dunderAll = file.getDunderAll();
    return dunderAll != null ? dunderAll.contains(name) : !name.startsWith("_");
  }

  /**
   * Filters only PyClass object (new class)
   */
  public static class ObjectPredicate extends NotNullPredicate<PyMemberInfo<PyElement>> {
    private final boolean myAllowObjects;

    /**
     * @param allowObjects allows only objects if true. Allows all but objects otherwise.
     */
    public ObjectPredicate(final boolean allowObjects) {
      myAllowObjects = allowObjects;
    }

    @Override
    public boolean applyNotNull(@NotNull final PyMemberInfo<PyElement> input) {
      return myAllowObjects == isObject(input);
    }

    private static boolean isObject(@NotNull final PyMemberInfo<PyElement> classMemberInfo) {
      final PyElement element = classMemberInfo.getMember();
      return (element instanceof PyClass) && PyNames.OBJECT.equals(element.getName());
    }
  }

  /**
   * Sometimes you do not know real FQN of some class, but you know class name and its package.
   * I.e. {@code django.apps.conf.AppConfig} is not documented, but you know
   * {@code AppConfig} and {@code django} package.
   *
   * @param symbol element to check (class or function)
   * @param expectedPackage package like "django"
   * @param expectedName expected name (i.e. AppConfig)
   * @return true if element in package
   */
  public static boolean isSymbolInPackage(@NotNull final PyQualifiedNameOwner symbol,
                                          @NotNull final String expectedPackage,
                                          @NotNull final String expectedName) {
    final String qualifiedNameString = symbol.getQualifiedName();
    if (qualifiedNameString == null) {
      return false;
    }
    final QualifiedName qualifiedName = QualifiedName.fromDottedString(qualifiedNameString);
    final String aPackage = qualifiedName.getFirstComponent();
    if (!(expectedPackage.equals(aPackage))) {
      return false;
    }
    final String symbolName = qualifiedName.getLastComponent();
    return expectedName.equals(symbolName);
  }

  public static boolean isObjectClass(@NotNull PyClass cls) {
    final String name = cls.getQualifiedName();
    return PyNames.OBJECT.equals(name) || PyNames.TYPES_INSTANCE_TYPE.equals(name);
  }

  public static boolean isInScratchFile(@NotNull PsiElement element) {
    return ScratchFileService.isInScratchRoot(PsiUtilCore.getVirtualFile(element));
  }

  @Nullable
  public static PyType getReturnTypeOfMember(@NotNull PyType type,
                                             @NotNull String memberName,
                                             @Nullable PyExpression location,
                                             @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
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
      final List<PyType> types = new ArrayList<>();

      for (PyType pyType : ((PyUnionType)type).getMembers()) {
        final PyType returnType = getReturnType(pyType, context);

        if (returnType != null) {
          types.add(returnType);
        }
      }

      return PyUnionType.union(types);
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
    if (stmt instanceof PyExpressionStatement) {
      final PyExpression expr = ((PyExpressionStatement)stmt).getExpression();
      if (expr instanceof PyStringLiteralExpression) {
        return true;
      }
    }
    return false;
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
      if (!PyTokenTypes.STRING_NODES.contains(node.getElementType())) {
        throw new IllegalArgumentException("Node must be valid Python string literal token, but " + node.getElementType() + " was given");
      }
      myNode = node;
      final String nodeText = node.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
      myPrefix = nodeText.substring(0, prefixLength);
      myContentRange = PyStringLiteralExpressionImpl.getNodeTextRange(nodeText);
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

  public static class IterHelper {  // TODO: rename sanely
    private IterHelper() {}
    @Nullable
    public static PsiNamedElement findName(Iterable<PsiNamedElement> it, String name) {
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
