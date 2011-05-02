package com.jetbrains.python.psi;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.documentation.EpydocUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.PyFunction.Flag.*;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.interpretAsStaticmethodOrClassmethodWrappingCall;

public class PyUtil {
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

  private static boolean isWhitespace(ASTNode node) {
    return node != null && node.getElementType().equals(TokenType.WHITE_SPACE);
  }


  @NotNull
  public static Set<PsiElement> getComments(PsiElement start) {
    final Set<PsiElement> comments = new HashSet<PsiElement>();
    PsiElement seeker = start.getPrevSibling();
    if (seeker == null) seeker = start.getParent().getPrevSibling();
    while (seeker instanceof PsiWhiteSpace || seeker instanceof PsiComment) {
      if (seeker instanceof PsiComment) {
        comments.add(seeker);
      }
      seeker = seeker.getPrevSibling();
    }
    return comments;
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
        result.add((T)child);
      }
      else {
        ContainerUtil.addAll(result, getAllChildrenOfType(child, aClass));
      }
    }
    return ArrayUtil.toObjectArray(result, aClass);
  }

  @Nullable
  public static PyExpression flattenParens(@Nullable PyExpression expr) {
    while (expr instanceof PyParenthesizedExpression) {
      expr = ((PyParenthesizedExpression) expr).getContainedExpression();
    }
    return expr;
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
        _unfoldParenExprs(new PyExpression[] { parex.getContainedExpression() }, receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyTupleExpression) {
        final PyTupleExpression tupex = (PyTupleExpression)exp;
        _unfoldParenExprs(tupex.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyListLiteralExpression && unfoldListLiterals) {
        final PyListLiteralExpression listLiteral = (PyListLiteralExpression) exp;
        _unfoldParenExprs(listLiteral.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyStarExpression && unfoldStarExpressions) {
        _unfoldParenExprs(new PyExpression[] { ((PyStarExpression) exp).getExpression() }, receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else {
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
  public static void addListNode(PsiElement parent, PsiElement newItem, ASTNode beforeThis, boolean isFirst, boolean isLast) {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(parent)) {
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
  }

  /**
   * Removes an element from a a comma-separated list in a PSI tree. E.g. can turn "foo, bar, baz" into "foo, baz",
   * removing commas as needed. It removes a trailing comma if it results from deletion.
   *
   * @param item what to remove. Its parent is considered the list, and commas must be its peers.
   */
  public static void removeListNode(PsiElement item) {
    PsiElement parent = item.getParent();
    if (!CodeInsightUtilBase.preparePsiElementForWrite(parent)) {
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
  private static boolean eraseWhitespaceAndComma(ASTNode parent_node, PsiElement item, boolean backwards) {
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
    for (PyClass ancestor : pyClass.iterateAncestorClasses()) superClasses.add(ancestor);
    return superClasses;
  }

  /**
   * Finds the first identifier AST node under target element, and returns its text.
   *
   * @param target
   * @return identifier text, or null.
   */
  public static
  @Nullable
  String getIdentifier(PsiElement target) {
    ASTNode node = target.getNode();
    if (node != null) {
      ASTNode ident_node = node.findChildByType(PyTokenTypes.IDENTIFIER);
      if (ident_node != null) return ident_node.getText();
    }
    return null;
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
        String attr_name = getIdentifier(ref);
        if ("__class__".equals(attr_name)) {
          PyType qual_type = context.getType(qualifier);
          if (qual_type instanceof PyClassType) {
            return new PyClassType(((PyClassType)qual_type).getPyClass(), true); // always as class, never instance
          }
        }
        else if ("__dict__".equals(attr_name)) {
          PyType qual_type = context.getType(qualifier);
          if (qual_type instanceof PyClassType && ((PyClassType)qual_type).isDefinition()) {
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

  /**
   * When a function is decorated many decorators, finds the deepest builtin decorator:
   * <pre>
   * &#x40;foo
   * &#x40;classmethod <b># &lt;-- that's it</b>
   * &#x40;bar
   * def moo(cls):
   * &nbsp;&nbsp;pass
   * </pre>
   * @param node the allegedly decorated function
   * @return name of the built-in decorator, or null (even if there are non-built-in decorators).
   */
  @Nullable
  public static String getClassOrStaticMethodDecorator(@NotNull final PyFunction node) {
    PyDecoratorList decolist = node.getDecoratorList();
    if (decolist != null) {
      PyDecorator[] decos = decolist.getDecorators();
      if (decos.length > 0) {
        for (int i = decos.length - 1; i >= 0; i -= 1) {
          PyDecorator deco = decos[i];
          String deconame = deco.getName();
          if (PyNames.CLASSMETHOD.equals(deconame) || PyNames.STATICMETHOD.equals(deconame)) {
            return deconame;
          }
          for(PyKnownDecoratorProvider provider: KnownDecoratorProviderHolder.KNOWN_DECORATOR_PROVIDERS) {
            String name = provider.toKnownDecorator(deconame);
            if (name != null) {
              return name;
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean isInstanceAttribute(PyExpression target) {
    if (!(target instanceof PyTargetExpression)) {
      return false;
    }
    PyFunction method = PsiTreeUtil.getParentOfType(target, PyFunction.class);
    if (method == null || method.getContainingClass() == null) {
      return false;
    }
    final PyParameter[] params = method.getParameterList().getParameters();
    if (params.length == 0) {
      return false;
    }
    final PyTargetExpression targetExpr = (PyTargetExpression)target;
    PyExpression qualifier = targetExpr.getQualifier();
    return qualifier != null && qualifier.getText().equals(params[0].getName());
  }

  public static boolean isDocString(PyExpression expression) {
    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(expression, PyDocStringOwner.class);
    if (docStringOwner != null) {
      if (docStringOwner.getDocStringExpression() == expression) {
        return true;
      }
    }
    if (EpydocUtil.isVariableDocString((PyStringLiteralExpression)expression)) return true;
    return false;
  }

  public static class KnownDecoratorProviderHolder {
    public static PyKnownDecoratorProvider[] KNOWN_DECORATOR_PROVIDERS = Extensions.getExtensions(PyKnownDecoratorProvider.EP_NAME);

    private KnownDecoratorProviderHolder() {
    }
  }

  /**
   * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
   *
   * @param function what to analyze
   * @return a set of flags describing what was detected.
   */
  @NotNull
  public static Set<PyFunction.Flag> detectDecorationsAndWrappersOf(PyFunction function) {
    Set<PyFunction.Flag> flags = EnumSet.noneOf(PyFunction.Flag.class);
    String deconame = getClassOrStaticMethodDecorator(function);
    if (PyNames.CLASSMETHOD.equals(deconame)) {
      flags.add(CLASSMETHOD);
    }
    else if (PyNames.STATICMETHOD.equals(deconame)) flags.add(STATICMETHOD);
    // implicit staticmethod __new__
    PyClass cls = function.getContainingClass();
    if (cls != null && PyNames.NEW.equals(function.getName()) && cls.isNewStyleClass()) flags.add(STATICMETHOD);
    //
    if (!flags.contains(CLASSMETHOD) && !flags.contains(STATICMETHOD)) { // not set by decos, look for reassignment
      String func_name = function.getName();
      if (func_name != null) {
        PyAssignmentStatement assignment = PsiTreeUtil.getNextSiblingOfType(function, PyAssignmentStatement.class);
        if (assignment != null) {
          for (Pair<PyExpression, PyExpression> pair : assignment.getTargetsToValuesMapping()) {
            PyExpression value = pair.getSecond();
            if (value instanceof PyCallExpression) {
              PyExpression target = pair.getFirst();
              if (target instanceof PyTargetExpression && func_name.equals(target.getName())) {
                Pair<String, PyFunction> interpreted = interpretAsStaticmethodOrClassmethodWrappingCall((PyCallExpression)value, function);
                if (interpreted != null) {
                  PyFunction original = interpreted.getSecond();
                  if (original == function) {
                    String wrapper_name = interpreted.getFirst();
                    if (PyNames.CLASSMETHOD.equals(wrapper_name)) {
                      flags.add(CLASSMETHOD);
                    }
                    else if (PyNames.STATICMETHOD.equals(wrapper_name)) flags.add(STATICMETHOD);
                    flags.add(WRAPPED);
                  }
                }
              }
            }
          }
        }
      }
    }
    return flags;
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

  /**
   * Counts initial underscores of an identifier.
   *
   * @param name identifier
   * @return 0 if no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  public static int getInitialUnderscores(String name) {
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
   */
  @Nullable
  public static PsiElement getConcealingParent(PsiElement elt) {
    if (elt == null || elt instanceof PsiFile) {
      return null;
    }
    PsiElement parent = PsiTreeUtil.getStubOrPsiParent(elt);
    boolean jump_over = false;
    while (parent != null) {
      if (parent instanceof PyClass || parent instanceof Callable) {
        if (jump_over) jump_over = false;
        else return parent;
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

  public static boolean isPythonIdentifier(String name) {
    return PyNames.isIdentifier(name);
  }

  public static LookupElement createNamedParameterLookup(String name) {
    LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(name + "=").setIcon(Icons.PARAMETER_ICON);
    return PrioritizedLookupElement.withGrouping(lookupElementBuilder, 1);
  }

  /**
   * Peels argument expression of parentheses and of keyword argument wrapper
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

  @Nullable
  public static VirtualFile findInRoots(Module module, String path) {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    VirtualFile result = findInRoots(moduleRootManager.getContentRoots(), path);
    if (result == null) {
      result = findInRoots(moduleRootManager.getSourceRoots(), path);
    }
    return result;
  }

  @Nullable
  public static VirtualFile findInRoots(VirtualFile[] roots, String path) {
    for (VirtualFile root : roots) {
      VirtualFile settingsFile = root.findFileByRelativePath(path);
      if (settingsFile != null) {
        return settingsFile;
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
      value = ((PyParenthesizedExpression) value).getContainedExpression();
    }
    if (value instanceof PySequenceExpression) {
      final PyExpression[] elements = ((PySequenceExpression)value).getElements();
      List<String> result = new ArrayList<String>(elements.length);
      for (PyExpression element : elements) {
        if (!(element instanceof PyStringLiteralExpression)) {
          return null;
        }
        result.add(((PyStringLiteralExpression) element).getStringValue());
      }
      return result;
    }
    return null;
  }

  @Nullable
  public static String strValue(@Nullable PyExpression expression) {
    return expression instanceof PyStringLiteralExpression ? ((PyStringLiteralExpression) expression).getStringValue() : null;
  }

  /**
   * @param what thing to search for
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
  public static PyExpression getKeywordArgument(PyCallExpression expr, String keyword) {
    for (PyExpression arg : expr.getArguments()) {
      if (arg instanceof PyKeywordArgument) {
        PyKeywordArgument kwarg = (PyKeywordArgument)arg;
        if (keyword.equals(kwarg.getKeyword())) {
          return kwarg.getValueExpression();
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getKeywordArgumentString(PyCallExpression expr, String keyword) {
    return strValue(getKeywordArgument(expr, keyword));
  }

  public static boolean isExceptionClass(PyClass pyClass) {
    return pyClass.isSubclass("exceptions.BaseException");
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
        Set<PyFunction.Flag> flags = detectDecorationsAndWrappersOf(node);
        boolean isMetaclassMethod = false;
        PyClass type_cls = PyBuiltinCache.getInstance(node).getClass("type");
        for (PyClass ancestor_cls : cls.iterateAncestorClasses()) {
          if (ancestor_cls == type_cls) {
            isMetaclassMethod = true;
            break;
          }
        }
        final String method_name = node.getName();
        boolean isSpecialMetaclassMethod = isMetaclassMethod && method_name != null && among(method_name, PyNames.INIT, "__call__");
        return new MethodFlags(flags.contains(CLASSMETHOD), flags.contains(STATICMETHOD), isMetaclassMethod, isSpecialMetaclassMethod);
      }
      return null;
    }
  }
}

