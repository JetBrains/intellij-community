// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User : catherine
 */
public abstract class CompatibilityVisitor extends PyAnnotator {

  @NotNull
  private static final Map<LanguageLevel, Set<String>> AVAILABLE_PREFIXES = Maps.newHashMap();

  @NotNull
  private static final Set<String> DEFAULT_PREFIXES = Sets.newHashSet("R", "U", "B", "BR", "RB");

  @NotNull
  protected static final String COMMON_MESSAGE = "Python version ";

  @NotNull
  protected List<LanguageLevel> myVersionsToProcess;

  static {
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON26, Sets.newHashSet("R", "U", "UR", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON27, Sets.newHashSet("R", "U", "UR", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON30, Sets.newHashSet("R", "B"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON31, Sets.newHashSet("R", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON32, Sets.newHashSet("R", "B", "BR"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON36, Sets.newHashSet("R", "U", "B", "BR", "RB", "F", "FR", "RF"));
    AVAILABLE_PREFIXES.put(LanguageLevel.PYTHON37, Sets.newHashSet("R", "U", "B", "BR", "RB", "F", "FR", "RF"));
  }

  public CompatibilityVisitor(@NotNull List<LanguageLevel> versionsToProcess) {
    myVersionsToProcess = versionsToProcess;
  }

  @Override
  public void visitPyAnnotation(PyAnnotation node) {
    final PsiElement parent = node.getParent();
    if (!(parent instanceof PyFunction || parent instanceof PyNamedParameter)) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON36),
                                     " not support variable annotations",
                                     node,
                                     null);
    }
  }

  @Override
  public void visitPyDictCompExpression(PyDictCompExpression node) {
    super.visitPyDictCompExpression(node);

    registerForAllMatchingVersions(level -> !level.supportsSetLiterals(),
                                   " not support dictionary comprehensions",
                                   node,
                                   new ConvertDictCompQuickFix(),
                                   false);
  }

  @Override
  public void visitPySetLiteralExpression(PySetLiteralExpression node) {
    super.visitPySetLiteralExpression(node);

    registerForAllMatchingVersions(level -> !level.supportsSetLiterals(),
                                   " not support set literal expressions",
                                   node,
                                   new ConvertSetLiteralQuickFix(),
                                   false);
  }

  @Override
  public void visitPySetCompExpression(PySetCompExpression node) {
    super.visitPySetCompExpression(node);

    registerForAllMatchingVersions(level -> !level.supportsSetLiterals(),
                                   " not support set comprehensions",
                                   node,
                                   null,
                                   false);
  }

  @Override
  public void visitPyExceptBlock(PyExceptPart node) {
    super.visitPyExceptBlock(node);

    final PyExpression exceptClass = node.getExceptClass();
    if (exceptClass != null) {
      PsiElement element = exceptClass.getNextSibling();
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }

      if (element != null && ",".equals(element.getText())) {
        registerForAllMatchingVersions(LanguageLevel::isPy3K, " not support this syntax.", node, new ReplaceExceptPartQuickFix());
      }
    }
  }

  @Override
  public void visitPyImportStatement(PyImportStatement node) {
    super.visitPyImportStatement(node);

    final PyIfStatement ifParent = PsiTreeUtil.getParentOfType(node, PyIfStatement.class);
    if (ifParent != null) return;

    for (PyImportElement importElement : node.getImportElements()) {
      final QualifiedName qName = importElement.getImportedQName();

      if (qName != null) {
        if (qName.matches("builtins")) {
          registerForAllMatchingVersions(level -> level.isPython2(), " not have module builtins", node, new ReplaceBuiltinsQuickFix());
        }
        else if (qName.matches("__builtin__")) {
          registerForAllMatchingVersions(LanguageLevel::isPy3K, " not have module __builtin__", node, new ReplaceBuiltinsQuickFix());
        }
      }
    }
  }

