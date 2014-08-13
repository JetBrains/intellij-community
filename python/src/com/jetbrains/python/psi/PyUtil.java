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
package com.jetbrains.python.psi;

import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.completion.OverwriteEqualsInsertHandler;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.magicLiteral.PyMagicLiteralTools;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.classes.PyDependenciesComparator;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHelper;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.jetbrains.python.psi.PyFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;

public class PyUtil {

  private static final Object[] EMPTY_OBJECTS = new Object[0];

  private PyUtil() {
  }

  public static ASTNode getNextNonWhitespace(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreeNext();
    }
    while (isWhitespace(node));
    return node;
  }

  public static ASTNode getPreviousNonWhitespace(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreePrev();
    }
    while (isWhitespace(node));
    return node;
  }

  private static boolean isWhitespace(ASTNode node) {
    return node != null && node.getElementType().equals(TokenType.WHITE_SPACE);
  }

  @Nullable
  public static PsiElement getFirstNonCommentAfter(PsiElement start) {
    PsiElement seeker = start;
    while (seeker instanceof PsiWhiteSpace || seeker instanceof PsiComment) seeker = seeker.getNextSibling();
    return seeker;
  }

  @Nullable
  public static PsiElement getFirstNonCommentBefore(PsiElement start) {
    PsiElement seeker = start;
    while (seeker instanceof PsiWhiteSpace || seeker instanceof PsiComment) {
      seeker = seeker.getPrevSibling();
    }
    return seeker;
  }

  @NotNull
  public static <T extends PyElement> T[] getAllChildrenOfType(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    List<T> result = new SmartList<T>();
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
  protected static List<PyExpression> _unfoldParenExprs(PyExpression[] targets, List<PyExpression> receiver,
                                                        boolean unfoldListLiterals, boolean unfoldStarExpressions) {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (PyExpression exp : targets) {
      if (exp instanceof PyParenthesizedExpression) {
        final PyParenthesizedExpression parex = (PyParenthesizedExpression)exp;
        _unfoldParenExprs(new PyExpression[]{parex.getContainedExpression()}, receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyTupleExpression) {
        final PyTupleExpression tupex = (PyTupleExpression)exp;
        _unfoldParenExprs(tupex.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyListLiteralExpression && unfoldListLiterals) {
        final PyListLiteralExpression listLiteral = (PyListLiteralExpression)exp;
        _unfoldParenExprs(listLiteral.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyStarExpression && unfoldStarExpressions) {
        _unfoldParenExprs(new PyExpression[]{((PyStarExpression)exp).getExpression()}, receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp != null) {
        receiver.add(exp);
      }
    }
    return receiver;
  }

  // Poor man's catamorhpism :)

  /**
   * Flattens the representation of every element in targets, and puts all results together.
   * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
   * Typical usage: <code>flattenedParensAndTuples(some_tuple.getExpressions())</code>.
   *
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  @NotNull
  public static List<PyExpression> flattenedParensAndTuples(PyExpression... targets) {
    return _unfoldParenExprs(targets, new ArrayList<PyExpression>(targets.length), false, false);
  }

  @NotNull
  public static List<PyExpression> flattenedParensAndLists(PyExpression... targets) {
    return _unfoldParenExprs(targets, new ArrayList<PyExpression>(targets.length), true, true);
  }

  @NotNull
  public static List<PyExpression> flattenedParensAndStars(PyExpression... targets) {
    return _unfoldParenExprs(targets, new ArrayList<PyExpression>(targets.length), false, true);
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
   * Removes an element from a a comma-separated list in a PSI tree. E.g. can turn "foo, bar, baz" into "foo, baz",
   * removing commas as needed. It removes a trailing comma if it results from deletion.
   *
   * @param item what to remove. Its parent is considered the list, and commas must be its peers.
   */
  public static void removeListNode(PsiElement item) {
    PsiElement parent = item.getParent();
    if (!FileModificationService.getInstance().preparePsiElementForWrite(parent)) {
      return;
    }
    // remove comma after the item
    ASTNode binder = parent.getNode();
    assert binder != null : "parent node is null, ensureWritable() lied";
    boolean got_comma_after = eraseWhitespaceAndComma(binder, item, false);
    if (!got_comma_after) {
      // there was not a comma after the item; remove a comma before the item
      eraseWhitespaceAndComma(binder, item, true);
    }
    // finally
    item.delete();
  }

  /**
   * Removes whitespace and comma(s) that are siblings of the item, up to the first non-whitespace and non-comma.
   *
   * @param parent_node node of the parent of item.
   * @param item        starting point; we erase left or right of it, but not it.
   * @param backwards   true to erase prev siblings, false to erase next siblings.
   * @return true       if a comma was found and removed.
   */
  public static boolean eraseWhitespaceAndComma(ASTNode parent_node, PsiElement item, boolean backwards) {
    // we operate on AST, PSI won't let us delete whitespace easily.
    boolean is_comma;
    boolean got_comma = false;
    ASTNode current = item.getNode();
    ASTNode candidate;
    boolean have_skipped_the_item = false;
    while (current != null) {
      candidate = current;
      current = backwards ? current.getTreePrev() : current.getTreeNext();
      if (have_skipped_the_item) {
        is_comma = ",".equals(candidate.getText());
        got_comma |= is_comma;
        if (is_comma || candidate.getElementType() == TokenType.WHITE_SPACE) {
          parent_node.removeChild(candidate);
        }
        else {
          break;
        }
      }
      else {
        have_skipped_the_item = true;
      }
    }
    return got_comma;
  }

  /**
   * Collects superclasses of a class all the way up the inheritance chain. The order is <i>not</i> necessarily the MRO.
   */
  @NotNull
  public static List<PyClass> getAllSuperClasses(@NotNull PyClass pyClass) {
    List<PyClass> superClasses = new ArrayList<PyClass>();
    for (PyClass ancestor : pyClass.getAncestorClasses()) {
      if (!PyNames.FAKE_OLD_BASE.equals(ancestor.getName())) {
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
   * Searhes for a method wrapping given element.
   *
   * @param start element presumably inside a method
   * @param deep  if true, allow 'start' to be inside functions nested in a method; else, 'start' must be directly inside a method.
   * @return if not 'deep', [0] is the method and [1] is the class; if 'deep', first several elements may be the nested functions,
   * the last but one is the method, and the last is the class.
   */
  @Nullable
  public static List<PsiElement> searchForWrappingMethod(PsiElement start, boolean deep) {
    PsiElement seeker = start;
    List<PsiElement> ret = new ArrayList<PsiElement>(2);
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
    if (pyFilePath.endsWith(".py")) {
      List<File> filesToDelete = new ArrayList<File>();
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
        Collections.addAll(filesToDelete, pycache.listFiles(new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            if (!FileUtilRt.extensionEquals(pathname.getName(), "pyc")) return false;
            String nameWithMagic = FileUtil.getNameWithoutExtension(pathname);
            return FileUtil.getNameWithoutExtension(nameWithMagic).equals(shortName);
          }
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

  public static boolean deleteParameter(@NotNull final PyFunction problemFunction, int index) {
    final PyParameterList parameterList = problemFunction.getParameterList();
    final PyParameter[] parameters = parameterList.getParameters();
    if (parameters.length <= 0) return false;

    PsiElement first = parameters[index];
    PsiElement last = parameters.length > index + 1 ? parameters[index + 1] : parameterList.getLastChild();
    PsiElement prevSibling = last.getPrevSibling() != null ? last.getPrevSibling() : parameters[index];

    parameterList.deleteChildRange(first, prevSibling);
    return true;
  }

  public static void removeQualifier(@NotNull final PyReferenceExpression element) {
    final PyExpression qualifier = element.getQualifier();
    if (qualifier == null) return;

    if (qualifier instanceof PyCallExpression) {
      final StringBuilder newElement = new StringBuilder(element.getLastChild().getText());
      final PyExpression callee = ((PyCallExpression)qualifier).getCallee();
      if (callee instanceof PyReferenceExpression) {
        final PyExpression calleeQualifier = ((PyReferenceExpression)callee).getQualifier();
        if (calleeQualifier != null) {
          newElement.insert(0, calleeQualifier.getText() + ".");
        }
      }
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
      final PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), newElement.toString());
      element.replace(expression);
    }
    else {
      final PsiElement dot = qualifier.getNextSibling();
      if (dot != null) dot.delete();
      qualifier.delete();
    }
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
    return element.getNode().getText();
  }

  public static boolean isOwnScopeComprehension(@NotNull PyComprehensionElement comprehension) {
    final boolean isAtLeast30 = LanguageLevel.forElement(comprehension).isAtLeast(LanguageLevel.PYTHON30);
    final boolean isListComprehension = comprehension instanceof PyListCompExpression;
    return !isListComprehension || isAtLeast30;
  }

  public static boolean hasCustomDecorators(@NotNull PyDecoratable decoratable) {
    PyDecoratorList decoratorList = decoratable.getDecoratorList();
    if (decoratorList == null) {
      return false;
    }
    for (PyDecorator decorator : decoratorList.getDecorators()) {
      QualifiedName name = decorator.getQualifiedName();
      if (name == null || (!PyNames.CLASSMETHOD.equals(name.toString()) && !PyNames.STATICMETHOD.equals(name.toString()))) {
        return true;
      }
    }
    return false;
  }

  public static boolean isDecoratedAsAbstract(@NotNull final PyDecoratable decoratable) {
    final PyDecoratorList decoratorList = decoratable.getDecoratorList();
    if (decoratorList == null) {
      return false;
    }
    for (PyDecorator decorator : decoratorList.getDecorators()) {
      final QualifiedName qualifiedName = decorator.getQualifiedName();
      if (qualifiedName != null) {
        final String name = qualifiedName.toString();
        if (name.endsWith(PyNames.ABSTRACTMETHOD) || name.endsWith(PyNames.ABSTRACTPROPERTY)) {
          return true;
        }
      }
    }
    return false;
  }

  public static ASTNode createNewName(PyElement element, String name) {
    return PyElementGenerator.getInstance(element.getProject()).createNameIdentifier(name, LanguageLevel.forElement(element));
  }

  /**
   * Finds element declaration by resolving its references top the top but not further than file (to prevent unstubing)
   *
   * @param element element to resolve
   * @return its declaration
   */
  @NotNull
  public static PsiElement resolveToTheTop(@NotNull final PsiElement elementToResolve) {
    PsiElement currentElement = elementToResolve;
    while (true) {
      final PsiReference reference = currentElement.getReference();
      if (reference == null) {
        break;
      }
      final PsiElement resolve = reference.resolve();
      if ((resolve == null) || resolve.equals(currentElement) || !inSameFile(resolve, currentElement)) {
        break;
      }
      currentElement = resolve;
    }
    return currentElement;
  }

  /**
   * Gets class init method
   *
   * @param pyClass class where to find init
   * @return class init method if any
   */
  @Nullable
  public static PyFunction getInitMethod(@NotNull final PyClass pyClass) {
    return pyClass.findMethodByName(PyNames.INIT, false);
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
      LanguageLevel level = folder.getUserData(LanguageLevel.KEY);
      if (level == null) level = PythonLanguageLevelPusher.getFileLanguageLevel(project, virtualFile);
      if (level != null) return level;
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
    return guessLanguageLevel(project);
  }

  private static LanguageLevel guessLanguageLevel(@NotNull Project project) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    if (moduleManager != null) {
      for (Module projectModule : moduleManager.getModules()) {
        final Sdk sdk = PythonSdkType.findPythonSdk(projectModule);
        if (sdk != null) {
          return PythonSdkType.getLanguageLevelForSdk(sdk);
        }
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
    if (expression == null) {
      return null;
    }
    if (clazz.isAssignableFrom(expression.getClass())) {
      return (T)expression;
    }
    return null;
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
   * @param expression expression of collection type
   * @param elementClass expected element type
   * @param <T>  expected element type
   * @return list of elements of expected element type
   */
  @NotNull
  public static <T> List<T> asList(@Nullable final Collection<?> expression, @NotNull final Class<T> elementClass) {
    if ((expression == null) || expression.isEmpty()) {
      return Collections.emptyList();
    }
    final List<T> result = new ArrayList<T>();
    for (final Object element : expression) {
      final T toAdd = as(element, elementClass);
      if (toAdd != null) {
        result.add(toAdd);
      }
    }
    return result;
  }

  public static class KnownDecoratorProviderHolder {
    public static PyKnownDecoratorProvider[] KNOWN_DECORATOR_PROVIDERS = Extensions.getExtensions(PyKnownDecoratorProvider.EP_NAME);

    private KnownDecoratorProviderHolder() {
    }
  }

  /**
   * Returns child element in the psi tree
   *
   * @param filter  Types of expected child
   * @param number  number
   * @param element tree parent node
   * @return PsiElement - child psiElement
   */
  @Nullable
  public static PsiElement getChildByFilter(@NotNull final PsiElement element, final @NotNull TokenSet filter, final int number) {
    final ASTNode node = element.getNode();
    if (node != null) {
      final ASTNode[] children = node.getChildren(filter);
      return (0 <= number && number < children.length) ? children[number].getPsi() : null;
    }
    return null;
  }

  /**
   * Returns first child psi element with specified element type or {@code null} if no such element exists.
   * Semantically it's the same as {@code getChildByFilter(element, TokenSet.create(type), 0)}.
   *
   * @param element tree parent node
   * @param type    element type expected
   * @return child element described
   */
  @Nullable
  public static PsiElement getFirstChildOfType(@NotNull final PsiElement element, @NotNull PyElementType type) {
    final ASTNode child = element.getNode().findChildByType(type);
    return child != null ? child.getPsi() : null;
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
  public static PsiElement turnDirIntoInit(PsiElement target) {
    if (target instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)target;
      final PsiFile file = dir.findFile(PyNames.INIT_DOT_PY);
      if (file != null) {
        return file; // ResolveImportUtil will extract directory part as needed, everyone else are better off with a file.
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
  public static PsiElement turnInitIntoDir(PsiElement target) {
    if (target instanceof PyFile && isPackage((PsiFile)target)) {
      return ((PsiFile)target).getContainingDirectory();
    }
    return target;
  }

  public static boolean isPackage(@NotNull PsiDirectory directory, @Nullable PsiElement anchor) {
    if (turnDirIntoInit(directory) != null) {
      return true;
    }
    final LanguageLevel level = anchor != null ?
                                LanguageLevel.forElement(anchor) :
                                getLanguageLevelForVirtualFile(directory.getProject(), directory.getVirtualFile());
    if (level.isAtLeast(LanguageLevel.PYTHON33)) {
      return true;
    }
    return hasNamespacePackageFile(directory);
  }

  public static boolean isPackage(@NotNull PsiFile file) {
    return PyNames.INIT_DOT_PY.equals(file.getName());
  }

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

  private static boolean hasNamespacePackageFile(@NotNull PsiDirectory directory) {
    final String name = directory.getName().toLowerCase();
    final PsiDirectory parent = directory.getParent();
    if (parent != null) {
      for (PsiFile file : parent.getFiles()) {
        final String filename = file.getName().toLowerCase();
        if (filename.startsWith(name) && filename.endsWith("-nspkg.pth")) {
          return true;
        }
      }
    }
    return false;
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
   * Tries to find nearest parent that conceals names defined inside it. Such elements are 'class' and 'def':
   * anything defined within it does not seep to the namespace below them, but is concealed within.
   *
   * @param elt starting point of search.
   * @return 'class' or 'def' element, or null if not found.
   * @deprecated Use {@link ScopeUtil#getScopeOwner} instead.
   */
  @Deprecated
  @Nullable
  public static PsiElement getConcealingParent(PsiElement elt) {
    if (elt == null || elt instanceof PsiFile) {
      return null;
    }
    PsiElement parent = PsiTreeUtil.getStubOrPsiParent(elt);
    boolean jump_over = false;
    while (parent != null) {
      if (parent instanceof PyClass || parent instanceof Callable) {
        if (jump_over) {
          jump_over = false;
        }
        else {
          return parent;
        }
      }
      else if (parent instanceof PyDecoratorList) {
        // decorators PSI is inside decorated things but their namespace is outside
        jump_over = true;
      }
      else if (parent instanceof PsiFileSystemItem) {
        break;
      }
      parent = PsiTreeUtil.getStubOrPsiParent(parent);
    }
    return null;
  }

  /**
   * @param name
   * @return true iff the name looks like a class-private one, starting with two underscores but not ending with two underscores.
   */
  public static boolean isClassPrivateName(String name) {
    return name.startsWith("__") && !name.endsWith("__");
  }

  public static boolean isSpecialName(@NotNull String name) {
    return name.length() > 4 && name.startsWith("__") && name.endsWith("__");
  }

  public static boolean isPythonIdentifier(@NotNull String name) {
    return PyNames.isIdentifier(name);
  }

  public static LookupElement createNamedParameterLookup(String name) {
    LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(name + "=").withIcon(PlatformIcons.PARAMETER_ICON);
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
    final Set<VirtualFile> result = new LinkedHashSet<VirtualFile>();
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    result.addAll(Arrays.asList(manager.getSourceRoots()));
    result.addAll(Arrays.asList(manager.getContentRoots()));
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
      List<String> result = new ArrayList<String>(elements.length);
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

  public static class UnderscoreFilter implements Condition<String> {
    private int myAllowed; // how many starting underscores is allowed: 0 is none, 1 is only one, 2 is two and more.

    public UnderscoreFilter(int allowed) {
      myAllowed = allowed;
    }

    public boolean value(String name) {
      if (name == null) return false;
      if (name.length() < 1) return false; // empty strings make no sense
      int have_underscores = 0;
      if (name.charAt(0) == '_') have_underscores = 1;
      if (have_underscores != 0 && name.length() > 1 && name.charAt(1) == '_') have_underscores = 2;
      return myAllowed >= have_underscores;
    }
  }

  @Nullable
  public static String getKeywordArgumentString(PyCallExpression expr, String keyword) {
    return PyPsiUtils.strValue(expr.getKeywordArgument(keyword));
  }

  public static boolean isExceptionClass(PyClass pyClass) {
    if (isBaseException(pyClass.getQualifiedName())) {
      return true;
    }
    for (PyClassLikeType type : pyClass.getAncestorTypes(TypeEvalContext.codeInsightFallback())) {
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

    private boolean myIsStaticMethod;
    private boolean myIsMetaclassMethod;
    private boolean myIsSpecialMetaclassMethod;
    private boolean myIsClassMethod;

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
        for (PyClass ancestor_cls : cls.getAncestorClasses()) {
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
          for (PyClass s : klass.getAncestorClasses()) {
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
        final FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance();
        final FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
        final String content = (template != null) ? template.getText(fileTemplateManager.getDefaultProperties(project)) : null;
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
        throw new IncorrectOperationException(String.format("Cannot create file '%s'", path));
      }
    }
    else {
      psi = PsiManager.getInstance(project).findFile(vfile);
    }
    if (!(psi instanceof PyFile)) {
      throw new IncorrectOperationException(PyBundle.message(
        "refactoring.move.class.or.function.error.cannot.place.elements.into.nonpython.file"));
    }
    return (PyFile)psi;
  }

  /**
   * counts elements in iterable
   *
   * @param expression to count containing elements (iterable)
   * @return element count
   */
  public static int getElementsCount(PyExpression expression, TypeEvalContext evalContext) {
    int valuesLength = -1;
    PyType type = evalContext.getType(expression);
    if (type instanceof PyTupleType) {
      valuesLength = ((PyTupleType)type).getElementCount();
    }
    else if (type instanceof PyNamedTupleType) {
      valuesLength = ((PyNamedTupleType)type).getElementCount();
    }
    else if (expression instanceof PySequenceExpression) {
      valuesLength = ((PySequenceExpression)expression).getElements().length;
    }
    else if (expression instanceof PyStringLiteralExpression) {
      valuesLength = ((PyStringLiteralExpression)expression).getStringValue().length();
    }
    else if (expression instanceof PyNumericLiteralExpression) {
      valuesLength = 1;
    }
    else if (expression instanceof PyCallExpression) {
      PyCallExpression call = (PyCallExpression)expression;
      if (call.isCalleeText("dict")) {
        valuesLength = call.getArguments().length;
      }
      else if (call.isCalleeText("tuple")) {
        PyExpression[] arguments = call.getArguments();
        if (arguments.length > 0 && arguments[0] instanceof PySequenceExpression) {
          valuesLength = ((PySequenceExpression)arguments[0]).getElements().length;
        }
      }
    }
    return valuesLength;
  }

  @Nullable
  public static PsiElement findPrevAtOffset(PsiFile psiFile, int caretOffset, Class... toSkip) {
    PsiElement element = psiFile.findElementAt(caretOffset);
    if (element == null || caretOffset < 0) {
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

  public static PsiElement addElementToStatementList(@NotNull PsiElement element,
                                                     @NotNull PyStatementList statementList,
                                                     boolean toTheBeginning) {
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
            }
            else {
              break;
            }
          }
          else {
            break;
          }
        }
        element = statementList.addBefore(element, anchor);
      }
      else {
        element = statementList.add(element);
      }
    }
    return element;
  }

  @NotNull
  public static List<PyParameter> getParameters(@NotNull Callable callable, @NotNull TypeEvalContext context) {
    PyType type = context.getType(callable);
    if (type instanceof PyUnionType) {
      type = ((PyUnionType)type).excludeNull(context);
    }
    if (type instanceof PyCallableType) {
      final PyCallableType callableType = (PyCallableType)type;
      final List<PyCallableParameter> callableTypeParameters = callableType.getParameters(context);
      if (callableTypeParameters != null) {
        boolean allParametersDefined = true;
        final List<PyParameter> parameters = new ArrayList<PyParameter>();
        for (PyCallableParameter callableParameter : callableTypeParameters) {
          final PyParameter parameter = callableParameter.getParameter();
          if (parameter == null) {
            allParametersDefined = false;
            break;
          }
          parameters.add(parameter);
        }
        if (allParametersDefined) {
          return parameters;
        }
      }
    }
    return Arrays.asList(callable.getParameterList().getParameters());
  }

  public static boolean isSignatureCompatibleTo(@NotNull Callable callable, @NotNull Callable otherCallable,
                                                @NotNull TypeEvalContext context) {
    final List<PyParameter> parameters = getParameters(callable, context);
    final List<PyParameter> otherParameters = getParameters(otherCallable, context);
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

  private static int optionalParametersCount(@NotNull List<PyParameter> parameters) {
    int n = 0;
    for (PyParameter parameter : parameters) {
      if (parameter.hasDefaultValue()) {
        n++;
      }
    }
    return n;
  }

  private static int requiredParametersCount(@NotNull Callable callable, @NotNull List<PyParameter> parameters) {
    return parameters.size() - optionalParametersCount(parameters) - specialParametersCount(callable, parameters);
  }

  private static int specialParametersCount(@NotNull Callable callable, @NotNull List<PyParameter> parameters) {
    int n = 0;
    if (hasPositionalContainer(parameters)) {
      n++;
    }
    if (hasKeywordContainer(parameters)) {
      n++;
    }
    if (callable.asMethod() != null) {
      n++;
    }
    else {
      if (parameters.size() > 0) {
        final PyParameter first = parameters.get(0);
        if (PyNames.CANONICAL_SELF.equals(first.getName())) {
          n++;
        }
      }
    }
    return n;
  }

  private static boolean hasPositionalContainer(@NotNull List<PyParameter> parameters) {
    for (PyParameter parameter : parameters) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter)parameter).isPositionalContainer()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasKeywordContainer(@NotNull List<PyParameter> parameters) {
    for (PyParameter parameter : parameters) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter)parameter).isKeywordContainer()) {
        return true;
      }
    }
    return false;
  }

  public static boolean isInit(@NotNull final PyFunction function) {
    return PyNames.INIT.equals(function.getName());
  }


  private static boolean isObject(@NotNull final PyMemberInfo<PyElement> classMemberInfo) {
    final PyElement element = classMemberInfo.getMember();
    if ((element instanceof PyClass) && PyNames.OBJECT.equals(element.getName())) {
      return true;
    }
    return false;
  }

  /**
   * Filters out {@link com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo}
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
   * Filters only pyclass object (new class)
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
      if ((element instanceof PyClass) && PyNames.OBJECT.equals(element.getName())) {
        return true;
      }
      return false;
    }
  }
}
