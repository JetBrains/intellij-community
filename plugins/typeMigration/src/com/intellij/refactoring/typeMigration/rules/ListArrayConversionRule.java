/*
 * User: anna
 * Date: 08-Aug-2008
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class ListArrayConversionRule extends TypeConversionRule {
  public TypeConversionDescriptorBase findConversion(final PsiType from,
                                                 final PsiType to,
                                                 PsiMember member,
                                                 final PsiExpression context,
                                                 final TypeMigrationLabeler labeler) {
    PsiExpression expression = context;
    PsiClassType classType = from instanceof PsiClassType ? (PsiClassType)from : to instanceof PsiClassType ? (PsiClassType)to : null;
    PsiArrayType arrayType = from instanceof PsiArrayType ? (PsiArrayType)from : to instanceof PsiArrayType ? (PsiArrayType)to : null;

    if (classType == null || arrayType == null) return null;
    final PsiType collectionType = evaluateCollectionsType(classType, expression);
    if (collectionType == null) return null;

    if (member == null) {
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(context, PsiMethodCallExpression.class);
      if (callExpression != null) {
        member = callExpression.resolveMethod();
        expression = callExpression;
      }
    }
    if (member instanceof PsiMethod) {
      TypeConversionDescriptor descriptor = changeCollectionCallsToArray((PsiMethod)member, context, collectionType, arrayType);
      if (descriptor != null) return descriptor;

      @NonNls final String memberName = member.getName();
      assert memberName != null;
      if (memberName.equals("sort")) {
        if (((PsiMethod)member).getParameterList().getParametersCount() == 1) {
          descriptor = new TypeConversionDescriptor("Arrays.sort($qualifier$)", "Collections.sort($qualifier$)", expression);
        }
        else {
          descriptor =
            new TypeConversionDescriptor("Arrays.sort($qualifier$, $expr$)", "Collections.sort($qualifier$, $expr$)", expression);
        }
      }
      else if (memberName.equals("binarySearch")) {
        if (((PsiMethod)member).getParameterList().getParametersCount() == 2) {
          descriptor =
            new TypeConversionDescriptor("Arrays.binarySearch($qualifier$, $key$)", "Collections.binarySearch($qualifier$, $key$)",
                                         expression);
        }
        else {
          descriptor = new TypeConversionDescriptor("Arrays.binarySearch($qualifier$, $key$, $comparator$)",
                                                    "Collections.binarySearch($qualifier$, $key$, $comparator$)", expression);
        }
      }
      else if (memberName.equals("asList")) {
        if (((PsiMethod)member).getParameterList().getParametersCount() == 1) {
          descriptor =
            new TypeConversionDescriptor("Arrays.asList($qualifier$)", "$qualifier$", expression);
        }
      }
      else if (memberName.equals("fill")) {
        descriptor = new TypeConversionDescriptor("Arrays.fill($qualifier$, $filler$)", "Collections.fill($qualifier$, $filler$)", expression);
      }
      if (descriptor != null) {
        return from instanceof PsiClassType
               ? new TypeConversionDescriptor(descriptor.getReplaceByString(), descriptor.getStringToReplace(), descriptor.getExpression())
               : descriptor;
      }
    }

    if (member instanceof PsiField && member.getName().equals("length")) {
      return new TypeConversionDescriptor("$qualifier$.length", "$qualifier$.size()");
    }

    final PsiElement parent = context.getParent();
    if (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getLExpression() == context) {
      if (TypeConversionUtil.isAssignable(collectionType, arrayType.getComponentType())) {
        return new TypeConversionDescriptor("$qualifier$[$idx$] = $expr$", "$qualifier$.set($idx$, $expr$)",
                                            (PsiExpression)parent);
      }
    }
    else if (context instanceof PsiArrayAccessExpression && TypeConversionUtil.isAssignable(arrayType.getComponentType(), collectionType)) {
      return new TypeConversionDescriptor("$qualifier$[$idx$]", "$qualifier$.get($idx$)");
    }

    return null;
  }

  @Nullable
  public static PsiType evaluateCollectionsType(PsiClassType classType, PsiExpression expression) {
    final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(classType);
    if (classResolveResult != null) {
      final PsiClass psiClass = classResolveResult.getElement();
      if (psiClass != null) {
        final GlobalSearchScope allScope = GlobalSearchScope.allScope(psiClass.getProject());
        final PsiClass collectionClass =
          JavaPsiFacade.getInstance(psiClass.getProject()).findClass(CommonClassNames.JAVA_UTIL_LIST, allScope);
        if (collectionClass != null && InheritanceUtil.isInheritorOrSelf(psiClass, collectionClass, true)) {
          final PsiSubstitutor derivedSubstitutor = classResolveResult.getSubstitutor();
          if (PsiUtil.isRawSubstitutor(psiClass, derivedSubstitutor)) return null;
          final PsiSubstitutor substitutor =
            TypeConversionUtil.getClassSubstitutor(collectionClass, psiClass, derivedSubstitutor);
          assert substitutor != null;
          final PsiType type = substitutor.substitute(collectionClass.getTypeParameters()[0]);
          assert type != null;
          return PsiImplUtil.normalizeWildcardTypeByPosition(type, expression);
        }
      }
    }
    return null;
  }

  @Override
  public Pair<PsiType, PsiType> bindTypeParameters(final PsiType from,
                                                   final PsiType to,
                                                   final PsiMethod method,
                                                   final PsiExpression context,
                                                   final TypeMigrationLabeler labeler) {
    if (findConversion(from, to, method, context, labeler) == null) return null;
    if (from instanceof PsiArrayType && to instanceof PsiClassType) {
      final PsiType collectionType = evaluateCollectionsType((PsiClassType)to, context);
      if (collectionType != null) {
        return Pair.create(((PsiArrayType)from).getComponentType(), collectionType);
      }
    }
    if (to instanceof PsiArrayType && from instanceof PsiClassType) {
      final PsiType collectionType = evaluateCollectionsType((PsiClassType)from, context);
      if (collectionType != null) {
        return Pair.create(collectionType, ((PsiArrayType)to).getComponentType());

      }
    }
    return null;
  }

  @Nullable
  private static TypeConversionDescriptor changeCollectionCallsToArray(final PsiMethod method,
                                                                       final PsiElement context,
                                                                       PsiType collectionType,
                                                                       PsiArrayType arrayType) {
    @NonNls final String methodName = method.getName();
    if (methodName.equals("toArray")) {
      if (method.getParameterList().getParameters().length == 0) {
        return new TypeConversionDescriptor("$qualifier$.toArray()", "$qualifier$");
      }
      return new TypeConversionDescriptor("$qualifier$.toArray($expr$)", "$qualifier$");
    }
    else if (methodName.equals("size")) {
      return new TypeConversionDescriptor("$qualifier$.size()", "$qualifier$.length");
    }
    else if (methodName.equals("get")) {
      if (TypeConversionUtil.isAssignable(collectionType, arrayType.getComponentType())) {
        return new TypeConversionDescriptor("$qualifier$.get($i$)", "$qualifier$[$i$]", PsiTreeUtil.getParentOfType(context, PsiMethodCallExpression.class));
      }
    }
    else if (methodName.equals("set")) {
      if (TypeConversionUtil.isAssignable(arrayType.getComponentType(), collectionType)) {
        return new TypeConversionDescriptor("$qualifier$.set($i$, $val$)", "$qualifier$[$i$] = $val$");
      }
    }
    return null;
  }

}