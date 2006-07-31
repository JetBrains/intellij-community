package com.intellij.psi.scope.conflictResolvers;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 19:41:51
 * To change this template use Options | File Templates.
 */
public class JavaMethodsConflictResolver implements PsiConflictResolver{
  private PsiExpressionList myArgumentsList;

  public JavaMethodsConflictResolver(PsiExpressionList list){
    myArgumentsList = list;
  }

  public CandidateInfo resolveConflict(List<CandidateInfo> conflicts){
    int conflictsCount = conflicts.size();
    if (conflictsCount <= 0) return null;
    if (conflictsCount == 1) return conflicts.get(0);
    if (conflictsCount > 1) {

      int maxCheckLevel = -1;
      int[] checkLevels = new int[conflictsCount];
      int index = 0;
      for (final CandidateInfo conflict1 : conflicts) {
        final MethodCandidateInfo method = (MethodCandidateInfo)conflict1;
        final int level = getCheckLevel(method);
        checkLevels[index++] = level;
        maxCheckLevel = Math.max(maxCheckLevel, level);
      }

      for (int i = conflictsCount - 1; i >= 0; i--) {
        // check for level
        if (checkLevels[i] < maxCheckLevel) {
          conflicts.remove(i);
        }
      }

      conflictsCount = conflicts.size();
      if (conflictsCount == 1) return conflicts.get(0);

      checkParametersNumber(conflicts, myArgumentsList.getExpressions().length);
      conflictsCount = conflicts.size();
      if (conflictsCount == 1) return conflicts.get(0);

      final int applicabilityLevel = checkApplicability(conflicts);
      final boolean applicable = applicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;

      conflictsCount = conflicts.size();
      if (conflictsCount == 1) return conflicts.get(0);

      CandidateInfo[] conflictsArray;
      conflictsArray = conflicts.toArray(new CandidateInfo[conflictsCount]);

      outer:
      for (int i = 0; i < conflictsCount; i++) {
        final CandidateInfo method = conflictsArray[i];
        // check overriding
        for (final CandidateInfo info : conflicts) {
          if (info == method) break;
          // candidates should go in order of class hierarchy traversal
          // in order for this to work
          if (checkOverriding(method, info)) {
            conflicts.remove(method);
            continue outer;
          }
        }
      }

      conflictsCount = conflicts.size();
      if (conflictsCount == 1) return conflicts.get(0);

      // Specifics
      if (applicable) {
        final CandidateInfo[] newConflictsArray = conflicts.toArray(new CandidateInfo[conflicts.size()]);
        for (int i = 0; i < conflictsCount; i++) {
          final CandidateInfo method = newConflictsArray[i];
          for (int j = 0; j < i; j++) {
            final CandidateInfo conflict = newConflictsArray[j];
            if (conflict == method) break;
            switch (isMoreSpecific(method, conflict, applicabilityLevel)) {
              case TRUE:
                conflicts.remove(conflict);
                break;
              case FALSE:
                conflicts.remove(method);
                continue;
              case CONFLICT:
                break;
            }
          }
        }
      }
    }
    if (conflicts.size() == 1){
      return conflicts.get(0);
    }

    return null;
  }

