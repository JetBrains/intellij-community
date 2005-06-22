package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author ven
 */
public class SuspiciousCollectionsMethodCallsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG =
    Logger.getInstance("#com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection");

  private void setupPatternMethods(PsiManager manager,
                                   GlobalSearchScope searchScope,
                                   List<PsiMethod> patternMethods,
                                   List<Integer> indices) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = manager.getElementFactory();
    final PsiClass collectionClass = manager.findClass("java.util.Collection", searchScope);
    if (collectionClass != null) {
      addMethod(collectionClass.findMethodBySignature(elementFactory.createMethodFromText("boolean remove(Object o);", null), false), 0,
                patternMethods, indices);
      addMethod(collectionClass.findMethodBySignature(elementFactory.createMethodFromText("boolean contains(Object o);", null), false), 0,
                patternMethods, indices);
    }

    final PsiClass listClass = manager.findClass("java.util.List", searchScope);
    if (listClass != null) {
      addMethod(listClass.findMethodBySignature(elementFactory.createMethodFromText("boolean indexOf(Object o);", null), false), 0,
                patternMethods, indices);
      addMethod(listClass.findMethodBySignature(elementFactory.createMethodFromText("boolean lastIndexOf(Object o);", null), false), 0,
                patternMethods, indices);
    }

    final PsiClass mapClass = manager.findClass("java.util.Map", searchScope);
    if (mapClass != null) {
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean remove(Object o);", null), false), 0,
                patternMethods, indices);
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean get(Object o);", null), false), 0,
                patternMethods, indices);
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean containsKey(Object o);", null), false), 0,
                patternMethods, indices);
      addMethod(mapClass.findMethodBySignature(elementFactory.createMethodFromText("boolean containsValue(Object o);", null), false), 1,
                patternMethods, indices);
    }

    patternMethods.remove(null);
  }

  private void addMethod(final PsiMethod patternMethod, int typeParamIndex, List<PsiMethod> patternMethods, List<Integer> indices) {
    if (patternMethod != null) {
      patternMethods.add(patternMethod);
      indices.add(new Integer(typeParamIndex));
    }
  }

  public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager manager) {
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    final List<PsiMethod> patternMethods = new ArrayList<PsiMethod>();
    final List<Integer> indices = new ArrayList<Integer>();
    try {
      setupPatternMethods(place.getManager(), place.getResolveScope(), patternMethods, indices);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }

    place.accept(new PsiRecursiveElementVisitor() {
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length != 1) return;
        PsiType argType = args[0].getType();
        if (argType instanceof PsiPrimitiveType) {
          argType = ((PsiPrimitiveType)argType).getBoxedType(methodCall.getManager(), methodCall.getResolveScope());
        }

        if (!(argType instanceof PsiClassType)) return;

        final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
        final PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();
        if (psiMethod == null) return;

        Iterator<Integer> indicesIterator = indices.iterator();
        for (Iterator<PsiMethod> methodsIterator = patternMethods.iterator(); methodsIterator.hasNext();) {
          PsiMethod patternMethod = methodsIterator.next();
          Integer index = indicesIterator.next();
          if (!patternMethod.getName().equals(methodExpression.getReferenceName())) continue;
          if (isInheritorOrSelf(psiMethod, patternMethod)) {
            PsiTypeParameter[] typeParameters = psiMethod.getContainingClass().getTypeParameters();
            int i = index.intValue();
            if (typeParameters.length <= i) return;
            final PsiTypeParameter typeParameter = typeParameters[i];
            PsiType typeParamMapping = resolveResult.getSubstitutor().substitute(typeParameter);
            if (typeParamMapping != null) {
              String message = null;
              if (!typeParamMapping.isAssignableFrom(argType)) {
                if (!typeParamMapping.isConvertibleFrom(argType)) {
                  message = MessageFormat.format("Calling ''{1}'' may not succeed for non-null objects of type ''{0}''",
                                                 PsiFormatUtil.formatType(argType, 0, PsiSubstitutor.EMPTY),
                                                 PsiFormatUtil.formatMethod(psiMethod, resolveResult.getSubstitutor(),
                                                                            PsiFormatUtil.SHOW_NAME |
                                                                            PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                                                            PsiFormatUtil.SHOW_TYPE));
                }
                else {
                  message = MessageFormat.format("Suspicious call to ''{0}''",
                                                 PsiFormatUtil.formatMethod(psiMethod, resolveResult.getSubstitutor(),
                                                                            PsiFormatUtil.SHOW_NAME |
                                                                            PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                                                            PsiFormatUtil.SHOW_TYPE));
                }
              }
              if (message != null) {
                problems.add(manager.createProblemDescriptor(args[0], message,
                                                             (LocalQuickFix [])null,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              }
            }
            return;
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
    return GroupNames.BUGS_GROUP_NAME;
  }

  public String getShortName() {
    return "SuspiciousMethodCalls";
  }
}
