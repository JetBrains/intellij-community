// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.google.common.collect.Sets;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Predicate;

/**
 * User : catherine
 */
public abstract class PyCompatibilityVisitor extends PyElementVisitor {

  private static final @NotNull Set<String> PYTHON2_PREFIXES = Sets.newHashSet("R", "U", "UR", "B", "BR");

  private static final @NotNull Set<String> PYTHON34_PREFIXES = Sets.newHashSet("R", "U", "B", "BR", "RB");

  private static final @NotNull Set<String> PYTHON36_PREFIXES = Sets.newHashSet("R", "U", "B", "BR", "RB", "F", "FR", "RF");

  private static final @NotNull Set<String> PYTHON314_PREFIXES = Sets.newHashSet("R", "U", "B", "BR", "RB", "F", "FR", "RF", "T", "TR", "RT");

  protected final @NotNull List<LanguageLevel> myVersionsToProcess;

  public PyCompatibilityVisitor(@NotNull List<LanguageLevel> versionsToProcess) {
    myVersionsToProcess = versionsToProcess;
  }

  @Override
  public void visitPyAnnotation(@NotNull PyAnnotation node) {
    final PsiElement parent = node.getParent();
    if (!(parent instanceof PyFunction || parent instanceof PyNamedParameter)) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON36) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.variable.annotations"),
                                     node);
    }
  }

  @Override
  public void visitPyExceptBlock(@NotNull PyExceptPart node) {
    super.visitPyExceptBlock(node);

    final PyExpression exceptClass = node.getExceptClass();
    if (exceptClass != null) {
      PsiElement element = exceptClass.getNextSibling();
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }

      if (element != null && ",".equals(element.getText())) {
        registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                       PyPsiBundle.message("INSP.compatibility.feature.support.this.syntax"),
                                       node,
                                       new ReplaceExceptPartQuickFix());
      }
    }
    PsiElement star = PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.MULT);
    if (star != null) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON311),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.starred.except.part"),
                                     star);
    }
  }

  @Override
  public void visitPyImportStatement(@NotNull PyImportStatement node) {
    super.visitPyImportStatement(node);

    final PyIfStatement ifParent = PsiTreeUtil.getParentOfType(node, PyIfStatement.class);
    if (ifParent != null) return;

    for (PyImportElement importElement : node.getImportElements()) {
      final QualifiedName qName = importElement.getImportedQName();

      if (qName != null) {
        if (qName.matches("builtins")) {
          registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.have.module.builtins"),
                                         node,
                                         new ReplaceBuiltinsQuickFix());
        }
        else if (qName.matches("__builtin__")) {
          registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.have.module.builtin"),
                                         node,
                                         new ReplaceBuiltinsQuickFix());
        }
      }
    }
  }

  @Override
  public void visitPyStarExpression(@NotNull PyStarExpression node) {
    super.visitPyStarExpression(node);

    if (node.isAssignmentTarget()) {
      registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.starred.expressions.as.assignment.targets"),
                                     node);
    }

    if (node.isUnpacking()) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.starred.expressions.in.tuples.lists.and.sets"),
                                     node);
      final PsiElement container = PsiTreeUtil.skipParentsOfType(node, PyParenthesizedExpression.class);
      if (container instanceof PyTupleExpression) {
        final PsiElement tupleParent = container.getParent();
        if (tupleParent instanceof PyReturnStatement) {
          registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                  level.isOlderThan(LanguageLevel.PYTHON38) &&
                                                  registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.support.unpacking.without.parentheses.in.return.statements"),
                                         node);
        }

        if (tupleParent instanceof PyYieldExpression && !((PyYieldExpression)tupleParent).isDelegating()) {
          registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                  level.isOlderThan(LanguageLevel.PYTHON38) &&
                                                  registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.support.unpacking.without.parentheses.in.yield.statements"),
                                         node);
        }

        if (tupleParent instanceof PySubscriptionExpression) {
          registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                  level.isOlderThan(LanguageLevel.PYTHON311) &&
                                                  registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.support.starred.expressions.in.subscriptions"),
                                         node,
                                         new PyReplaceStarByUnpackQuickFix());
        }
      }
      if (node.getParent() instanceof PySubscriptionExpression || node.getParent() instanceof PySliceItem) {
        registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                level.isOlderThan(LanguageLevel.PYTHON311) &&
                                                registerForLanguageLevel(level),
                                       PyPsiBundle.message("INSP.compatibility.feature.support.starred.expressions.in.subscriptions"),
                                       node,
                                       new PyReplaceStarByUnpackQuickFix());
      }
    }

    PsiElement parent = node.getParent();
    if (parent instanceof PyAnnotation && PyStarAnnotatorVisitor.isVariadicArg(parent.getParent())) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON311) &&
                                              registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.starred.expressions.in.type.annotations"),
                                     node);
    }
  }

  @Override
  public void visitPyDoubleStarExpression(@NotNull PyDoubleStarExpression node) {
    super.visitPyDoubleStarExpression(node);

    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.starred.expressions.in.dicts"),
                                   node);
  }

  @Override
  public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
    super.visitPyBinaryExpression(node);

    if (node.isOperator("<>")) {
      registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.diamond.operator"),
                                     node,
                                     new ReplaceNotEqOperatorQuickFix());
    }
    else if (node.isOperator("@")) {
      checkMatrixMultiplicationOperator(node.getPsiOperator());
    }

    checkBitwiseOrUnionSyntax(node);
  }

  private void checkMatrixMultiplicationOperator(PsiElement node) {
    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.matrix.multiplication.operators"),
                                   node);
  }

  @Override
  public void visitPyNumericLiteralExpression(final @NotNull PyNumericLiteralExpression node) {
    super.visitPyNumericLiteralExpression(node);

    final String text = node.getText();

    if (node.isIntegerLiteral()) {
      String suffix = node.getIntegerLiteralSuffix();
      if ("l".equalsIgnoreCase(suffix)) {
        registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                       PyPsiBundle.message("INSP.compatibility.feature.support.long.integer.literal.suffix", suffix),
                                       node,
                                       new RemoveTrailingSuffixQuickFix());
      }

      if (text.length() > 1 && text.charAt(0) == '0') {
        final char secondChar = Character.toLowerCase(text.charAt(1));
        if (secondChar != 'o' && secondChar != 'b' && secondChar != 'x' && text.chars().anyMatch(c -> c != '0')) {
          registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.support.old.style.octal.literals"),
                                         node,
                                         new ReplaceOctalNumericLiteralQuickFix());
        }
      }
    }

    if (text.contains("_")) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON36) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.underscores.in.numeric.literals"),
                                     node,
                                     new PyRemoveUnderscoresInNumericLiteralsQuickFix());
    }
  }

  @Override
  public void visitPyStringLiteralExpression(final @NotNull PyStringLiteralExpression node) {
    super.visitPyStringLiteralExpression(node);

    boolean seenBytes = false;
    boolean seenNonBytes = false;
    for (PyStringElement element : node.getStringElements()) {
      final String prefix = StringUtil.toUpperCase(element.getPrefix());
      if (prefix.isEmpty()) continue;

      final boolean bytes = element.isBytes();
      seenBytes |= bytes;
      seenNonBytes |= !bytes;

      final int elementStart = element.getTextOffset();
      registerForAllMatchingVersions(level -> !getSupportedStringPrefixes(level).contains(prefix) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.string.literal.prefix", prefix),
                                     node,
                                     TextRange.create(elementStart, elementStart + element.getPrefixLength()),
                                     true,
                                     new RemovePrefixQuickFix(prefix));
    }

    if (seenBytes && seenNonBytes) {
      registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.allow.to.mix.bytes.and.non.bytes.literals"),
                                     node);
    }
  }

  private static @NotNull Set<String> getSupportedStringPrefixes(@NotNull LanguageLevel level) {
    if (level.isPython2()) {
      return PYTHON2_PREFIXES;
    }
    else if (level.isOlderThan(LanguageLevel.PYTHON36)) {
      return PYTHON34_PREFIXES;
    }
    else if (level.isOlderThan(LanguageLevel.PYTHON314)) {
      return PYTHON36_PREFIXES;
    }
    else {
      return PYTHON314_PREFIXES;
    }
  }

  @Override
  public void visitPyListCompExpression(final @NotNull PyListCompExpression node) {
    super.visitPyListCompExpression(node);

    for (PyComprehensionForComponent component : node.getForComponents()) {
      registerForAllMatchingVersions(
        level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.listComprehensionIteratesOverNonParenthesizedTuple(node, level),
        PyPsiBundle.message("INSP.compatibility.feature.support.this.syntax.in.list.comprehensions"),
        component.getIteratedList(),
        new ReplaceListComprehensionsQuickFix()
      );
    }
  }

  @Override
  public void visitPyRaiseStatement(@NotNull PyRaiseStatement node) {
    super.visitPyRaiseStatement(node);

    // empty raise under finally
    registerForAllMatchingVersions(
      level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.raiseHasNoArgsUnderFinally(node, level),
      PyPsiBundle.message("INSP.compatibility.feature.support.raise.with.no.arguments.outside.except.block"),
      node
    );

    // raise 1, 2, 3
    registerForAllMatchingVersions(level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.raiseHasMoreThenOneArg(node, level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.this.syntax"),
                                   node,
                                   new ReplaceRaiseStatementQuickFix());

    // raise exception from cause
    registerForAllMatchingVersions(level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.raiseHasFromKeyword(node, level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.this.syntax"),
                                   node,
                                   new ReplaceRaiseStatementQuickFix());
  }

  @Override
  public void visitPyReprExpression(@NotNull PyReprExpression node) {
    super.visitPyReprExpression(node);

    registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.backquotes"),
                                   node,
                                   new ReplaceBackquoteExpressionQuickFix());
  }


  @Override
  public void visitPyWithStatement(@NotNull PyWithStatement node) {
    super.visitPyWithStatement(node);
    checkParenthesizedWithItems(node);
    checkAsyncKeyword(node);
  }

  private void checkParenthesizedWithItems(@NotNull PyWithStatement node) {
    final PsiElement lpar = PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.LPAR);
    final PsiElement rpar = PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.RPAR);
    if (lpar == null && rpar == null) {
      return;
    }
    // Context expressions such as "(foo(), bar())" or "(foo(),)" are valid in Python < 3.9, but most likely indicate an error anyway
    PyWithItem[] withItems = node.getWithItems();
    boolean canBeParsedAsSingleParenthesizedExpression = (
      withItems.length == 1 &&
      PyPsiUtils.getFirstChildOfType(withItems[0], PyTokenTypes.AS_KEYWORD) == null &&
      PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.COMMA) == null
    );
    if (canBeParsedAsSingleParenthesizedExpression) {
      return;
    }
    for (PsiElement parenthesis : ContainerUtil.packNullables(lpar, rpar)) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON39) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.parenthesized.context.expressions"),
                                     parenthesis);
    }
  }

  @Override
  public void visitPyForStatement(@NotNull PyForStatement node) {
    super.visitPyForStatement(node);
    checkAsyncKeyword(node);
  }

  @Override
  public void visitPyPrintStatement(@NotNull PyPrintStatement node) {
    super.visitPyPrintStatement(node);

    final PsiElement[] arguments = node.getChildren();
    final Condition<PsiElement> nonParenthesesPredicate =
      element -> !(element instanceof PyParenthesizedExpression || element instanceof PyTupleExpression);

    if (arguments.length == 0 || ContainerUtil.exists(arguments, nonParenthesesPredicate)) {
      registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.print.statement"),
                                     node,
                                     new CompatibilityPrintCallQuickFix());
    }
  }

  @Override
  public void visitPyCallExpression(@NotNull PyCallExpression node) {
    super.visitPyCallExpression(node);

    final PsiElement firstChild = node.getFirstChild();
    if (firstChild != null && PyNames.SUPER.equals(firstChild.getText()) && ArrayUtil.isEmpty(node.getArguments())) {
      registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.super.without.arguments"),
                                     node);
    }

    highlightIncorrectArguments(node);
  }

  @Override
  public void visitPyFunction(@NotNull PyFunction node) {
    super.visitPyFunction(node);
    checkAsyncKeyword(node);
  }

  @Override
  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
    super.visitPyPrefixExpression(node);

    if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.this.syntax"),
                                     node);
    }
  }

  @Override
  public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
    super.visitPyYieldExpression(node);
    if (!node.isDelegating()) {
      return;
    }
    registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.yield.from"),
                                   node);
  }

  @Override
  public void visitPyReturnStatement(@NotNull PyReturnStatement node) {
    if (ContainerUtil.exists(myVersionsToProcess, level -> level.isPython2() && registerForLanguageLevel(level))) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class);
      if (function != null && node.getExpression() != null) {
        final YieldVisitor visitor = new YieldVisitor();
        function.acceptChildren(visitor);
        if (visitor.haveYield()) {
          registerProblem(node,
                          PyPsiBundle.message("INSP.compatibility.pre35.versions.do.not.allow.return.with.argument.inside.generator"));
        }
      }
    }
  }

  @Override
  public void visitPyEllipsisLiteralExpression(@NotNull PyEllipsisLiteralExpression node) {
    final PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression.class);
    if (subscription != null && PsiTreeUtil.isAncestor(subscription.getIndexExpression(), node, false)) {
      return;
    }
    final PySliceItem sliceItem = PsiTreeUtil.getParentOfType(node, PySliceItem.class);
    if (sliceItem != null) {
      return;
    }
    registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.ellipsis.outside.slices"),
                                   node);
  }

  @Override
  public void visitPyAugAssignmentStatement(@NotNull PyAugAssignmentStatement node) {
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
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.this.syntax"),
                                     node,
                                     asyncNode.getTextRange(),
                                     true);
    }
  }

  private static class YieldVisitor extends PyElementVisitor {
    private boolean _haveYield = false;

    public boolean haveYield() {
      return _haveYield;
    }

    @Override
    public void visitPyYieldExpression(final @NotNull PyYieldExpression node) {
      _haveYield = true;
    }

    @Override
    public void visitPyElement(final @NotNull PyElement node) {
      if (!_haveYield) {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyFunction(final @NotNull PyFunction node) {
      // do not go to nested functions
    }
  }

  protected boolean registerForLanguageLevel(@NotNull LanguageLevel level) {
    return true;
  }

  protected abstract void registerProblem(@NotNull PsiElement node,
                                          @NotNull TextRange range,
                                          @NotNull @InspectionMessage String message,
                                          boolean asError,
                                          LocalQuickFix @NotNull ... fixes);

  protected void registerProblem(@NotNull PsiElement node,
                                 @NotNull @InspectionMessage String message,
                                 LocalQuickFix @NotNull ... fixes) {
    registerProblem(node, node.getTextRange(), message, true, fixes);
  }

  protected void registerForAllMatchingVersions(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull @Nls String suffix,
                                                @NotNull PsiElement node,
                                                @NotNull TextRange range,
                                                boolean asError,
                                                LocalQuickFix @NotNull ... fixes) {
    final List<LanguageLevel> levels = ContainerUtil.filter(myVersionsToProcess, levelPredicate::test);
    if (!levels.isEmpty()) {
      @NlsSafe String versions = StringUtil.join(levels,", ");
      @InspectionMessage String message = PyPsiBundle.message("INSP.compatibility.inspection.unsupported.feature.prefix",
                                                              levels.size(), versions, suffix);
      registerProblem(node, range, message, asError, fixes);
    }
  }

  protected void registerForAllMatchingVersions(@NotNull Predicate<LanguageLevel> levelPredicate,
                                                @NotNull @Nls String suffix,
                                                @NotNull PsiElement node,
                                                LocalQuickFix @NotNull... fixes) {
    registerForAllMatchingVersions(levelPredicate, suffix, node, node.getTextRange(), true, fixes);
  }

  @Override
  public void visitPyNonlocalStatement(final @NotNull PyNonlocalStatement node) {
    registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.have.nonlocal.keyword"),
                                   node);
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
          registerProblem(argument, PyPsiBundle.message("INSP.compatibility.keyword.argument.repeated"), new PyRemoveArgumentQuickFix());
        }
        else if (seenKeywordContainer) {
          registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.allow.keyword.arguments.after.kwargs"),
                                         argument,
                                         new PyRemoveArgumentQuickFix());
        }

        seenKeywordArgument = true;
        keywordArgumentNames.add(keyword);
      }
      else if (argument instanceof PyStarArgument starArgument) {
        if (starArgument.isKeyword()) {
          if (seenKeywordContainer) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                           PyPsiBundle.message("INSP.compatibility.feature.allow.duplicate.kwargs"),
                                           argument,
                                           new PyRemoveArgumentQuickFix());
          }
          seenKeywordContainer = true;
        }
        else {
          if (seenPositionalContainer) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                           PyPsiBundle.message("INSP.compatibility.feature.allow.duplicate.positional.varargs"),
                                           argument,
                                           new PyRemoveArgumentQuickFix());
          }
          seenPositionalContainer = true;
        }
      }
      else {
        if (seenKeywordArgument) {
          registerProblem(argument, PyPsiBundle.message("INSP.compatibility.positional.argument.after.keyword.argument"), new PyRemoveArgumentQuickFix());
        }
        else if (seenPositionalContainer) {
          registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                         PyPsiBundle.message("INSP.compatibility.feature.allow.positional.arguments.after.expression"),
                                         argument,
                                         new PyRemoveArgumentQuickFix());
        }
        else if (seenKeywordContainer) {
          registerProblem(argument, PyPsiBundle.message("INSP.compatibility.positional.argument.after.kwargs"), new PyRemoveArgumentQuickFix());
        }
      }
    }

    /* check for trailing comma */
    PyExpression lastArg = ContainerUtil.getLastItem(Arrays.asList(callExpression.getArguments()));
    if (lastArg instanceof PyStarArgument) {
      PsiElement sibling = PyPsiUtils.getNextNonWhitespaceSibling(lastArg);
      if (sibling != null && sibling.getNode().getElementType() == PyTokenTypes.COMMA) {
        boolean isKeyword = ((PyStarArgument)lastArg).isKeyword();
        String message;
        if (isKeyword) {
          message = PyPsiBundle.message("INSP.compatibility.feature.allow.trailing.comma.after.kwargs");
        }
        else {
          message = PyPsiBundle.message("INSP.compatibility.feature.allow.trailing.comma.after.positional.vararg");
        }
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                       message,
                                       sibling);
      }
    }
  }

  @Override
  public void visitPySlashParameter(@NotNull PySlashParameter node) {
    super.visitPySlashParameter(node);

    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON38) && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.positional.only.parameters"),
                                   node);
  }

  @Override
  public void visitPyFStringFragment(@NotNull PyFStringFragment node) {
    super.visitPyFStringFragment(node);

    final ASTNode equalitySignInFStringFragment = node.getNode().findChildByType(PyTokenTypes.EQ);
    if (equalitySignInFStringFragment != null) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON38) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.support.equality.signs.in.fstrings"),
                                     equalitySignInFStringFragment.getPsi());
    }

    List<PyFStringFragment> containingFragmentsOfSameFString =
      PsiTreeUtil.collectParents(node, PyFStringFragment.class, false, o -> o instanceof PyStringLiteralExpression);

    if (containingFragmentsOfSameFString.size() > 1) {
      // At the moment, there is a limit of 2 for CPython 3.12, but it's implementation-dependent.
      // See https://peps.python.org/pep-0701/#specification
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.allow.deep.expression.nesting.in.f-strings"), node);
    }

    boolean isTopmostFragment = PsiTreeUtil.getParentOfType(node, PyFStringFragment.class, true) == null;
    if (isTopmostFragment) {
      List<PyFStringFragment> fragments = new ArrayList<>();
      fragments.add(node);
      PyFStringFragmentFormatPart formatPart = node.getFormatPart();
      if (formatPart != null) {
        fragments.addAll(formatPart.getFragments());
      }
      for (PyFStringFragment fragment : fragments) {
        String wholeNodeText = fragment.getText();
        TextRange range = fragment.getExpressionContentRange();
        for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
          if (wholeNodeText.charAt(i) == '\\') {
            TextRange backslashRange = TextRange.from(i, 1).shiftRight(fragment.getTextRange().getStartOffset());
            registerForAllMatchingVersions(
              level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
              PyPsiBundle.message("INSP.compatibility.feature.allow.backslashes.in.f-strings"),
              node, backslashRange, true
            );
          }
        }
      }
    }


    List<PyFormattedStringElement> containingFStrings =
      PsiTreeUtil.collectParents(node, PyFormattedStringElement.class, false, e -> e instanceof PyStatement);
    assert !containingFStrings.isEmpty();
    PyFormattedStringElement parentFString = containingFStrings.get(0);
    List<PyFormattedStringElement> remainingEnclosingFStrings = ContainerUtil.subList(containingFStrings, 1);
    // Report only on fragments of the topmost single-quoted f-string to avoid duplicates
    // in cases like: f'{f'{1<BR>
    // + 1}'}'
    boolean isFragmentOfTopmostSingleQuotedFString = !parentFString.isTripleQuoted() &&
                                                     ContainerUtil.all(remainingEnclosingFStrings, PyStringElement::isTripleQuoted);
    if (isFragmentOfTopmostSingleQuotedFString && node.textContains('\n')) {
      int lineBreakOffset = node.getText().indexOf('\n');
      if (node.getExpressionContentRange().contains(lineBreakOffset)) {
        PsiElement multiLineLeaf = node.findElementAt(lineBreakOffset);
        assert multiLineLeaf != null;
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                       PyPsiBundle.message("INSP.compatibility.feature.allow.new.lines.in.f-strings"), multiLineLeaf);
      }
    }


    String parentFStringQuote = parentFString.getQuote();
    // Report only on fragments of the topmost f-string with this quote type to avoid duplicates in cases like: f'{f'{f"'"}'}'
    boolean isFragmentOfTopmostFStringWithSuchQuotes = ContainerUtil.all(remainingEnclosingFStrings,
                                                                         fString -> !fString.getQuote().equals(parentFStringQuote));
    if (isFragmentOfTopmostFStringWithSuchQuotes) {
      int illegalQuoteOffset = node.getText().indexOf(parentFStringQuote);
      if (node.getExpressionContentRange().contains(illegalQuoteOffset)) {
        TextRange illegalQuoteRange = TextRange.from(illegalQuoteOffset, parentFStringQuote.length());
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                       PyPsiBundle.message("INSP.compatibility.feature.allow.quote.reuse.in.f-strings"),
                                       node, illegalQuoteRange.shiftRight(node.getTextRange().getStartOffset()), true);
      }
    }
  }

  @Override
  public void visitComment(@NotNull PsiComment node) {
    boolean insideFStringFragment = PsiTreeUtil.getParentOfType(node, PyFStringFragment.class) != null;
    if (insideFStringFragment) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.line.comments.in.f-strings"), node);
    }
  }

  @Override
  public void visitPyAssignmentExpression(@NotNull PyAssignmentExpression node) {
    super.visitPyAssignmentExpression(node);
    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON38) && registerForLanguageLevel(level),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.assignment.expressions"), node);
  }

  @Override
  public void visitPyContinueStatement(@NotNull PyContinueStatement node) {
    super.visitPyContinueStatement(node);

    if (PsiTreeUtil.getParentOfType(node, PyFinallyPart.class, false, PyLoopStatement.class) != null) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON38) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.continue.inside.finally.clause"),
                                     node);
    }
  }

  @Override
  public void visitPyDecorator(@NotNull PyDecorator decorator) {
    super.visitPyDecorator(decorator);
    if (PsiTreeUtil.getChildOfType(decorator, PsiErrorElement.class) == null && decorator.getQualifiedName() == null) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON39) && registerForLanguageLevel(level),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.arbitrary.expressions.as.decorator"), decorator);
    }
  }

  @Override
  public void visitPyMatchStatement(@NotNull PyMatchStatement matchStatement) {
    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON310),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.match.statements"),
                                   matchStatement.getFirstChild());
  }

  @Override
  public void visitPyTypeAliasStatement(@NotNull PyTypeAliasStatement node) {
    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312),
                                   PyPsiBundle.message("INSP.compatibility.feature.support.type.alias.statements"),
                                   node);
  }

  @Override
  public void visitPyTypeParameterList(@NotNull PyTypeParameterList node) {
    // No need to report an error inside the statement which is already reported as unsupported
    if (!(node.getParent() instanceof PyTypeAliasStatement)) {
      registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312),
                                     PyPsiBundle.message("INSP.compatibility.feature.support.this.syntax"),
                                     node);
    }
  }

  private void checkBitwiseOrUnionSyntax(@NotNull PyBinaryExpression node) {
    if (node.getOperator() != PyTokenTypes.OR) return;

    final PsiFile file = node.getContainingFile();
    final boolean isInAnnotation = PsiTreeUtil.getParentOfType(node, PyAnnotation.class, false, PyStatement.class) != null;
    if (file == null ||
        file instanceof PyFile &&
        ((PyFile)file).hasImportFromFuture(FutureFeature.ANNOTATIONS) &&
        isInAnnotation) {
      return;
    }

    final TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());

    // Consider only full expression not parts to have only one registered problem
    if (PsiTreeUtil.getParentOfType(node, PyBinaryExpression.class, true, PyStatement.class) != null) return;

    final Ref<PyType> refType = PyTypingTypeProvider.getType(node, context);
    if (refType != null && refType.get() instanceof PyUnionType) {
      if (isInAnnotation) {
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON310),
                                       PyPsiBundle.message("INSP.compatibility.new.union.syntax.not.available.in.earlier.version"),
                                       node, new ReplaceWithOldStyleUnionQuickFix(), new AddFromFutureImportAnnotationsQuickFix());
      }
      else {
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON310),
                                       PyPsiBundle.message("INSP.compatibility.new.union.syntax.not.available.in.earlier.version"),
                                       node, new ReplaceWithOldStyleUnionQuickFix());
      }
    }
  }

  private static class ReplaceWithOldStyleUnionQuickFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("QFIX.replace.with.old.union.style");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiFile file = element.getContainingFile();
      if (file == null) return;

      if (!(element instanceof PyBinaryExpression expression)) return;

      final List<String> types = collectUnionTypes(expression);
      final LanguageLevel languageLevel = LanguageLevel.forElement(file);
      final AddImportHelper.ImportPriority priority = languageLevel.isAtLeast(LanguageLevel.PYTHON35) ?
                                                      AddImportHelper.ImportPriority.BUILTIN :
                                                      AddImportHelper.ImportPriority.THIRD_PARTY;

      final PyCallExpression call = PsiTreeUtil.getParentOfType(expression, PyCallExpression.class);
      if (call != null && call.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
        replaceOldExpressionWith(project, languageLevel, expression, "(" + StringUtil.join(types, ", ") + ")");
      }
      else {
        if (types.size() == 2 && types.contains(PyNames.NONE)) {
          final String notNoneType = PyNames.NONE.equals(types.get(0)) ? types.get(1) : types.get(0);
          AddImportHelper.addOrUpdateFromImportStatement(file, "typing", "Optional", null, priority, expression);
          replaceOldExpressionWith(project, languageLevel, expression, "Optional[" + notNoneType + "]");
        }
        else {
          AddImportHelper.addOrUpdateFromImportStatement(file, "typing", "Union", null, priority, expression);
          replaceOldExpressionWith(project, languageLevel, expression, "Union[" + StringUtil.join(types, ", ") + "]");
        }
      }
    }

    private static void replaceOldExpressionWith(@NotNull Project project, @NotNull LanguageLevel languageLevel,
                                                 @NotNull PyBinaryExpression expression, @NotNull String text) {
      final PyExpression newExpression = PyElementGenerator
        .getInstance(project)
        .createExpressionFromText(languageLevel, text);
      expression.replace(newExpression);
    }

    private static @Unmodifiable @NotNull List<String> collectUnionTypes(@Nullable PyExpression expression) {
      if (expression == null) return Collections.emptyList();
      if (expression instanceof PyBinaryExpression) {
        final List<String> leftTypes = collectUnionTypes(((PyBinaryExpression)expression).getLeftExpression());
        final List<String> rightTypes = collectUnionTypes(((PyBinaryExpression)expression).getRightExpression());
        return ContainerUtil.concat(leftTypes, rightTypes);
      }
      else if (expression instanceof PyParenthesizedExpression) {
        return collectUnionTypes(PyPsiUtils.flattenParens(expression));
      }
      else {
        return Collections.singletonList(expression.getText());
      }
    }
  }

  private static class AddFromFutureImportAnnotationsQuickFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("QFIX.add.from.future.import.annotations");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiFile file = element.getContainingFile();
      AddImportHelper.addOrUpdateFromImportStatement(file, "__future__", "annotations", null, AddImportHelper.ImportPriority.FUTURE, null);
    }
  }
}
