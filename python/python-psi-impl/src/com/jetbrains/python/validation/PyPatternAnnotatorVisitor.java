package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.PyRemoveElementFix;
import com.jetbrains.python.psi.PyAsPattern;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyDoubleStarPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyGroupPattern;
import com.jetbrains.python.psi.PyKeyValuePattern;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyLiteralPattern;
import com.jetbrains.python.psi.PyMappingPattern;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyOrPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyPatternArgumentList;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PySequencePattern;
import com.jetbrains.python.psi.PySingleStarPattern;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyValuePattern;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

final class PyPatternAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  PyPatternAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPySingleStarPattern(@NotNull PySingleStarPattern starPattern) {
    PsiElement parent = starPattern.getParent();
    if (!(parent instanceof PySequencePattern)) {
      myHolder.markError(starPattern, PyPsiBundle.message("ANN.patterns.single.star.pattern.cannot.be.used.outside.sequence.patterns"));
    }
  }

  @Override
  public void visitPyDoubleStarPattern(@NotNull PyDoubleStarPattern starPattern) {
    PsiElement parent = starPattern.getParent();
    if (!(parent instanceof PyMappingPattern)) {
      myHolder.markError(starPattern, PyPsiBundle.message("ANN.patterns.double.star.pattern.cannot.be.used.outside.mapping.patterns"));
    }
  }

  @Override
  public void visitPyLiteralPattern(@NotNull PyLiteralPattern literalPattern) {
    PyBinaryExpression expression = as(literalPattern.getExpression(), PyBinaryExpression.class);
    if (expression != null) {
      PyNumericLiteralExpression rightOperand = as(expression.getRightExpression(), PyNumericLiteralExpression.class);
      if (rightOperand != null && rightOperand.getFirstChild().getNode().getElementType() != PyTokenTypes.IMAGINARY_LITERAL) {
        myHolder.markError(expression, PyPsiBundle.message("ANN.patterns.invalid.complex.number.literal"));
      }
    }
  }

  @Override
  public void visitPyKeyValuePattern(@NotNull PyKeyValuePattern keyValuePattern) {
    PyPattern keyPattern = keyValuePattern.getKeyPattern();
    if (!(keyPattern instanceof PyValuePattern || keyPattern instanceof PyLiteralPattern)) {
      myHolder.markError(keyPattern, PyPsiBundle.message("ANN.patterns.key.pattern.can.only.be.value.or.literal.pattern"));
    }
  }

  @Override
  public void visitPyOrPattern(@NotNull PyOrPattern orPattern) {
    List<PyPattern> alternatives = orPattern.getAlternatives();
    PyPattern lastAlternative = alternatives.get(alternatives.size() - 1);
    Map<PyPattern, Set<String>> patternToBoundNames = new HashMap<>();
    Set<String> allBoundNames = new HashSet<>();

    for (PyPattern alternative : alternatives) {
      Set<String> boundNames = SyntaxTraverser.psiTraverser(alternative)
        .filter(PyTargetExpression.class)
        .map(PyTargetExpression::getName)
        .toSet();
      patternToBoundNames.put(alternative, boundNames);
      allBoundNames.addAll(boundNames);

      if (alternative != lastAlternative && alternative.isIrrefutable()) {
        myHolder.markError(alternative, PyPsiBundle.message("ANN.patterns.pattern.makes.remaining.alternatives.unreachable"));
      }
    }

    for (Map.Entry<PyPattern, Set<String>> entry : patternToBoundNames.entrySet()) {
      Set<String> boundNames = entry.getValue();
      if (!boundNames.equals(allBoundNames)) {
        Collection<String> missingNames = ContainerUtil.subtract(allBoundNames, boundNames);
        String nameList = StringUtil.join(ContainerUtil.sorted(missingNames), ", ");
        myHolder.markError(entry.getKey(), PyPsiBundle.message("ANN.patterns.pattern.does.not.bind.names", missingNames.size(), nameList));
      }
    }
  }

  @Override
  public void visitPyMatchStatement(@NotNull PyMatchStatement matchStatement) {
    List<PyCaseClause> clauses = matchStatement.getCaseClauses();
    if (clauses.isEmpty()) return;

    for (PyCaseClause clause : clauses.subList(0, clauses.size() - 1)) {
      PyPattern pattern = clause.getPattern();
      if (pattern == null) {
        continue;
      }
      PyPattern unwrappedPattern = unwrapGroupAndAsPatterns(pattern);
      // There will be another warning for top-level star patterns
      if (unwrappedPattern instanceof PySingleStarPattern || unwrappedPattern instanceof PyDoubleStarPattern) {
        continue;
      }
      if (clause.getGuardCondition() == null && pattern.isIrrefutable()) {
        myHolder.markError(pattern, PyPsiBundle.message("ANN.patterns.pattern.makes.remaining.case.clauses.unreachable"));
      }
    }
  }

  @Override
  public void visitPyPatternArgumentList(@NotNull PyPatternArgumentList argumentList) {
    Set<String> usedAttrNames = new HashSet<>();
    boolean seenKeywordPattern = false;
    for (PyPattern attrPattern : argumentList.getPatterns()) {
      PyKeywordPattern keywordPattern = as(attrPattern, PyKeywordPattern.class);
      if (keywordPattern == null) {
        if (seenKeywordPattern) {
          myHolder.newAnnotation(HighlightSeverity.ERROR,
                                 PyPsiBundle.message("ANN.patterns.positional.pattern.must.appear.before.keyword.pattern"))
            .range(attrPattern).withFix(new PyRemoveElementFix(attrPattern)).create();
        }
        continue;
      }
      seenKeywordPattern = true;
      if (!usedAttrNames.add(keywordPattern.getKeyword())) {
        myHolder.newAnnotation(HighlightSeverity.ERROR,
                               PyPsiBundle.message("ANN.patterns.attribute.name.is.repeated", keywordPattern.getKeyword()))
          .range(keywordPattern.getKeywordElement()).withFix(new PyRemoveElementFix(keywordPattern)).create();
      }
    }
  }

  @Override
  public void visitPyCaseClause(@NotNull PyCaseClause caseClause) {
    PyPattern pattern = caseClause.getPattern();
    if (pattern == null) return;

    Stack<Set<String>> boundNamesPerOrBranch = new Stack<>();
    // Assume that the top-level pattern is a single branch of a synthetic OR pattern
    boundNamesPerOrBranch.push(new HashSet<>());

    pattern.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyOrPattern(@NotNull PyOrPattern orPattern) {
        Set<String> allBoundNamesInOrPattern = new HashSet<>();
        for (PyPattern alternative : orPattern.getAlternatives()) {
          boundNamesPerOrBranch.push(new HashSet<>());
          alternative.accept(this);
          allBoundNamesInOrPattern.addAll(boundNamesPerOrBranch.peek());
          boundNamesPerOrBranch.pop();
        }
        boundNamesPerOrBranch.peek().addAll(allBoundNamesInOrPattern);
      }

      @Override
      public void visitPyTargetExpression(@NotNull PyTargetExpression target) {
        String name = target.getName();
        boolean alreadyBound = ContainerUtil.exists(boundNamesPerOrBranch, names -> names.contains(name));
        if (alreadyBound) {
          myHolder.markError(target, PyPsiBundle.message("ANN.patterns.name.already.bound", name));
        }
        boundNamesPerOrBranch.peek().add(name);
      }
    });
  }

  @Override
  public void visitPySequencePattern(@NotNull PySequencePattern sequencePattern) {
    List<PySingleStarPattern> starPatterns = PsiTreeUtil.getChildrenOfTypeAsList(sequencePattern, PySingleStarPattern.class);
    if (starPatterns.size() > 1) {
      for (PySingleStarPattern pattern : starPatterns.subList(1, starPatterns.size())) {
        myHolder.markError(pattern, PyPsiBundle.message("ANN.patterns.repeated.star.pattern"));
      }
    }
  }

  @Override
  public void visitPyMappingPattern(@NotNull PyMappingPattern mappingPattern) {
    List<PyDoubleStarPattern> starPatterns = PsiTreeUtil.getChildrenOfTypeAsList(mappingPattern, PyDoubleStarPattern.class);
    if (starPatterns.size() > 1) {
      for (PyDoubleStarPattern pattern : starPatterns.subList(1, starPatterns.size())) {
        myHolder.markError(pattern, PyPsiBundle.message("ANN.patterns.repeated.star.pattern"));
      }
    }
  }

  private static @NotNull PyPattern unwrapGroupAndAsPatterns(@NotNull PyPattern pattern) {
    if (pattern instanceof PyGroupPattern) {
      return unwrapGroupAndAsPatterns(((PyGroupPattern)pattern).getPattern());
    }
    if (pattern instanceof PyAsPattern) {
      return unwrapGroupAndAsPatterns(((PyAsPattern)pattern).getPattern());
    }
    return pattern;
  }
}
