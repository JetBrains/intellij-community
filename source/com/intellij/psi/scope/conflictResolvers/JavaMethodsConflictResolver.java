package com.intellij.psi.scope.conflictResolvers;

import com.intellij.openapi.util.Comparing;
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
    if (conflictsCount > 1){

      int maxCheckLevel = -1;
      int[] checkLevels = new int[conflictsCount];
      int index = 0;
      for (final CandidateInfo conflict1 : conflicts) {
        final MethodCandidateInfo method = (MethodCandidateInfo)conflict1;
        final int level = getCheckLevel(method);
        checkLevels[index++] = level;
        maxCheckLevel = Math.max(maxCheckLevel, level);
      }

      for(int i = conflictsCount - 1; i>= 0; i--){
        // check for level
        if (checkLevels[i] < maxCheckLevel){
          conflicts.remove(i);
        }
      }

      conflictsCount = conflicts.size();
      if(conflictsCount == 1) return conflicts.get(0);

      checkParametersNumber(conflicts, myArgumentsList.getExpressions().length);
      conflictsCount = conflicts.size();
      if(conflictsCount == 1) return conflicts.get(0);

      final boolean applicable = checkApplicability(conflicts);
      conflictsCount = conflicts.size();
      if(conflictsCount == 1) return conflicts.get(0);

      CandidateInfo[] conflictsArray;
      conflictsArray = conflicts.toArray(new CandidateInfo[conflictsCount]);
outer:
      for(int i = 0; i < conflictsCount; i++){
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

        // Specifics
        if (applicable){
          final CandidateInfo[] newConflictsArray = conflicts.toArray(new CandidateInfo[conflicts.size()]);

          for(int j = 0; j < i; j++){
            final CandidateInfo conflict = newConflictsArray[j];
            if (conflict == method) break;
            switch(isMoreSpecific((MethodCandidateInfo)method, (MethodCandidateInfo)conflict)){
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

  private void checkParametersNumber(final List<CandidateInfo> conflicts, final int argumentsCount) {
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

  private static boolean checkApplicability(List<CandidateInfo> conflicts) {
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

    return maxApplicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;
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

  private boolean checkOverriding(final CandidateInfo one, final CandidateInfo two){
    final PsiMethod method1 = (PsiMethod)one.getElement();
    final PsiMethod method2 = (PsiMethod)two.getElement();
    if (method1 != method2 && method1.getContainingClass() == method2.getContainingClass()) return false;
    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();
    if(params1.length != params2.length) return false;
    for(int i = 0; i < params1.length; i++){
      final PsiType type1 = one.getSubstitutor().substitute(params1[i].getType());
      final PsiType type2 = two.getSubstitutor().substitute(params2[i].getType());
      if (type1 == null || !type1.equals(type2)) {
        return false;
      }
    }
    return Comparing.equal(method1.getReturnType(), method2.getReturnType());
  }

  private Specifics isMoreSpecific(final MethodCandidateInfo info1, final MethodCandidateInfo info2) {
    PsiMethod method1 = info1.getElement();
    PsiMethod method2 = info2.getElement();
    final PsiClass class1 = method1.getContainingClass();
    final PsiClass class2 = method2.getContainingClass();
    Boolean isMoreSpecific = null;

    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();

    PsiExpression[] args = myArgumentsList.getExpressions();

    /*
    //check again, now that applicability check has been performed
    if (params1.length == args.length && params2.length != args.length) return Specifics.TRUE;
    if (params2.length == args.length && params1.length != args.length) return Specifics.FALSE;
    */

    for(int i = 0; i < Math.max(params1.length, params2.length); i++){
      final PsiParameter param1 = i >= params1.length ? params1[params1.length - 1] : params1[i];
      final PsiParameter param2 = i >= params2.length ? params2[params2.length - 1] : params2[i];
      final PsiType type1 = TypeConversionUtil.erasure(param1.getType());
      final PsiType type2 = TypeConversionUtil.erasure(param2.getType());
      assert type1 != null && type2 != null; //because erasure returns null for nulls only

      if (i < args.length) {
        final PsiType argType = args[i].getType();
        if (argType != null) {
          Boolean lessBoxing = isLessBoxing(argType, type1, type2);
          if (lessBoxing != null) {
            if (isMoreSpecific != null && !lessBoxing.equals(isMoreSpecific)) return Specifics.CONFLICT;
            isMoreSpecific = lessBoxing;
            continue;
          }
        }
      }

      final boolean assignable2From1 = type2.isAssignableFrom(type1);
      final boolean assignable1From2 = type1.isAssignableFrom(type2);
      if (assignable1From2 && assignable2From1) {
        continue;
      } else if (assignable1From2) {
        if (isMoreSpecific == Boolean.TRUE) return Specifics.CONFLICT;
        isMoreSpecific = Boolean.FALSE;
      } else if (assignable2From1) {
        if (isMoreSpecific == Boolean.FALSE) return Specifics.CONFLICT;
        isMoreSpecific = Boolean.TRUE;
      } else {
        return Specifics.CONFLICT;
      }
    }

    if (isMoreSpecific == null){
      if (class1 != class2){
        if (class2.isInheritor(class1, true)
            || class1.isInterface() && !class2.isInterface()){
          isMoreSpecific = Boolean.FALSE;
        }
        else if (class1.isInheritor(class2, true)
                 || class2.isInterface()){
            isMoreSpecific = Boolean.TRUE;
          }
      }
    }
    if (isMoreSpecific == null){
      return Specifics.CONFLICT;
    }

    return isMoreSpecific.booleanValue() ? Specifics.TRUE : Specifics.FALSE;
  }

  private Boolean isLessBoxing(PsiType argType, PsiType type1, PsiType type2) {
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
    if (boxing1) return Boolean.FALSE;
    return Boolean.TRUE;
  }

  public void handleProcessorEvent(PsiScopeProcessor.Event event, Object associatied){}

}
