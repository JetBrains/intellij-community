package com.intellij.refactoring.util.duplicates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author dsl
 */
public class DuplicatesFinder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.duplicates.DuplicatesFinder");
  private static final Key<PsiVariable> PARAMETER = Key.create("PARAMETER");
  private final PsiElement[] myPattern;
  private final boolean mySkipStaticContext;
  private final List<? extends PsiVariable> myParameters;
  private final List<? extends PsiVariable> myOutputParameters;
  private final List<PsiElement> myPatternAsList;

  public DuplicatesFinder(PsiElement[] pattern,
                          List<? extends PsiVariable> parameters,
                          List<? extends PsiVariable> outputParameters,
                          boolean maintainStaticContext) {
    LOG.assertTrue(pattern.length > 0);
    myPattern = pattern;
    myPatternAsList = Arrays.asList(myPattern);
    myParameters = parameters;
    myOutputParameters = outputParameters;
    mySkipStaticContext = maintainStaticContext && !RefactoringUtil.isInStaticContext(myPattern[0]);
  }


  public List<Match> findDuplicates(PsiElement scope) {
    annotatePattern();
    final ArrayList<Match> result = new ArrayList<Match>();
    findPatternOccurrences(result, scope);
    deannotatePattern();
    return result;
  }

  private void annotatePattern() {
    for (int i = 0; i < myPattern.length; i++) {
      final PsiElement patternComponent = myPattern[i];
      patternComponent.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          final PsiElement element = reference.resolve();
          if (myParameters.contains(element)) {
            reference.putUserData(PARAMETER, (PsiVariable) element);
          }
          if (myOutputParameters.contains(element)) {
            reference.putUserData(PARAMETER, (PsiVariable) element);
          }
          PsiElement qualifier = reference.getQualifier();
          if (qualifier != null) {
            qualifier.accept(this);
          }
        }
      });
    }
  }

  private void deannotatePattern() {
    for (int i = 0; i < myPattern.length; i++) {
      final PsiElement patternComponent = myPattern[i];
      patternComponent.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          if (reference.getUserData(PARAMETER) != null) {
            reference.putUserData(PARAMETER, null);
          }
        }
      });
    }
  }


  private void findPatternOccurrences(List<Match> array, PsiElement scope) {
    if (mySkipStaticContext && RefactoringUtil.isInStaticContext(scope)) return;
    PsiElement[] children = scope.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      final Match match = isDuplicateFragment(child);
      if (match != null) {
        array.add(match);
        continue;
      }
      findPatternOccurrences(array, child);
    }
  }


  public Match isDuplicateFragment(PsiElement candidate) {
    if (candidate == myPattern[0]) return null;
    PsiElement sibling = candidate;
    ArrayList<PsiElement> candidates = new ArrayList<PsiElement>();
    for (int i = 0; i < myPattern.length; i++) {
      final PsiElement element = myPattern[i];
      if (sibling == null) return null;
      if (!canBeEquivalent(element, sibling)) return null;
      candidates.add(sibling);
      sibling = sibling.getNextSibling();
      while(sibling instanceof PsiWhiteSpace) {
        sibling = sibling.getNextSibling();
      }
    }
    LOG.assertTrue(myPattern.length == candidates.size());
    if (myPattern.length == 1 && myPattern[0] instanceof PsiExpression) {
      if (candidates.get(0) instanceof PsiExpression) {
        final PsiType patternType = ((PsiExpression)myPattern[0]).getType();
        final PsiType candidateType = ((PsiExpression)candidates.get(0)).getType();
        if (patternType != null && candidateType != null && !candidateType.isAssignableFrom(patternType)) {
          return null;
        }
      }
      else {
        return null;
      }

    }
    final Match match = new Match(candidates.get(0), candidates.get(candidates.size() - 1));
    for (int i = 0; i < myPattern.length; i++) {
      if (!matchPattern(myPattern[i], candidates.get(i), candidates, match)) return null;
    }

    return match;
  }

  private boolean canBeEquivalent(final PsiElement pattern, PsiElement candidate) {
    if (pattern instanceof PsiReturnStatement && candidate instanceof PsiExpressionStatement) return true;
    if (pattern instanceof PsiReturnStatement && candidate instanceof PsiDeclarationStatement) return true;
    return pattern.getClass().equals(candidate.getClass());
  }

  private boolean matchPattern(PsiElement pattern,
                               PsiElement candidate,
                               List<PsiElement> candidates,
                               Match match) {
    if ((pattern == null || candidate == null)) return pattern == candidate;
    if (pattern.getUserData(PARAMETER) != null) {
      final PsiVariable parameter = pattern.getUserData(PARAMETER);
      return match.putParameter(parameter, candidate);
    }

    if (!canBeEquivalent(pattern, candidate)) return false; // Q : is it correct to check implementation classes?

    PsiReference ref1 = pattern.getReference();
    if (ref1 != null) {
      PsiReference ref2 = candidate.getReference();
      if (ref2 == null) return false;
      final PsiElement resolveResult1 = ref1.resolve();
      final PsiElement resolveResult2 = ref2.resolve();
      if (isUnder(resolveResult1, myPatternAsList) && isUnder(resolveResult2, candidates)) {
        return match.putDeclarationCorrespondence(resolveResult1, resolveResult2);
      }
      if (!equivalentResolve(resolveResult1, resolveResult2)) {
        return false;
      }
    }

    if (pattern instanceof PsiReturnStatement) {
      final PsiReturnStatement patternReturnStatement = ((PsiReturnStatement)pattern);
      return matchReturnStatement(patternReturnStatement, candidate, candidates, match);
    }

    if (pattern instanceof PsiReferenceExpression && candidate instanceof PsiReferenceExpression) {
      final PsiReferenceExpression patternRefExpr = ((PsiReferenceExpression)pattern);
      final PsiReferenceExpression candidateRefExpr = ((PsiReferenceExpression)candidate);
      if (patternRefExpr.getQualifierExpression() == null) {
        return match.registerInstanceExpression(candidateRefExpr.getQualifierExpression());
      }
    }

    PsiElement[] children1 = getFilteredChildren(pattern);
    PsiElement[] children2 = getFilteredChildren(candidate);
    if (children1.length != children2.length) return false;


    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      if (!matchPattern(child1, child2, candidates, match)) return false;
    }

    if (children1.length == 0) {
      if (pattern.getParent() instanceof PsiVariable && ((PsiVariable)pattern.getParent()).getNameIdentifier() == pattern) {
        return match.putDeclarationCorrespondence(pattern.getParent(), candidate.getParent());
      }
      if (!pattern.textMatches(candidate)) return false;
    }

    return true;
  }

  private boolean matchReturnStatement(final PsiReturnStatement patternReturnStatement,
                                       PsiElement candidate,
                                       List<PsiElement> candidates,
                                       Match match) {
    if (candidate instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)candidate).getExpression();
      if (expression instanceof PsiAssignmentExpression) {
        final PsiExpression returnValue = patternReturnStatement.getReturnValue();
        final PsiExpression rExpression = ((PsiAssignmentExpression)expression).getRExpression();
        if (!matchPattern(returnValue, rExpression, candidates, match)) return false;
        final PsiExpression lExpression = ((PsiAssignmentExpression)expression).getLExpression();
        return match.registerReturnValue(new ExpressionReturnValue(lExpression));
      }
      else return false;
    }
    else if (candidate instanceof PsiDeclarationStatement) {
      final PsiElement[] declaredElements = ((PsiDeclarationStatement)candidate).getDeclaredElements();
      if (declaredElements.length != 1) return false;
      if (!(declaredElements[0] instanceof PsiVariable)) return false;
      final PsiVariable variable = ((PsiVariable)declaredElements[0]);
      if (!matchPattern(patternReturnStatement.getReturnValue(), variable.getInitializer(), candidates, match)) return false;
      return match.registerReturnValue(new VariableReturnValue(variable));
    }
    else if (candidate instanceof PsiReturnStatement) {
      if (!match.registerReturnValue(ReturnStatementReturnValue.INSTANCE)) return false;
      return matchPattern(patternReturnStatement.getReturnValue(), ((PsiReturnStatement)candidate).getReturnValue(), candidates, match);
    }
    else return false;
  }

  private boolean equivalentResolve(final PsiElement resolveResult1, final PsiElement resolveResult2) {
    final boolean b = Comparing.equal(resolveResult1, resolveResult2);
    if (b) return b;
    if (resolveResult1 instanceof PsiMethod && resolveResult2 instanceof PsiMethod) {
      final PsiMethod method1 = ((PsiMethod)resolveResult1);
      final PsiMethod method2 = ((PsiMethod)resolveResult2);
      if (Arrays.asList(method1.findSuperMethods()).contains(method2)) return true;
      if (Arrays.asList(method2.findSuperMethods()).contains(method1)) return true;
      return false;
    }
    else {
      return false;
    }
  }

  private static boolean isUnder(PsiElement element, List<PsiElement> parents) {
    if (element == null) return false;
    for (int i = 0; i < parents.size(); i++) {
      final PsiElement parent = parents.get(i);
      if (PsiTreeUtil.isAncestor(parent, element, false)) return true;
    }
    return false;
  }

  private static PsiElement[] getFilteredChildren(PsiElement element1) {
    PsiElement[] children1 = element1.getChildren();
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    for (int i = 0; i < children1.length; i++) {
      PsiElement child = children1[i];
      if (!(child instanceof PsiWhiteSpace) && !(child instanceof PsiComment)) {
        array.add(child);
      }
    }
    return array.toArray(new PsiElement[array.size()]);
  }

}