  @Override
  public void visitPyStarExpression(PyStarExpression node) {
    super.visitPyStarExpression(node);

    if (node.isAssignmentTarget()) {
      registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON30),
                                     "Python versions < 3.0 do not support starred expressions as assignment targets",
                                     node);
    }

    if (node.isUnpacking()) {
      registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                     "Python versions < 3.5 do not support starred expressions in tuples, lists, and sets",
                                     node);
    }
  }

  @Override
  public void visitPyDoubleStarExpression(PyDoubleStarExpression node) {
    super.visitPyDoubleStarExpression(node);

    registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                   "Python versions < 3.5 do not support starred expressions in dicts",
                                   node);
  }

  @Override
  public void visitPyBinaryExpression(PyBinaryExpression node) {
    super.visitPyBinaryExpression(node);

    if (node.isOperator("<>")) {
      registerForAllMatchingVersions(LanguageLevel::isPy3K, " not support <>, use != instead.", node, new ReplaceNotEqOperatorQuickFix());
    }
    else if (node.isOperator("@")) {
      checkMatrixMultiplicationOperator(node.getPsiOperator());
    }
  }

  private void checkMatrixMultiplicationOperator(PsiElement node) {
    registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                   "Python versions < 3.5 do not support matrix multiplication operators",
                                   node);
  }

  @Override
  public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
    super.visitPyNumericLiteralExpression(node);

    final String text = node.getText();

    if (node.isIntegerLiteral()) {
      if (text.endsWith("l") || text.endsWith("L")) {
        registerForAllMatchingVersions(LanguageLevel::isPy3K,
                                       " not support a trailing \'l\' or \'L\'.",
                                       node,
                                       new RemoveTrailingLQuickFix());
      }

      if (text.length() > 1 && text.charAt(0) == '0') {
        final char secondChar = Character.toLowerCase(text.charAt(1));
        if (secondChar != 'o' && secondChar != 'b' && secondChar != 'x' && text.chars().anyMatch(c -> c != '0')) {
          registerForAllMatchingVersions(LanguageLevel::isPy3K,
                                         " not support this syntax. It requires '0o' prefix for octal literals",
                                         node,
                                         new ReplaceOctalNumericLiteralQuickFix());
        }
      }
    }

    if (text.contains("_")) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON36),
                                     " not support underscores in numeric literals",
                                     node,
                                     new PyRemoveUnderscoresInNumericLiteralsQuickFix());
    }
  }

  @Override
  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    super.visitPyStringLiteralExpression(node);

    for (ASTNode stringNode : node.getStringNodes()) {
      final String text = stringNode.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(text);
      final String prefix = text.substring(0, prefixLength).toUpperCase();
      if (prefix.isEmpty()) continue;

      final TextRange range = TextRange.create(stringNode.getStartOffset(), stringNode.getStartOffset() + prefixLength);
      registerForAllMatchingVersions(level -> !AVAILABLE_PREFIXES.getOrDefault(level, DEFAULT_PREFIXES).contains(prefix),
                                     " not support a '" + prefix + "' prefix",
                                     node,
                                     range,
                                     new RemovePrefixQuickFix(prefix),
                                     true);
    }
  }

  @Override
  public void visitPyListCompExpression(final PyListCompExpression node) {
    super.visitPyListCompExpression(node);

    final List<PyExpression> nodes = node.getForComponents().stream().map(PyComprehensionForComponent::getIteratedList).collect(Collectors.toList());
    registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.visitPyListCompExpression(node, level),
                                   " not support this syntax in list comprehensions.",
                                   nodes,
                                   new ReplaceListComprehensionsQuickFix());
  }

  @Override
  public void visitPyRaiseStatement(PyRaiseStatement node) {
    super.visitPyRaiseStatement(node);

    // empty raise under finally
    registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.raiseHasNoArgsUnderFinally(node, level),
                                   " not support this syntax. Raise with no arguments can only be used in an except block",
                                   node,
                                   null,
                                   false);

    // raise 1, 2, 3
    registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.raiseHasMoreThenOneArg(node, level),
                                   " not support this syntax.",
                                   node,
                                   new ReplaceRaiseStatementQuickFix());

    // raise exception from cause
    registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.raiseHasFromKeyword(node, level),
                                   " not support this syntax.",
                                   node,
                                   new ReplaceRaiseStatementQuickFix());
  }

  @Override
  public void visitPyReprExpression(PyReprExpression node) {
    super.visitPyReprExpression(node);

    registerForAllMatchingVersions(LanguageLevel::isPy3K,
                                   " not support backquotes, use repr() instead",
                                   node,
                                   new ReplaceBackquoteExpressionQuickFix());
  }


  @Override
  public void visitPyWithStatement(PyWithStatement node) {
    super.visitPyWithStatement(node);

    final PyWithItem[] items = node.getWithItems();
    if (items.length > 1) {
      registerForAllMatchingVersions(level -> !level.supportsSetLiterals(),
                                     " not support multiple context managers",
                                     Arrays.asList(items).subList(1, items.length),
                                     null);
    }

    checkAsyncKeyword(node);
  }

  @Override
  public void visitPyForStatement(PyForStatement node) {
    super.visitPyForStatement(node);
    checkAsyncKeyword(node);
  }

  @Override
  public void visitPyPrintStatement(PyPrintStatement node) {
    super.visitPyPrintStatement(node);

    final PsiElement[] arguments = node.getChildren();
    final Predicate<PsiElement> nonParenthesesPredicate =
      element -> !(element instanceof PyParenthesizedExpression || element instanceof PyTupleExpression);

    if (arguments.length == 0 || Arrays.stream(arguments).anyMatch(nonParenthesesPredicate)) {
      registerOnFirstMatchingVersion(LanguageLevel::isPy3K,
                                     "Python version >= 3.0 do not support this syntax. " +
                                     "The print statement has been replaced with a print() function",
                                     node,
                                     new CompatibilityPrintCallQuickFix());
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    super.visitPyCallExpression(node);

    final PsiElement firstChild = node.getFirstChild();
    if (firstChild != null && PyNames.SUPER.equals(firstChild.getText()) && ArrayUtil.isEmpty(node.getArguments())) {
      registerForAllMatchingVersions(level -> level.isPython2(),
                                     " not support this syntax. super() should have arguments in Python 2",
                                     node,
                                     null);
    }

    highlightIncorrectArguments(node);
  }

  @Override
  public void visitPyFunction(PyFunction node) {
    super.visitPyFunction(node);
    checkAsyncKeyword(node);
  }

  @Override
  public void visitPyPrefixExpression(PyPrefixExpression node) {
    super.visitPyPrefixExpression(node);

    if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
      registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                     "Python versions < 3.5 do not support this syntax",
                                     node);
    }
  }

  @Override
  public void visitPyYieldExpression(PyYieldExpression node) {
    super.visitPyYieldExpression(node);

    Optional
      .ofNullable(ScopeUtil.getScopeOwner(node))
      .map(owner -> PyUtil.as(owner, PyFunction.class))
      .filter(function -> function.isAsync() && function.isAsyncAllowed())
      .ifPresent(
        function -> {
          if (!node.isDelegating() && myVersionsToProcess.contains(LanguageLevel.PYTHON35)) {
            registerProblem(node, "Python version 3.5 does not support 'yield' inside async functions");
          }
        }
      );

    if (!node.isDelegating()) {
      return;
    }

    registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON33),
                                   "Python versions < 3.3 do not support this syntax. Delegating to a subgenerator is available since " +
                                   "Python 3.3; use explicit iteration over subgenerator instead.",
                                   node);
  }

  @Override
  public void visitPyReturnStatement(PyReturnStatement node) {
    if (myVersionsToProcess.stream().anyMatch(level -> level.isOlderThan(LanguageLevel.PYTHON33))) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class);
      if (function != null && node.getExpression() != null) {
        final YieldVisitor visitor = new YieldVisitor();
        function.acceptChildren(visitor);
        if (visitor.haveYield()) {
          registerProblem(node, "Python versions < 3.3 do not allow 'return' with argument inside generator.");
        }
      }
    }
  }

  @Override
  public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
    if (node.isEllipsis()) {
      final PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression.class);
      if (subscription != null && PsiTreeUtil.isAncestor(subscription.getIndexExpression(), node, false)) {
        return;
      }
      final PySliceItem sliceItem = PsiTreeUtil.getParentOfType(node, PySliceItem.class);
      if (sliceItem != null) {
        return;
      }
      registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON30),
                                     "Python versions < 3.0 do not support '...' outside of sequence slicings.",
                                     node);
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
    super.visitPyAugAssignmentStatement(node);
    final PsiElement operation = node.getOperation();
    if (operation != null) {
      final IElementType operationType = operation.getNode().getElementType();
      if (PyTokenTypes.ATEQ.equals(operationType)) {
        checkMatrixMultiplicationOperator(operation);
      }
    }
  }

  private void checkAsyncKeyword(@NotNull PsiElement node) {
    final ASTNode asyncNode = node.getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD);
    if (asyncNode != null) {
      registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                     "Python versions < 3.5 do not support this syntax",
                                     node,
                                     asyncNode.getTextRange(),
                                     null);
    }
  }

  private static class YieldVisitor extends PyElementVisitor {
    private boolean _haveYield = false;

    public boolean haveYield() {
      return _haveYield;
    }

    @Override
    public void visitPyYieldExpression(final PyYieldExpression node) {
      _haveYield = true;
    }

    @Override
    public void visitPyElement(final PyElement node) {
      if (!_haveYield) {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      // do not go to nested functions
    }
  }

  protected abstract void registerProblem(@NotNull PsiElement node,
                                          @NotNull TextRange range,
                                          @NotNull String message,
                                          @Nullable LocalQuickFix localQuickFix,
                                          boolean asError);

  protected void registerProblem(@NotNull PsiElement node, @NotNull String message, @Nullable LocalQuickFix localQuickFix) {
    registerProblem(node, node.getTextRange(), message, localQuickFix, true);
  }

  protected void registerProblem(@NotNull PsiElement node, @NotNull String message) {
    registerProblem(node, message, null);
  }

  protected void setVersionsToProcess(@NotNull List<LanguageLevel> versionsToProcess) {
    myVersionsToProcess = versionsToProcess;
  }

  protected void registerForAllMatchingVersions(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String suffix,
                                                @NotNull Iterable<Pair<? extends PsiElement, TextRange>> nodesWithRanges,
                                                @Nullable LocalQuickFix localQuickFix,
                                                boolean asError) {
    final List<String> levels = myVersionsToProcess
      .stream()
      .filter(levelPredicate)
      .map(LanguageLevel::toString)
      .collect(Collectors.toList());

    if (!levels.isEmpty()) {
      final String result = COMMON_MESSAGE + StringUtil.join(levels, ", ") + (levels.size() == 1 ? " does" : " do") + suffix;
      for (Pair<? extends PsiElement, TextRange> nodeWithRange : nodesWithRanges) {
        registerProblem(nodeWithRange.first, nodeWithRange.second, result, localQuickFix, asError);
      }
    }
  }

  protected void registerForAllMatchingVersions(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String suffix,
                                                @NotNull Iterable<? extends PsiElement> nodes,
                                                @Nullable LocalQuickFix localQuickFix) {
    final List<Pair<? extends PsiElement, TextRange>> nodesWithRanges =
      ContainerUtil.map(nodes, node -> Pair.createNonNull(node, node.getTextRange()));
    registerForAllMatchingVersions(levelPredicate, suffix, nodesWithRanges, localQuickFix, true);
  }

  protected void registerForAllMatchingVersions(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String suffix,
                                                @NotNull PsiElement node,
                                                @NotNull TextRange range,
                                                @Nullable LocalQuickFix localQuickFix,
                                                boolean asError) {
    final List<Pair<? extends PsiElement, TextRange>> nodesWithRanges = Collections.singletonList(Pair.createNonNull(node, range));
    registerForAllMatchingVersions(levelPredicate, suffix, nodesWithRanges, localQuickFix, asError);
  }

  protected void registerForAllMatchingVersions(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String suffix,
                                                @NotNull PsiElement node,
                                                @Nullable LocalQuickFix localQuickFix,
                                                boolean asError) {
    registerForAllMatchingVersions(levelPredicate, suffix, node, node.getTextRange(), localQuickFix, asError);
  }

  protected void registerForAllMatchingVersions(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String suffix,
                                                @NotNull PsiElement node,
                                                @Nullable LocalQuickFix localQuickFix) {
    registerForAllMatchingVersions(levelPredicate, suffix, node, node.getTextRange(), localQuickFix, true);
  }

  protected void registerOnFirstMatchingVersion(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String message,
                                                @NotNull PsiElement node,
                                                @NotNull TextRange range,
                                                @Nullable LocalQuickFix localQuickFix) {
    if (myVersionsToProcess.stream().anyMatch(levelPredicate)) {
      registerProblem(node, range, message, localQuickFix, true);
    }
  }

  protected void registerOnFirstMatchingVersion(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String message,
                                                @NotNull PsiElement node,
                                                @Nullable LocalQuickFix localQuickFix) {
    registerOnFirstMatchingVersion(levelPredicate, message, node, node.getTextRange(), localQuickFix);
  }

  protected void registerOnFirstMatchingVersion(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull String message,
                                                @NotNull PsiElement node) {
    registerOnFirstMatchingVersion(levelPredicate, message, node, node.getTextRange(), null);
  }

  @Override
  public void visitPyNonlocalStatement(final PyNonlocalStatement node) {
    registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON30), "nonlocal keyword available only since py3", node);
  }

  private void highlightIncorrectArguments(@NotNull PyCallExpression callExpression) {
    final Set<String> keywordArgumentNames = new HashSet<>();

    boolean seenKeywordArgument = false;
    boolean seenKeywordContainer = false;
    boolean seenPositionalContainer = false;

    for (PyExpression argument : callExpression.getArguments()) {
      if (argument instanceof PyKeywordArgument) {
        final String keyword = ((PyKeywordArgument)argument).getKeyword();

        if (keywordArgumentNames.contains(keyword)) {
          registerProblem(argument, "Keyword argument repeated", new PyRemoveArgumentQuickFix());
        }
        else if (seenKeywordContainer) {
          registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                         "Python versions < 3.5 do not allow keyword arguments after **expression",
                                         argument,
                                         new PyRemoveArgumentQuickFix());
        }

        seenKeywordArgument = true;
        keywordArgumentNames.add(keyword);
      }
      else if (argument instanceof PyStarArgument) {
        final PyStarArgument starArgument = (PyStarArgument)argument;
        if (starArgument.isKeyword()) {
          if (seenKeywordContainer) {
            registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                           "Python versions < 3.5 do not allow duplicate **expressions",
                                           argument,
                                           new PyRemoveArgumentQuickFix());
          }
          seenKeywordContainer = true;
        }
        else {
          if (seenPositionalContainer) {
            registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                           "Python versions < 3.5 do not allow duplicate *expressions",
                                           argument,
                                           new PyRemoveArgumentQuickFix());
          }
          seenPositionalContainer = true;
        }
      }
      else {
        if (seenKeywordArgument) {
          registerProblem(argument, "Positional argument after keyword argument", new PyRemoveArgumentQuickFix());
        }
        else if (seenPositionalContainer) {
          registerOnFirstMatchingVersion(level -> level.isOlderThan(LanguageLevel.PYTHON35),
                                         "Python versions < 3.5 do not allow positional arguments after *expression",
                                         argument,
                                         new PyRemoveArgumentQuickFix());
        }
        else if (seenKeywordContainer) {
          registerProblem(argument, "Positional argument after **expression", new PyRemoveArgumentQuickFix());
        }
      }
    }
  }

  @Override
  public void visitPyComprehensionElement(PyComprehensionElement node) {
    super.visitPyComprehensionElement(node);

    if (myVersionsToProcess.contains(LanguageLevel.PYTHON35)) {
      Arrays
        .stream(node.getNode().getChildren(TokenSet.create(PyTokenTypes.ASYNC_KEYWORD)))
        .filter(Objects::nonNull)
        .map(ASTNode::getPsi)
        .forEach(element -> registerProblem(element,
                                            "Python version 3.5 does not support 'async' inside comprehensions and generator expressions"));

      final Stream<PyPrefixExpression> resultPrefixExpressions = PsiTreeUtil
        .collectElementsOfType(node.getResultExpression(), PyPrefixExpression.class)
        .stream();

      final Stream<PyPrefixExpression> ifComponentsPrefixExpressions = node.getIfComponents()
        .stream()
        .map(ifComponent -> PsiTreeUtil.collectElementsOfType(ifComponent.getTest(), PyPrefixExpression.class))
        .flatMap(Collection::stream);

      Stream.concat(resultPrefixExpressions, ifComponentsPrefixExpressions)
        .filter(expression -> expression.getOperator() == PyTokenTypes.AWAIT_KEYWORD && expression.getOperand() != null)
        .map(expression -> expression.getNode().findChildByType(PyTokenTypes.AWAIT_KEYWORD))
        .filter(Objects::nonNull)
        .map(ASTNode::getPsi)
        .forEach(element -> registerProblem(element, "Python version 3.5 does not support 'await' inside comprehensions"));
    }
  }
}
