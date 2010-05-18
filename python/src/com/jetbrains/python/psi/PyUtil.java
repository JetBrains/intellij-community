package com.jetbrains.python.psi;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.jetbrains.python.psi.PyFunction.Flag.*;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.interpretAsStaticmethodOrClassmethodWrappingCall;

public class PyUtil {
  private PyUtil() {
  }

  @Nullable
  public static PsiElement addLineToExpression(PyExpression expr, PyExpression add, boolean forceAdd, Comparator<String> comparator) {
    return addLineToExpressionIfMissing(expr, add, null, forceAdd, comparator);
  }

  /**
   * Returns the element that was actually added, or {@code null} if no way
   * was found to add it and {@code forceAdd} was false. The returned
   * element's parent will always be a {@code PyListLiteralExpression}.
   * <br><br>
   * If the given {@code line} is already present, this method will not change
   * the PSI and it will return {@code null}.
   */
  public static
  @Nullable
  PsiElement addLineToExpressionIfMissing(
    PyExpression expr, PyExpression add, String line, boolean forceAdd, @Nullable Comparator<String> comparator
  ) {
    Project project = expr.getProject();
    if (expr instanceof PyListLiteralExpression) {
      PyListLiteralExpression listExp = (PyListLiteralExpression)expr;
      if (comparator != null && add instanceof PyStringLiteralExpression) {
        PyStringLiteralExpression addLiteral = (PyStringLiteralExpression)add;

        List<PyStringLiteralExpression> literals = new ArrayList<PyStringLiteralExpression>();
        for (PyExpression exp : listExp.getElements()) {
          if (exp instanceof PyStringLiteralExpression) {
            PyStringLiteralExpression str = (PyStringLiteralExpression)exp;
            if (line != null && str.getStringValue().equals(line)) {
              // the string is already present
              return null;
            }
            literals.add(str);
          }
        }

        String addval = addLiteral.getStringValue();
        PyStringLiteralExpression after = null;
        boolean quitnext = false;
        for (PyStringLiteralExpression literal : literals) {
          String val = literal.getStringValue();
          if (val == null) {
            continue;
          }

          if (comparator.compare(val, addval) < 0) {
            after = literal;
            quitnext = true;
          }
          else if (quitnext) {
            break;
          }
        }
        try {
          if (after == null) {
            if (literals.size() == 0) {
              return listExp.add(add);
            }
            else {
              return listExp.addBefore(add, literals.get(0));
            }
          }
          else {
            return listExp.addAfter(add, after);
          }
        }
        catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }

      }
      else {
        try {
          return listExp.add(add);
        }
        catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }
      }

    }
    else if (expr instanceof PyBinaryExpression) {
      PyBinaryExpression binexp = (PyBinaryExpression)expr;
      if (binexp.isOperator("+")) {
        PsiElement b = addLineToExpressionIfMissing(binexp.getLeftExpression(), add, line, false, comparator);
        if (b != null) {
          return b;
        }
        PsiElement c = addLineToExpressionIfMissing(binexp.getRightExpression(), add, line, false, comparator);
        if (c != null) {
          return c;
        }
      }
    }
    if (forceAdd) {
      PyListLiteralExpression listLiteral = PyElementGenerator.getInstance(project).createListLiteral();
      try {
        listLiteral.add(add);
      }
      catch (IncorrectOperationException e) {
        throw new IllegalStateException(e);
      }
      PyBinaryExpression binExpr = PyElementGenerator.getInstance(project).createBinaryExpression("+", expr, listLiteral);
      ASTNode exprNode = expr.getNode();
      assert exprNode != null;
      ASTNode parent = exprNode.getTreeParent();
      ASTNode binExprNode = binExpr.getNode();
      assert binExprNode != null;
      parent.replaceChild(exprNode, binExprNode);
      PyListLiteralExpression copiedListLiteral = (PyListLiteralExpression)binExpr.getRightExpression();
      assert copiedListLiteral != null;
      PyExpression[] expressions = copiedListLiteral.getElements();
      return expressions[expressions.length - 1];

    }
    else {
      return null;
    }
  }

  public static ASTNode getNextNonWhitespace(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreeNext();
    }
    while (isWhitespace(node));
    return node;
  }

  public static ASTNode getPrevNonWhitespace(ASTNode after) {
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
        result.add((T)child);
      }
      else {
        result.addAll(Arrays.asList(getAllChildrenOfType(child, aClass)));
      }
    }
    return ArrayUtil.toObjectArray(result, aClass);
  }

  /**
   * @see PyUtil#flattenedParens
   */
  protected static <T extends PyElement> List<T> _unfoldParenExprs(T[] targets, List<T> receiver) {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (T exp : targets) {
      if (exp instanceof PyParenthesizedExpression) {
        final PyParenthesizedExpression parex = (PyParenthesizedExpression)exp;
        PyExpression cont = parex.getContainedExpression();
        if (cont instanceof PyTupleExpression) {
          final PyTupleExpression tupex = (PyTupleExpression)cont;
          _unfoldParenExprs((T[])tupex.getElements(), receiver);
        }
        else {
          receiver.add(exp);
        }
      }
      else if (exp instanceof PyTupleExpression) {
        final PyTupleExpression tupex = (PyTupleExpression)exp;
        _unfoldParenExprs((T[])tupex.getElements(), receiver);
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
   * Typical usage: <code>flattenedParens(some_tuple.getExpressions())</code>.
   *
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  @NotNull
  public static <T extends PyElement> List<T> flattenedParens(T... targets) {
    return _unfoldParenExprs(targets, new ArrayList<T>(targets.length));
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
   * Appends elements of array to a string buffer, interspersing them with a separator: {@code ['a', 'b', 'c'] -> "a, b, c"}.
   *
   * @param what      array to join
   * @param from      index of first element to include (may safely be bigger than array length)
   * @param upto      index of last element to include (may safely be bigger than array length)
   * @param separator string to put between elements
   * @param target    buffer to collect the result
   * @return target (for easy chaining)
   */
  @NotNull
  public static StringBuilder joinSubarray(
    @NotNull String[] what, int from, int upto, @NotNull String separator, @NotNull StringBuilder target
  ) {
    boolean made_step = false;
    for (int i = from; i <= upto; i += 1) {
      if (i >= what.length) break; // safety
      if (made_step) target.append(separator);
      else made_step = true;
      target.append(what[i]);
    }
    return target;
  }

  /**
   * Appends all elements of array to a string buffer, interspersing them with a separator: {@code ['a', 'b', 'c'] -> "a, b, c"}.
   * Actually calls {@link PyUtil#joinSubarray(String[], int, int, String, StringBuilder) joinSubarray} with right bounds.
   * @param what      array to join.
   * @param separator string to put between elements.
   * @param target    collects the result.
   * @return          target, for easy chaining.
   */
  @NotNull
  public static StringBuilder joinArray(
    @NotNull String[] what, @NotNull String separator, @NotNull StringBuilder target
  ) {
    return joinSubarray(what, 0, what.length, separator, target);
  }


  /**
   * Produce a reasonable representation of a PSI element, good for debugging.
   * @param elt element to represent; nulls and invalid nodes are ok.
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

  /**
   * @param element             which to process
   * @param requiredElementType which type of container element is required
   * @return closest containing element of given type, or element itself, it it is of required type.
   */
  @Nullable
  public static <T extends PyElement> T getElementOrContaining(final PyElement element, final Class<T> requiredElementType) {
    if (element == null) return null;
    if (requiredElementType.isInstance(element)) {
      //noinspection unchecked
      return (T)element;
    }

    final PsiElement parent = element.getContainingElement(requiredElementType);
    if (parent != null) {
      //noinspection unchecked
      return (T)parent;
    }

    return null;
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
    if (s != null) return "'" + s.toString() + "'";
    else return "null";
  }

  /**
   * Adds an item into a comma-separated list in a PSI tree. E.g. can turn "foo, bar" into "foo, bar, baz", adding commas as needed. 
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
    if (! got_comma_after) {
      // there was not a comma after the item; remove a comma before the item
      eraseWhitespaceAndComma(binder, item, true);
    }
    // finally
    item.delete();
  }

  /**
   * Removes whitespace and comma(s) that are siblings of the item, up to the first non-whitespace and non-comma.
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
      current = backwards? current.getTreePrev() : current.getTreeNext();
      if (have_skipped_the_item) {
        is_comma = ",".equals(candidate.getText());
        got_comma |= is_comma;
        if (is_comma || candidate.getElementType() == TokenType.WHITE_SPACE) parent_node.removeChild(candidate);
        else break;
      }
      else have_skipped_the_item = true;
    }
    return got_comma;
  }

  /**
   * Collects superclasses of a class all the way up the inheritance chain. The order is <i>not</i> necessarily the MRO.
   */
  @NotNull
  public static PyClass[] getAllSuperClasses(@NotNull PyClass pyClass) {
    Set<PyClass> superClasses = new HashSet<PyClass>();
    /* ZZZ
    List<PyClass> superClassesBuffer = new LinkedList<PyClass>();
    while (true) {
      final PyClass[] classes = pyClass.getSuperClasses();
      if (classes.length == 0) {
        break;
      }
      superClassesBuffer.addAll(Arrays.asList(classes));
      if (!superClasses.containsAll(Arrays.asList(classes))) {
        superClasses.addAll(Arrays.asList(classes));
      }
      else {
        break;
      }
      if (!superClassesBuffer.isEmpty()) {
        pyClass = superClassesBuffer.remove(0);
      }
      else {
        break;
      }
    }
    */
    for (PyClass ancestor : pyClass.iterateAncestors()) superClasses.add(ancestor);
    return superClasses.toArray(new PyClass[superClasses.size()]);
  }

  /**
   * Finds the first identifier AST node under target element, and returns its text.
   * @param target
   * @return identifier text, or null.
   */
  public static @Nullable
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
   * @param ref reference to a possible attribute; only qualified references make sense.
   * @return type, or null (if type cannot be determined, reference is not to a known attribute, etc.)
   */
  @Nullable
  public static PyType getSpecialAttributeType(PyReferenceExpression ref) {
    if (ref != null) {
      PyExpression qualifier = ref.getQualifier();
      if (qualifier != null) {
        String attr_name = getIdentifier(ref);
        if ("__class__".equals(attr_name)) {
          PyType qual_type = qualifier.getType(TypeEvalContext.fast());
          if (qual_type instanceof PyClassType) {
            return new PyClassType(((PyClassType)qual_type).getPyClass(), true); // always as class, never instance
          }
        }
        else if ("__dict__".equals(attr_name)) {
          PyType qual_type = qualifier.getType(TypeEvalContext.fast());
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
   * @param thing what we check.
   */
  public static void sure(boolean thing) {
    if (!thing) throw new IncorrectOperationException();
  }

  /**
   * For cases when a function is decorated with only one decorator, and this is a built-in decorator.
   * <br/> <i>TODO: handle multiple decorators sensibly; then rename and move.</i>
   * @param node the allegedly decorated function
   * @return name of the only built-in decorator, or null (even if there are multiple or non-built-in decorators!)
   */
  public static @Nullable String getTheOnlyBuiltinDecorator(@NotNull final PyFunction node) {
    PyDecoratorList decolist = node.getDecoratorList();
    if (decolist != null) {
      PyDecorator[] decos = decolist.getDecorators();
      // TODO: look for all decorators
      if (decos.length == 1) {
        PyDecorator deco = decos[0];
        String deconame = deco.getName();
        if (deco.isBuiltin()) {
          return deconame;
        }
      }
    }
    return null;
  }

  /**
   * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
   * @param function what to analyze
   * @return a set of flags describing what was detected.
   */
  @NotNull
  public static Set<PyFunction.Flag> detectDecorationsAndWrappersOf(PyFunction function) {
    Set<PyFunction.Flag> flags = EnumSet.noneOf(PyFunction.Flag.class);
    String deconame = getTheOnlyBuiltinDecorator(function);
    if (PyNames.CLASSMETHOD.equals(deconame)) flags.add(CLASSMETHOD);
    else if (PyNames.STATICMETHOD.equals(deconame)) flags.add(STATICMETHOD);
    // implicit classmethod __new__
    PyClass cls = function.getContainingClass();
    if (cls != null && PyNames.NEW.equals(function.getName()) && cls.isNewStyleClass()) flags.add(CLASSMETHOD);
    //
    if (! flags.contains(CLASSMETHOD) && ! flags.contains(STATICMETHOD)) { // not set by decos, look for reassignment
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
                    if (PyNames.CLASSMETHOD.equals(wrapper_name)) flags.add(CLASSMETHOD);
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
      return (0 <= number && number < children.length) ? children [number].getPsi() : null;
    }
    return null;
  }

  /**
   * If argument is a PsiDirectory, turn it into a PsiFile that points to __init__.py in that directory.
   * If there's no __init__.py there, null is returned, there's no point to resolve to a dir which is not a package.
   * Alas, resolve() and multiResolve() can't return anything but a PyFile or PsiFileImpl.isPsiUpToDate() would fail.
   * This is because isPsiUpToDate() relies on identity of objects returned by FileViewProvider.getPsi().
   * If we ever need to exactly tell a dir from __init__.py, that logic has to change.
   * @param target a resolve candidate.
   * @return a PsiFile if target was a PsiDirectory, or null, or target unchanged.
   */
  @Nullable
  public static PsiElement turnDirIntoInit(PsiElement target) {
    if (target instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)target;
      final PsiFile file = dir.findFile(PyNames.INIT_DOT_PY);
      if (file != null) {
        file.putCopyableUserData(PyFile.KEY_IS_DIRECTORY, Boolean.TRUE);
        return file; // ResolveImportUtil will extract directory part as needed, everyone else are better off with a file.
      }
      else return null; // dir without __init__.py does not resolve
    }
    else return target; // don't touch non-dirs
  }

  /**
   * Counts initial underscores of an identifier.
   * @param name identifier
   * @return 0 if no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  public static int getInitialUnderscores(String name) {
    int underscores=0;
    if (name.startsWith("__")) underscores = 2;
    else if (name.startsWith("_")) underscores = 1;
    return underscores;
  }

  /**
   * Tries to find nearest parent that conceals names defined inside it. Such elements are 'class' and 'def':
   * anything defined within it does not seep to the namespace below them, but is concealed within.
   * @param elt starting point of search.
   * @return 'class' or 'def' element, or null if not found.
   */
  @Nullable
  public static PsiElement getConcealingParent(PsiElement elt) {
    if (elt == null) {
      return null;
    }
    PsiElement parent = elt.getParent();
    while(parent != null) {
      if (parent instanceof PyClass || parent instanceof Callable) {
        return parent;
      }
      if (parent instanceof PsiFile) {
        break;
      }
      parent = parent.getParent();
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
}