  private static void checkParametersNumber(final List<CandidateInfo> conflicts, final int argumentsCount) {
    boolean parametersNumberMatch = false;
    for (CandidateInfo info : conflicts) {
      if (info instanceof MethodCandidateInfo) {
        final PsiMethod method = ((MethodCandidateInfo)info).getElement();
        if (method.isVarArgs()) return;
        if (method.getParameterList().getParameters().length == argumentsCount) {
          parametersNumberMatch = true;
        }
      }
    }

    if (parametersNumberMatch) {
      for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
        CandidateInfo info = iterator.next();
        if (info instanceof MethodCandidateInfo) {
          final PsiMethod method = ((MethodCandidateInfo)info).getElement();
          if (method.getParameterList().getParameters().length != argumentsCount) {
            iterator.remove();
          }
        }
      }
    }
  }

  private static int checkApplicability(List<CandidateInfo> conflicts) {
    int maxApplicabilityLevel = 0;
    boolean toFilter = false;
    for (CandidateInfo conflict : conflicts) {
      final int level = ((MethodCandidateInfo)conflict).getApplicabilityLevel();
      if (maxApplicabilityLevel > 0 && maxApplicabilityLevel != level) {
        toFilter = true;
      }
      if (level > maxApplicabilityLevel) {
        maxApplicabilityLevel = level;
      }
    }

    if (toFilter) {
      for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
        CandidateInfo info = iterator.next();
        final int level = ((MethodCandidateInfo)info).getApplicabilityLevel();  //cached
        if (level < maxApplicabilityLevel) {
          iterator.remove();
        }
      }
    }

    return maxApplicabilityLevel;
  }

  private static int getCheckLevel(MethodCandidateInfo method){
    boolean visible = method.isAccessible();// && !method.myStaticProblem;
    boolean available = method.isStaticsScopeCorrect();
    return (visible ? 1 : 0) << 2 |
           (available ? 1 : 0) << 1 |
           (!(method.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) ? 1 : 0);
  }

  private enum Specifics {
    FALSE,
    TRUE,
    CONFLICT
  }

  private static boolean checkOverriding(final CandidateInfo one, final CandidateInfo two){
    final PsiMethod method1 = (PsiMethod)one.getElement();
    final PsiMethod method2 = (PsiMethod)two.getElement();
    if (method1 != method2 && method1.getContainingClass() == method2.getContainingClass()) return false;
    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();
    if(params1.length != params2.length) return false;
    final PsiSubstitutor substitutor1 = one.getSubstitutor();
    final PsiSubstitutor substitutor2 = two.getSubstitutor();
    for(int i = 0; i < params1.length; i++){
      final PsiType type1 = substitutor1.substitute(params1[i].getType());
      final PsiType type2 = substitutor2.substitute(params2[i].getType());
      if (type1 == null || !type1.equals(type2)) {
        return false;
      }
    }

    return true;
  }

  private Specifics checkSubtyping(PsiType type1, PsiType type2, final PsiType argType) {
    final Specifics lessBoxing = isLessBoxing(argType, type1, type2);
    if (lessBoxing != null) return lessBoxing;

    final boolean assignable2From1 = type2.isAssignableFrom(type1);
    final boolean assignable1From2 = type1.isAssignableFrom(type2);
    if (assignable1From2 || assignable2From1) {
      if (assignable1From2 && assignable2From1) {
        return null;
      }

      return assignable1From2 ? Specifics.FALSE : Specifics.TRUE;
    }

    return Specifics.CONFLICT;
  }

  private Specifics isLessBoxing(PsiType argType, PsiType type1, PsiType type2) {
    if (argType == null) return null;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(myArgumentsList);
    if (type1 instanceof PsiClassType) {
      type1 = ((PsiClassType)type1).setLanguageLevel(languageLevel);
    }
    if (type2 instanceof PsiClassType) {
      type2 = ((PsiClassType)type2).setLanguageLevel(languageLevel);
    }

    final boolean boxing1 = TypeConversionUtil.boxingConversionApplicable(type1, argType);
    final boolean boxing2 = TypeConversionUtil.boxingConversionApplicable(type2, argType);
    if (boxing1 == boxing2) return null;
    if (boxing1) return Specifics.FALSE;
    return Specifics.TRUE;
  }

  private Specifics isMoreSpecific(final CandidateInfo info1, final CandidateInfo info2, final int applicabilityLevel) {
    PsiMethod method1 = (PsiMethod)info1.getElement();
    PsiMethod method2 = (PsiMethod)info2.getElement();
    final PsiClass class1 = method1.getContainingClass();
    final PsiClass class2 = method2.getContainingClass();
    Specifics isMoreSpecific = null;

    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();

    PsiExpression[] args = myArgumentsList.getExpressions();

    //check again, now that applicability check has been performed
    if (params1.length == args.length && params2.length != args.length) return Specifics.TRUE;
    if (params2.length == args.length && params1.length != args.length) return Specifics.FALSE;

    final PsiTypeParameter[] typeParameters1 = method1.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = method2.getTypeParameters();
    PsiSubstitutor substitutor1 = info1.getSubstitutor();
    PsiSubstitutor substitutor2 = info2.getSubstitutor();

    final int max = Math.max(params1.length, params2.length);
    PsiType[] types1 = new PsiType[max];
    PsiType[] types2 = new PsiType[max];
    for (int i = 0; i < max; i++) {
      PsiType type1 = params1[Math.min(i, params1.length - 1)].getType();
      PsiType type2 = params2[Math.min(i, params2.length - 1)].getType();
      if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
        type1 = type1 instanceof PsiEllipsisType ? ((PsiArrayType)type1).getComponentType() : type1;
        type2 = type2 instanceof PsiEllipsisType ? ((PsiArrayType)type2).getComponentType() : type2;
      }

      types1[i] = type1;
      types2[i] = type2;
    }

    if (typeParameters1.length == 0 || typeParameters2.length == 0) {
      if (typeParameters1.length > 0) {
        final PsiResolveHelper resolveHelper = myArgumentsList.getManager().getResolveHelper();
        substitutor1 = resolveHelper.inferTypeArguments(typeParameters1, types1, types2, PsiUtil.getLanguageLevel(myArgumentsList));
      }
      if (typeParameters2.length > 0) {
        final PsiResolveHelper resolveHelper = myArgumentsList.getManager().getResolveHelper();
        substitutor2 = resolveHelper.inferTypeArguments(typeParameters2, types2, types1, PsiUtil.getLanguageLevel(myArgumentsList));
      }
    }
    else {
      substitutor1 = createRawSubstitutor(typeParameters1);
      substitutor2 = createRawSubstitutor(typeParameters2);
    }

    for (int i = 0; i < types1.length; i++) {
      PsiType type1 = substitutor1.substitute(types1[i]);
      PsiType type2 = substitutor2.substitute(types2[i]);
      PsiType argType = i < args.length ? args[i].getType() : null;

      final Specifics specifics = checkSubtyping(type1, type2, argType);
      if (specifics == null) continue;
      switch (specifics) {
        case TRUE:
          if (isMoreSpecific == Specifics.FALSE) return Specifics.CONFLICT;
          isMoreSpecific = specifics;
          break;
        case FALSE:
          if (isMoreSpecific == Specifics.TRUE) return Specifics.CONFLICT;
          isMoreSpecific = specifics;
          break;
        case CONFLICT:
          return Specifics.CONFLICT;
      }
    }
    if (isMoreSpecific == null) {
      if (class1 != class2) {
        if (class2.isInheritor(class1, true) || class1.isInterface() && !class2.isInterface()) {
          isMoreSpecific = Specifics.FALSE;
        }
        else if (class1.isInheritor(class2, true) || class2.isInterface()) {
          isMoreSpecific = Specifics.TRUE;
        }
      }
    }
    if (isMoreSpecific == null) {
      return Specifics.CONFLICT;
    }

    return isMoreSpecific;
  }

  private static PsiSubstitutor createRawSubstitutor(final PsiTypeParameter[] typeParameters) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (final PsiTypeParameter typeParameter : typeParameters) {
      substitutor = substitutor.put(typeParameter, null);
    }

    return substitutor;
  }

  public void handleProcessorEvent(PsiScopeProcessor.Event event, Object associatied){}
}
