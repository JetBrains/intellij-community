package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;
import java.text.MessageFormat;

/**
 * @author ven
 */
public class SuspiciousCollectionsMethodCallsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection");

  private List<PsiMethod> myMethods = new ArrayList<PsiMethod>();
  private List<Integer> myIndices = new ArrayList<Integer>();

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    try {
      try {
        setupPatternMethods(aClass.getManager(), aClass.getResolveScope());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }

      return super.checkClass(aClass, manager, isOnTheFly);
    }
    finally {
      myIndices.clear();
      myMethods.clear();
    }
  }

  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    try {
      try {
        setupPatternMethods(method.getManager(), method.getResolveScope());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }

      return super.checkMethod(method, manager, isOnTheFly);
    }
    finally {
      myIndices.clear();
      myMethods.clear();
    }
  }

  private void setupPatternMethods(PsiManager manager, GlobalSearchScope searchScope) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = manager.getElementFactory();
    final PsiClass collectionClass = manager.findClass("java.util.Collection", searchScope);
    if (collectionClass != null) {
      addMethod(collectionClass.findMethodBySignature(elementFactory.createMethodFromText("boolean remove(Object o);", null), false), 0);
      addMethod(collectionClass.findMethodBySignature(elementFactory.createMethodFromText("boolean contains(Object o);", null), false), 0);
    }

    final PsiClass listClass = manager.findClass("java.util.List", searchScope);
    if (listClass != null) {
      addMethod(listClass.findMethodBySignature(elementFactory.createMethodFromText("boolean indexOf(Object o);", null), false), 0);
      addMethod(listClass.findMethodBySignature(elementFactory.createMethodFromText("boolean lastIndexOf(Object o);", null), false), 0);
    }

    final PsiClass mapClass = manager.findClass("java.util.Map", searchScope);
    if (mapClass != null) {
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean remove(Object o);", null), false), 0);
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean get(Object o);", null), false), 0);
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean containsKey(Object o);", null), false), 0);
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean containsValue(Object o);", null), false), 1);
    }

    myMethods.remove(null);
  }

  private void addMethod(final PsiMethod patternMethod, int typeParamIndex) {
    if (patternMethod != null) {
      myMethods.add(patternMethod);
      myIndices.add(new Integer(typeParamIndex));
    }
  }

  public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager manager) {
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    place.accept(new PsiRecursiveElementVisitor() {
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length != 1 || !(args[0].getType() instanceof PsiClassType)) return;

        final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        Iterator<Integer> indicesIterator = myIndices.iterator();
        for (Iterator<PsiMethod> methodsIterator = myMethods.iterator(); methodsIterator.hasNext();) {
          PsiMethod patternMethod = methodsIterator.next();
          Integer index = indicesIterator.next();
          if (!patternMethod.getName().equals(methodExpression.getReferenceName())) continue;
          final ResolveResult resolveResult = methodExpression.advancedResolve(false);
          final PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();
          if (psiMethod != null && isInheritorOrSelf(psiMethod, patternMethod)) {
            PsiTypeParameter[] typeParameters = psiMethod.getContainingClass().getTypeParameters();
            int i = index.intValue();
            if (typeParameters.length <= i) return;
            final PsiTypeParameter typeParameter = typeParameters[i];
            PsiType typeParamMapping = resolveResult.getSubstitutor().substitute(typeParameter);
            if (typeParamMapping != null) {
              if (!typeParamMapping.isConvertibleFrom(args[0].getType())) {
                final String message = MessageFormat.format("For no non-null object of type ''{0}'' can ''{1}'' return 'true'", new Object[]{
                  PsiFormatUtil.formatType(args[0].getType(), 0, PsiSubstitutor.EMPTY),
                  PsiFormatUtil.formatMethod(psiMethod, resolveResult.getSubstitutor(), PsiFormatUtil.SHOW_NAME |
                                                                                        PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                             PsiFormatUtil.SHOW_TYPE)});
                problems.add(manager.createProblemDescriptor(args[0], message,
                                                             null,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                return;
              }
            }
          }
        }
      }

      private boolean isInheritorOrSelf(PsiMethod inheritorCandidate, PsiMethod base) {
        PsiClass aClass = inheritorCandidate.getContainingClass();
        PsiClass bClass = base.getContainingClass();
        if (aClass == null || bClass == null) return false;
        PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(bClass, aClass, PsiSubstitutor.EMPTY);
        if (substitutor == null) return false;
        return MethodSignatureUtil.findMethodBySignature(bClass, inheritorCandidate.getSignature(substitutor), false) == base;
      }
    });

    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public String getDisplayName() {
    return "Suspicious collections method calls";
  }

  public String getGroupDisplayName() {
    return "Local Code Analysis";
  }

  public String getShortName() {
    return "SuspiciousMethodCalls";
  }
}
