/*
 * Highlight method problems
 * User: cdr
 * Date: Aug 14, 2002
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.xml.util.XmlUtil;

import java.text.MessageFormat;
import java.util.*;

public class HighlightMethodUtil {
  static final String WRONG_METHOD_ARGUMENTS = "''{0}'' in ''{1}'' cannot be applied to ''{2}''";
  public static final String EXCEPTION_NEVER_THROWN_IN_METHOD = "Exception ''{0}'' is never thrown in the method";
  private static final String CANNOT_RESOLVE_METHOD = "Cannot resolve method ''{0}''";
  private static final String INCOMPATIBLE_RETURN_TYPE = "attempting to use incompatible return type";
  private static final String CANNOT_RESOLVE_CONSTRUCTOR = "Cannot resolve constructor ''{0}''";
  private static final String WRONG_CONSTRUCTOR_ARGUMENTS = "''{0}'' cannot be applied to ''{2}''";

  public static String createClashMethodMessage(PsiMethod method1, PsiMethod method2, boolean showContainingClasses) {
    String pattern = "''{0}''";
    if (showContainingClasses) {
      pattern += " in ''{2}''";
    }
    pattern += " clashes with ''{1}''";
    if (showContainingClasses) {
      pattern += " in ''{3}''";
    }
    String message = MessageFormat.format(pattern,
                                          new Object[]{
                                            HighlightUtil.formatMethod(method1),
                                            HighlightUtil.formatMethod(method2),
                                            HighlightUtil.formatClass(method1.getContainingClass()),
                                            HighlightUtil.formatClass(method2.getContainingClass())
                                          });
    return message;
  }

  //@top
  static HighlightInfo checkMethodWeakerPrivileges(MethodSignatureBackedByPsiMethod methodSignature,
                                                   List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                   boolean includeRealPositionInfo) {
    final PsiMethod method = methodSignature.getMethod();
    final PsiModifierList modifierList = method.getModifierList();
    if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) return null;
    final int accessLevel = PsiUtil.getAccessLevel(modifierList);
    final String accessModifier = PsiUtil.getAccessModifier(accessLevel);
    for (int i = 0; i < superMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod superMethodSignature = superMethodSignatures.get(i);
      PsiMethod superMethod = superMethodSignature.getMethod();
      final int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
      if (accessLevel < superAccessLevel) {
        String message = MessageFormat.format("{0}; attempting to assign weaker access privileges (''{1}''); was ''{2}''",
                                              new Object[]{
                                                createClashMethodMessage(method, superMethod, true),
                                                accessModifier,
                                                PsiUtil.getAccessModifier(superAccessLevel)
                                              });

        TextRange textRange;
        if (includeRealPositionInfo) {
          if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
            textRange = method.getNameIdentifier().getTextRange();
          }
          else {
            PsiElement keyword = PsiUtil.findModifierInList(modifierList, accessModifier);
            textRange = keyword.getTextRange();
          }
        }
        else {
          textRange = new TextRange(0, 0);
        }

        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(method, PsiUtil.getAccessModifier(superAccessLevel), true));
        return highlightInfo;
      }
    }
    return null;
  }

  //@top
  static HighlightInfo checkMethodIncompatibleReturnType(MethodSignatureBackedByPsiMethod methodSignature,
                                                         List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                         boolean includeRealPositionInfo) {
    final PsiMethod method = methodSignature.getMethod();
    PsiType returnType = methodSignature.getSubstitutor().substitute(method.getReturnType());
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    for (int i = 0; i < superMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod superMethodSignature = superMethodSignatures.get(i);
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiType superReturnType = superMethodSignature.getSubstitutor().substitute(superMethod.getReturnType());
      if (returnType == null || superReturnType == null || method == superMethod) continue;
      final PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      // EJB override rules are tricky, they are checked elsewhere in EJB Highlighter
      if (!Comparing.strEqual(method.getName(), superMethod.getName())
          || method.getParameterList().getParameters().length != superMethod.getParameterList().getParameters().length) {
        continue;
      }

      HighlightInfo highlightInfo = checkSuperMethodSignature(superMethod, superMethodSignature, superReturnType, method, methodSignature,
                                                              returnType, includeRealPositionInfo, INCOMPATIBLE_RETURN_TYPE, method);
      if (highlightInfo != null) return highlightInfo;
    }

    return null;
  }

  private static HighlightInfo checkSuperMethodSignature(PsiMethod superMethod,
                                                         final MethodSignatureBackedByPsiMethod superMethodSignature,
                                                         PsiType superReturnType,
                                                         final PsiMethod method,
                                                         MethodSignatureBackedByPsiMethod methodSignature,
                                                         PsiType returnType,
                                                         boolean includeRealPositionInfo,
                                                         final String detailMessage,
                                                         PsiMethod methodToHighlight) {
    if (superReturnType == null) return null;
    PsiType substitutedSuperReturnType;
    if (!superMethodSignature.isRaw()) {
      final PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature,
                                                                                                        superMethodSignature);
      substitutedSuperReturnType = unifyingSubstitutor == null
                                   ? superReturnType
                                   : unifyingSubstitutor.substitute(superMethodSignature.getSubstitutor().substitute(superReturnType));
    }
    else {
      substitutedSuperReturnType = TypeConversionUtil.erasure(superReturnType);
    }

    if (!returnType.equals(superReturnType) &&
        (!(returnType.getDeepComponentType() instanceof PsiClassType) ||
         !(substitutedSuperReturnType.getDeepComponentType() instanceof PsiClassType) ||
         LanguageLevel.JDK_1_5.compareTo(method.getManager().getEffectiveLanguageLevel()) > 0 ||
         !TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType))) {
      return createIncompatibleReturnTypeMessage(methodToHighlight, method, superMethod, includeRealPositionInfo,
                                                 substitutedSuperReturnType, returnType, detailMessage);
    }
    return null;
  }

  private static HighlightInfo createIncompatibleReturnTypeMessage(final PsiMethod methodToHighlight,
                                                                   final PsiMethod method,
                                                                   PsiMethod superMethod,
                                                                   boolean includeRealPositionInfo,
                                                                   final PsiType substitutedSuperReturnType,
                                                                   PsiType returnType,
                                                                   String detailMessage) {
    String message = MessageFormat.format("{0}; {1}", new Object[]{createClashMethodMessage(method, superMethod, true), detailMessage});
    TextRange textRange = includeRealPositionInfo ? methodToHighlight.getReturnTypeElement().getTextRange() : new TextRange(0, 0);
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    QuickFixAction.registerQuickFixAction(errorResult, new MethodReturnFix(method, substitutedSuperReturnType, false));
    QuickFixAction.registerQuickFixAction(errorResult, new SuperMethodReturnFix(superMethod, returnType));

    return errorResult;
  }

  //@top
  static HighlightInfo checkMethodOverridesFinal(MethodSignatureBackedByPsiMethod methodSignature,
                                                 List<MethodSignatureBackedByPsiMethod> superMethodSignatures) {
    final PsiMethod method = methodSignature.getMethod();
    for (int i = 0; i < superMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod superMethodSignature = superMethodSignatures.get(i);
      PsiMethod superMethod = superMethodSignature.getMethod();
      // strange things happen when super method is from Object and method from interface
      if (superMethod != null
          && superMethod.hasModifierProperty(PsiModifier.FINAL)) {
        String message = MessageFormat.format("''{0}'' cannot override ''{1}'' in ''{2}''; overridden method is final",
                                              new Object[]{
                                                HighlightUtil.formatMethod(method),
                                                HighlightUtil.formatMethod(superMethod),
                                                HighlightUtil.formatClass(superMethod.getContainingClass()),
                                              });
        TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
        HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      textRange,
                                                                      message);
        QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(superMethod, PsiModifier.FINAL, false, true));
        return errorResult;
      }
    }
    return null;
  }

  //@top
  static HighlightInfo checkMethodIncompatibleThrows(MethodSignatureBackedByPsiMethod methodSignature,
                                                     List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                     boolean includeRealPositionInfo) {
    final PsiMethod method = methodSignature.getMethod();
    PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
    final PsiJavaCodeReferenceElement[] referenceElements;
    List<PsiClassType> checkedExceptions = new ArrayList<PsiClassType>();
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    List<PsiElement> exceptionContexts;
    if (includeRealPositionInfo) {
      exceptionContexts = new ArrayList<PsiElement>();
      referenceElements = method.getThrowsList().getReferenceElements();
    }
    else {
      exceptionContexts = null;
      referenceElements = null;
    }

    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exception = exceptions[i];
      if (!ExceptionUtil.isUncheckedException(exception)) {
        checkedExceptions.add(exception);
        if (includeRealPositionInfo) {
          PsiJavaCodeReferenceElement exceptionRef = referenceElements[i];
          exceptionContexts.add(exceptionRef);
        }
      }
    }
    for (int i = 0; i < superMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod superMethodSignature = superMethodSignatures.get(i);
      PsiMethod superMethod = superMethodSignature.getMethod();
      int index = getExtraExceptionNum(superMethod, checkedExceptions, aClass);
      if (index != -1) {
        PsiClassType exception = checkedExceptions.get(index);
        String message = MessageFormat.format("{0}; overridden method does not throw ''{1}''",
                                              new Object[]{
                                                createClashMethodMessage(method, superMethod, true),
                                                HighlightUtil.formatType(exception)
                                              });
        TextRange textRange;
        if (includeRealPositionInfo) {
          PsiElement exceptionContext = exceptionContexts.get(index);
          textRange = exceptionContext.getTextRange();
        }
        else {
          textRange = new TextRange(0, 0);
        }
        HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
        QuickFixAction.registerQuickFixAction(errorResult, new MethodThrowsFix(method, exception, false));
        QuickFixAction.registerQuickFixAction(errorResult, new MethodThrowsFix(superMethod, exception, true, true));
        return errorResult;
      }
    }
    return null;
  }

  // return number of exception  which was not declared in super method or -1
  private static int getExtraExceptionNum(PsiMethod superMethod,
                                          List<PsiClassType> checkedExceptions,
                                          PsiClass aClass) {
    final PsiClass superClass = superMethod.getContainingClass();
    if (superClass == null) return -1;
    PsiSubstitutor superClassSubstitutor = aClass.isInheritor(superClass, true)
                                           ?
                                           TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass,
                                                                                       PsiSubstitutor.EMPTY)
                                           : PsiSubstitutor.EMPTY;
    // due to "sibling" inheritance superClass maybe not real super class
    for (int i = 0; i < checkedExceptions.size(); i++) {
      PsiType exception = checkedExceptions.get(i);
      if (!isMethodThrows(superMethod, superClassSubstitutor, exception)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isMethodThrows(PsiMethod method, PsiSubstitutor substitutorForMethod, PsiType exception) {
    PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
    for (int i = 0; i < thrownExceptions.length; i++) {
      PsiType thrownException = substitutorForMethod.substitute(thrownExceptions[i]);
      if (TypeConversionUtil.isAssignable(thrownException, exception)) return true;
    }
    return false;
  }

  //@top
  static HighlightInfo checkExceptionsNeverThrown(PsiJavaCodeReferenceElement referenceElement) {
    if (!(referenceElement.getParent() instanceof PsiReferenceList)) return null;
    final PsiReferenceList referenceList = (PsiReferenceList)referenceElement.getParent();
    if (!(referenceList.getParent() instanceof PsiMethod)) return null;
    final PsiMethod method = (PsiMethod)referenceList.getParent();
    if (referenceList != method.getThrowsList()) return null;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;

    PsiManager manager = referenceElement.getManager();
    final PsiClassType exceptionType = manager.getElementFactory().createType(referenceElement);
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(exceptionType)) return null;
    if (!DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().isToolEnabled(HighlightDisplayKey.UNUSED_THROWS_DECL)) {
      return null;
    }

    final PsiCodeBlock body = method.getBody();
    if (body == null) return null;

    final PsiModifierList modifierList = method.getModifierList();
    final PsiClass containingClass = method.getContainingClass();
    if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)
        && !modifierList.hasModifierProperty(PsiModifier.STATIC)
        && !modifierList.hasModifierProperty(PsiModifier.FINAL)
        && !method.isConstructor()
        && !(containingClass instanceof PsiAnonymousClass)
        && !(containingClass != null && containingClass.hasModifierProperty(PsiModifier.FINAL))) {
      return null;
    }

    PsiClassType[] types = ExceptionUtil.collectUnhandledExceptions(body, method);
    Collection<PsiClassType> unhandled = new HashSet<PsiClassType>(Arrays.asList(types));
    if (method.isConstructor()) {
      // there may be field initializer throwing exception
      // that exception must be caught in the constructor
      PsiField[] fields = aClass.getFields();
      for (int i = 0; i < fields.length; i++) {
        final PsiField field = fields[i];
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) continue;
        unhandled.addAll(Arrays.asList(ExceptionUtil.collectUnhandledExceptions(initializer, field)));
      }
    }

    for (Iterator<PsiClassType> iterator = unhandled.iterator(); iterator.hasNext();) {
      PsiClassType unhandledException = iterator.next();
      if (unhandledException.isAssignableFrom(exceptionType) ||
          exceptionType.isAssignableFrom(unhandledException)) {
        return null;
      }
    }

    String description = MessageFormat.format(EXCEPTION_NEVER_THROWN_IN_METHOD, new Object[]{HighlightUtil.formatType(exceptionType)});
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_THROWS_DECL, referenceElement, description);

    QuickFixAction.registerQuickFixAction(errorResult, new MethodThrowsFix(method, exceptionType, false));
    return errorResult;
  }

  //@top
  public static HighlightInfo checkMethodCall(PsiMethodCallExpression methodCall,
                                              PsiExpressionList list,
                                              PsiResolveHelper resolveHelper) {
    PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
    final ResolveResult resolveResult = referenceToMethod.advancedResolve(true);
    final PsiElement element = resolveResult.getElement();

    boolean isDummy = false;
    final boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
    if (isThisOrSuper) {
      // super(..) or this(..)
      final PsiMember constructor = PsiUtil.findEnclosingConstructorOrInitializer(methodCall);
      if (!(constructor instanceof PsiMethod)) {
        final String description = MessageFormat.format("Call to ''{0}()'' allowed in constructor only",
                                                        new Object[]{referenceToMethod.getReferenceName()});
        HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      methodCall,
                                                                      description);
        return errorResult;
      }
      if (list.getExpressions().length == 0) { // implicit ctr call
        final CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
        if (candidates.length == 1 && !candidates[0].getElement().isPhysical()) {
          isDummy = true;// dummy constructor
        }
      }
    }


    HighlightInfo highlightInfo;
    if (isDummy) return null;


    if (element instanceof PsiMethod && resolveResult.isValidResult()) {
      PsiMethod resolvedMethod = (PsiMethod)element;
      if (resolvedMethod.isConstructor() && !isThisOrSuper) {
        highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, methodCall, "Direct constructor call is not allowed");
      }
      else {
        TextRange fixRange = getFixRange(methodCall);
        highlightInfo = HighlightUtil.checkUnhandledExceptions(methodCall, fixRange);
      }

      if (highlightInfo == null) {
        highlightInfo = GenericsHighlightUtil.checkUncheckedCall(resolveResult, methodCall);
      }
      if (highlightInfo == null) {
        highlightInfo = GenericsHighlightUtil.checkGenericCallWithRawArguments(resolveResult, methodCall);
      }
    }
    else {
      PsiMethod resolvedMethod = null;
      MethodCandidateInfo info = null;
      if (resolveResult instanceof MethodCandidateInfo) {
        info = (MethodCandidateInfo)resolveResult;
        resolvedMethod = info.getElement();
      }

      if (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect()) {
        // check for ambiguous method call
        String methodName = referenceToMethod.getReferenceName();
        methodName += HighlightUtil.buildArgTypesList(list);

        final ResolveResult[] resolveResults = referenceToMethod.multiResolve(true);
        MethodCandidateInfo methodCandidate1 = null;
        MethodCandidateInfo methodCandidate2 = null;
        List<MethodCandidateInfo> candidateList = new ArrayList<MethodCandidateInfo>();
        for (int i = 0; i < resolveResults.length; i++) {
          ResolveResult result = resolveResults[i];
          if (!(result instanceof MethodCandidateInfo)) continue;
          MethodCandidateInfo candidate = (MethodCandidateInfo)result;
          if (candidate.isApplicable()) {
            if (methodCandidate1 == null) {
              methodCandidate1 = candidate;
            }
            else {
              methodCandidate2 = candidate;
              break;
            }
          }
        }

        for (int i = 0; i < resolveResults.length; i++) {
          ResolveResult result = resolveResults[i];
          if (!(result instanceof MethodCandidateInfo)) continue;
          MethodCandidateInfo candidate = (MethodCandidateInfo)result;
          if (candidate.isAccessible()) candidateList.add(candidate);
        }

        String description;
        String toolTip;
        PsiElement elementToHighlight;
        HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
        if (methodCandidate2 != null) {
          final String m1 = PsiFormatUtil.formatMethod(methodCandidate1.getElement(),
                                                       methodCandidate1.getSubstitutor(),
                                                       PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME |
                                                       PsiFormatUtil.SHOW_PARAMETERS,
                                                       PsiFormatUtil.SHOW_TYPE);
          final String m2 = PsiFormatUtil.formatMethod(methodCandidate2.getElement(),
                                                       methodCandidate2.getSubstitutor(),
                                                       PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME |
                                                       PsiFormatUtil.SHOW_PARAMETERS,
                                                       PsiFormatUtil.SHOW_TYPE);
          description = MessageFormat.format("Ambiguous method call: both ''{0}'' and ''{1}'' match", new Object[]{m1, m2});
          toolTip = createAmbiguousMethodHtmlTooltip(new MethodCandidateInfo[]{methodCandidate1, methodCandidate2});
          elementToHighlight = list;
        }
        else {
          if (element != null && !resolveResult.isAccessible()) {
            description = HighlightUtil.buildProblemWithAccessDescription(element, referenceToMethod, resolveResult);
            elementToHighlight = referenceToMethod.getReferenceNameElement();
          }
          else if (element != null && !resolveResult.isStaticsScopeCorrect()) {
            description = HighlightUtil.buildProblemWithStaticDescription(element);
            elementToHighlight = referenceToMethod.getReferenceNameElement();
          }
          else {
            description = MessageFormat.format(CANNOT_RESOLVE_METHOD, new Object[]{methodName});
            if (candidateList.size() == 0) {
              elementToHighlight = referenceToMethod.getReferenceNameElement();
              highlightInfoType = HighlightInfoType.WRONG_REF;
            }
            else {
              elementToHighlight = list;
            }
          }
          toolTip = description;
        }
        highlightInfo = HighlightInfo.createHighlightInfo(highlightInfoType, elementToHighlight, description, toolTip);
        if (methodCandidate2 == null) {
          registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
        }
        if (!resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect()) {
          HighlightUtil.registerAccessQuickFixAction((PsiMember)element, referenceToMethod, highlightInfo);
        }
        if (!resolveResult.isStaticsScopeCorrect()) {
          HighlightUtil.registerStaticProblemQuickFixAction(element, highlightInfo, referenceToMethod);
        }

        MethodCandidateInfo[] candidates = candidateList.toArray(new MethodCandidateInfo[candidateList.size()]);
        CastMethodParametersFix.registerCastActions(candidates, list, methodCall.getMethodExpression(), highlightInfo);
        WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), highlightInfo);
        ChangeParameterClassFix.registerQuickFixActions(methodCall, list, highlightInfo);
        highlightInfo.navigationShift = +1;
      }
      else if (info != null && !info.isApplicable()) {
        if (info.isTypeArgumentsApplicable()) {
          String methodName = HighlightMessageUtil.getSymbolName(element, resolveResult.getSubstitutor());
          String containerName = HighlightMessageUtil.getSymbolName(element.getParent(), resolveResult.getSubstitutor());
          String argTypes = HighlightUtil.buildArgTypesList(list);
          String description = MessageFormat.format(WRONG_METHOD_ARGUMENTS, new Object[]{methodName, containerName, argTypes});
          String toolTip = element.getParent() instanceof PsiClass ?
                           createMismatchedArgumentsHtmlTooltip(info, list) : description;
          highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description, toolTip);
          registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
          highlightInfo.navigationShift = +1;
        }
        else {
          final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
          PsiReferenceParameterList typeArgumentList = methodCall.getTypeArgumentList();
          if ((typeArgumentList == null || typeArgumentList.getTypeArguments().length == 0) &&
              resolvedMethod.getTypeParameterList().getTypeParameters().length > 0) {
            highlightInfo = GenericsHighlightUtil.checkInferredTypeArguments(resolvedMethod, methodCall, resolveResult.getSubstitutor());
          }
          else {
            highlightInfo = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(element, methodExpression,
                                                                                           resolveResult.getSubstitutor());
          }
        }
      }
      else {
        highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, methodCall, "Method call expected");
        if (element instanceof PsiClass) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new InsertNewFix(methodCall, (PsiClass)element));
        }
        else {
          TextRange range = getFixRange(methodCall);
          QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreateMethodFromUsageAction(methodCall));
          QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreatePropertyFromUsageAction(methodCall));
        }
      }
    }
    if (highlightInfo == null) {
      highlightInfo =
      HighlightUtil.checkDeprecated(element, referenceToMethod.getReferenceNameElement(), DaemonCodeAnalyzerSettings.getInstance());
    }
    if (highlightInfo == null) {
      highlightInfo =
      GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(element, referenceToMethod, resolveResult.getSubstitutor());
    }
    return highlightInfo;
  }

  private static void registerMethodCallIntentions(HighlightInfo highlightInfo,
                                                   PsiMethodCallExpression methodCall,
                                                   PsiExpressionList list, PsiResolveHelper resolveHelper) {
    TextRange range = getFixRange(methodCall);
    QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreateMethodFromUsageAction(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreateConstructorFromSuperAction(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreateConstructorFromThisAction(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreatePropertyFromUsageAction(methodCall));
    CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates(methodCall, false);
    CastMethodParametersFix.registerCastActions(methodCandidates, list, methodCall.getMethodExpression(), highlightInfo);
    registerMethodAccessLevelIntentions(methodCandidates, methodCall, list, highlightInfo);
    ChangeMethodSignatureFromUsageFix.registerIntentions(methodCandidates, list, highlightInfo, range);
    WrapExpressionFix.registerWrapAction(methodCandidates, list.getExpressions(), highlightInfo);
    ChangeParameterClassFix.registerQuickFixActions(methodCall, list, highlightInfo);
  }

  private static void registerMethodAccessLevelIntentions(CandidateInfo[] methodCandidates,
                                                          PsiMethodCallExpression methodCall,
                                                          PsiExpressionList exprList,
                                                          HighlightInfo highlightInfo) {
    for (int i = 0; i < methodCandidates.length; i++) {
      ResolveResult methodCandidate = methodCandidates[i];
      PsiMethod method = ((PsiMethod)methodCandidate.getElement());
      if (!methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        HighlightUtil.registerAccessQuickFixAction(method, methodCall.getMethodExpression(), highlightInfo);
      }
    }
  }

  private static String createAmbiguousMethodHtmlTooltip(MethodCandidateInfo[] methodCandidates) {
    String s = "<html><body><table border=0>";
    s += "<tr><td colspan=" + (Math.max(1, methodCandidates[0].getElement().getParameterList().getParameters().length) + 2) +
         ">Ambiguous method call. Both</td></tr>";

    for (int i = 0; i < 2; i++) {
      final MethodCandidateInfo methodCandidate = methodCandidates[i];
      final PsiMethod method = methodCandidate.getElement();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
      s += "<tr><td><b>" + method.getName() + "</b></td>";

      for (int j = 0; j < parameters.length; j++) {
        PsiParameter parameter = parameters[j];
        final PsiType type = substitutor.substitute(parameter.getType());
        s += "<td><b>" + (j == 0 ? "(" : "") +
             XmlUtil.escapeString(type.getPresentableText())
             + (j == parameters.length - 1 ? ")" : ",") + "</b></td>";
      }
      if (parameters.length == 0) {
        s += "<td><b>()</b></td>";
      }
      PsiClass containingClass = method.getContainingClass();
      String name = containingClass == null ? method.getContainingFile().getName() : HighlightUtil.formatClass(containingClass, false);
      s += "<td>" + "in <b>" + name + "</b>";
      s += "&nbsp;" + (i == 0 ? "and" : "match.") + "</td>";
      s += "</tr>";
    }
    s += "</table></body></html>";
    return s;
  }

  static String createMismatchedArgumentsHtmlTooltip(MethodCandidateInfo info, PsiExpressionList list) {
    final PsiMethod method = info.getElement();
    final PsiClass aClass = method.getContainingClass();
    final PsiSubstitutor substitutor = info.getSubstitutor();
    String s = "<html><body><table border=0>";
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiExpression[] expressions = list.getExpressions();
    int cols = Math.max(parameters.length, expressions.length);
    s += "<tr>";
    s += "<td><b>" + method.getName() + (parameters.length == 0 ? "(&nbsp;)&nbsp;" : "") + "</b></td>";
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType type = substitutor.substitute(parameter.getType());
      s += "<td><b>" + (i == 0 ? "(" : "") +
           XmlUtil.escapeString(showShortType(i, parameters, expressions, substitutor)
                                ? type.getPresentableText()
                                : HighlightUtil.formatType(type))
           + (i == parameters.length - 1 ? ")" : ",") + "</b></td>";
    }
    s += "<td colspan=" + (cols - parameters.length + 1) + ">" + "in <b>" + HighlightUtil.formatClass(aClass, false) +
         "</b>" +
         "&nbsp;cannot be applied</td>";
    s += "</tr>";

    s += "<tr>";
    s += "<td>to</td>";
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      final PsiType type = expression.getType();

      String mismatchColor = showShortType(i, parameters, expressions, substitutor) ? null : "red";
      s += "<td> " + "<b>" + (i == 0 ? "(" : "")
           + "<font " + (mismatchColor == null ? "" : "color=" + mismatchColor) + ">"
           +
           XmlUtil.escapeString(showShortType(i, parameters, expressions, substitutor)
                                ? type.getPresentableText()
                                : HighlightUtil.formatType(type))
           + "</font>"
           + (i == expressions.length - 1 ? ")" : ",") + "</b></td>";
    }
    for (int i = expressions.length; i < cols + 1; i++) {
      s += "<td>" + (i == 0 ? "<b>()</b>" : "") +
           "&nbsp;</td>";
    }
    s += "</tr>";
    s += "</table></body></html>";
    return s;
  }

  private static boolean showShortType(int i,
                                       PsiParameter[] parameters,
                                       PsiExpression[] expressions,
                                       PsiSubstitutor substitutor) {
    PsiExpression expression = i < expressions.length ? expressions[i] : null;
    if (expression == null) return true;
    final PsiType paramType = i < parameters.length && parameters[i] != null
                              ? substitutor.substitute(parameters[i].getType())
                              : null;
    return paramType != null && TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression);
  }

  //@top
  static HighlightInfo checkMethodMustHaveBody(PsiMethod method, final PsiClass aClass) {
    HighlightInfo errorResult = null;
    if (method.getBody() == null
        && !method.hasModifierProperty(PsiModifier.ABSTRACT)
        && !method.hasModifierProperty(PsiModifier.NATIVE)
        && aClass != null
        && !aClass.isInterface()
        && !PsiUtil.hasErrorElementChild(method)) {
      int start = method.getModifierList().getTextRange().getStartOffset();
      int end = method.getTextRange().getEndOffset();

      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      start, end,
                                                      "Missing method body, or declare abstract");
      if (HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, method.getModifierList(),
                                                HighlightUtil.ourMethodIncompatibleModifiers) == null) {
        QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(method, PsiModifier.ABSTRACT, true));
      }
      QuickFixAction.registerQuickFixAction(errorResult, new AddMethodBodyFix(method));
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkAbstractMethodInConcreteClass(PsiMethod method, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    PsiClass aClass = method.getContainingClass();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)
        && aClass != null
        && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)
        && !aClass.isEnum()
        && !PsiUtil.hasErrorElementChild(method)) {
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      elementToHighlight,
                                                      "Abstract method in non-abstract class");
      if (method.getBody() != null) {
        QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(method, PsiModifier.ABSTRACT, false));
      }
      QuickFixAction.registerQuickFixAction(errorResult, new AddMethodBodyFix(method));
      QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(aClass, PsiModifier.ABSTRACT, true));
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkConstructorName(PsiMethod method) {
    String methodName = method.getName();
    PsiClass aClass = method.getContainingClass();
    HighlightInfo errorResult = null;

    if (aClass != null) {
      String className = aClass instanceof PsiAnonymousClass ? null : aClass.getName();
      if (!methodName.equals(className)) {
        errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, method.getNameIdentifier(),
                                                        "Invalid method declaration; return type required");
      }
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkDuplicateMethod(final PsiClass aClass, PsiMethod method) {
    if (aClass == null) return null;

    final MethodSignatureUtil.MethodSignatureToMethods allMethods = MethodSignatureUtil.getSameSignatureMethods(aClass);
    final MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    List<MethodSignatureBackedByPsiMethod> sameSignatureMethods = allMethods.get(methodSignature);
    int methodCount = 0;
    if (sameSignatureMethods != null) {
      for (int i = 0; i < sameSignatureMethods.size(); i++) {
        final MethodSignatureBackedByPsiMethod methodBackedMethodSignature = sameSignatureMethods.get(i);
        PsiMethod psiMethod = methodBackedMethodSignature.getMethod();
        if (aClass.getManager().areElementsEquivalent(aClass, psiMethod.getContainingClass())) {
          if (psiMethod.isConstructor() == method.isConstructor()) {
            methodCount++;
            if (methodCount > 1) break;
          }
        }
      }
    }
    if (methodCount == 1 && aClass.isEnum() &&
        GenericsHighlightUtil.isEnumSyntheticMethod(methodSignature, aClass.getProject())) {
      methodCount = 2;
    }
    if (methodCount > 1) {
      String message = MessageFormat.format("''{0}'' is already defined in ''{1}''",
                                            new Object[]{HighlightUtil.formatMethod(method),
                                                         HighlightUtil.formatClass(aClass)});
      TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    }
    return null;
  }

  //@top
  public static HighlightInfo checkMethodCanHaveBody(PsiMethod method) {
    if (method.getBody() == null) return null;
    PsiClass aClass = method.getContainingClass();

    String message = null;
    if (aClass != null && aClass.isInterface()) {
      message = "Interface methods cannot have body";
    }
    else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      message = "Abstract methods cannot have a body";
    }
    else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      message = "Native methods cannot have a body";
    }

    if (message != null) {
      TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
      QuickFixAction.registerQuickFixAction(info, new DeleteMethodBodyFix(method));
      return info;
    }
    return null;
  }

  //@top
  static HighlightInfo checkConstructorCallMustBeFirstStatement(PsiReferenceExpression expression) {
    final String text = expression.getText();
    if (!PsiKeyword.THIS.equals(text) && !PsiKeyword.SUPER.equals(text)) return null;
    if (!(expression.getParent() instanceof PsiMethodCallExpression)) return null;
    final PsiElement codeBlock = PsiUtil.getTopLevelEnclosingCodeBlock(expression, null);
    if (new PsiMatcherImpl(expression)
      .parent(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
      .parent(PsiMatcherImpl.hasClass(PsiStatement.class))
      .dot(PsiMatcherImpl.isFirstStatement(true))
      .parent(PsiMatcherImpl.hasClass(PsiCodeBlock.class))
      .parent(PsiMatcherImpl.hasClass(PsiMethod.class))
      .getElement() == null
        && codeBlock != null
        && codeBlock.getParent() instanceof PsiMethod
        && ((PsiMethod)codeBlock.getParent()).isConstructor()
    ) {
      String message = MessageFormat.format("Call to ''{0}'' must be first statement in constructor", new Object[]{text + "()"});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression.getParent(), message);
    }
    return null;
  }

  //@top
  public static HighlightInfo checkAbstractMethodDirectCall(PsiSuperExpression expr) {
    if (expr.getParent() instanceof PsiReferenceExpression
        && expr.getParent().getParent() instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expr.getParent().getParent();
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method != null && method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        String message = MessageFormat.format("Abstract method ''{0}'' cannot be accessed directly",
                                              new Object[]{HighlightUtil.formatMethod(method)});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, methodCallExpression, message);
      }
    }
    return null;
  }

  //@top
  public static HighlightInfo checkConstructorCallBaseclassConstructor(PsiMethod constructor, RefCountHolder refCountHolder) {
    if (!constructor.isConstructor()) return null;
    final PsiClass aClass = constructor.getContainingClass();
    if (aClass == null) return null;
    if (aClass.isEnum()) return null;
    final PsiCodeBlock body = constructor.getBody();
    if (body == null) return null;

    // check whether constructor call super(...) or this(...)
    final PsiElement element = new PsiMatcherImpl(body)
      .firstChild(PsiMatcherImpl.hasClass(PsiExpressionStatement.class))
      .firstChild(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
      .firstChild(PsiMatcherImpl.hasClass(PsiReferenceExpression.class))
      .firstChild(PsiMatcherImpl.hasClass(PsiKeyword.class))
      .getElement();
    if (element != null) return null;
    TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(constructor);
    PsiClassType[] handledExceptions = constructor.getThrowsList().getReferencedTypes();
    HighlightInfo info = HighlightClassUtil.checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, textRange, handledExceptions);
    if (info != null) {
      QuickFixAction.registerQuickFixAction(info, new InsertSuperFix(constructor));
      QuickFixAction.registerQuickFixAction(info, new AddDefaultConstructorFix(aClass.getSuperClass()));
    }
    return info;
  }

  //@top
  /**
   * @return error if static method overrides instance method or
   *         instance method overrides static. see JLS 8.4.6.1, 8.4.6.2
   */
  public static HighlightInfo checkStaticMethodOverride(PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiClass superClass = aClass.getSuperClass();
    PsiMethod superMethod = superClass == null
                            ? null
                            : MethodSignatureUtil.findMethodBySignature(superClass, method, true);

    HighlightInfo highlightInfo = checkStaticMethodOverride(aClass, method, superClass, superMethod, true);
    if (highlightInfo != null) return highlightInfo;
    PsiClass[] interfaces = aClass.getInterfaces();
    for (int i = 0; i < interfaces.length; i++) {
      superClass = interfaces[i];
      superMethod = MethodSignatureUtil.findMethodBySignature(superClass, method, true);
      highlightInfo = checkStaticMethodOverride(aClass, method, superClass, superMethod, true);
      if (highlightInfo != null) return highlightInfo;
    }
    return highlightInfo;
  }

  //@top
  public static HighlightInfo checkStaticMethodOverride(PsiClass aClass,
                                                        PsiMethod method,
                                                        PsiClass superClass,
                                                        PsiMethod superMethod,
                                                        boolean includeRealPositionInfo) {
    if (superMethod == null) return null;
    final PsiManager manager = superMethod.getManager();
    final PsiModifierList superModifierList = superMethod.getModifierList();
    final PsiModifierList modifierList = method.getModifierList();
    if (superModifierList.hasModifierProperty(PsiModifier.PRIVATE)) return null;
    if (superModifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
        && !manager.arePackagesTheSame(aClass, superClass)) {
      return null;
    }
    final boolean isMethodStatic = modifierList.hasModifierProperty(PsiModifier.STATIC);
    final boolean isSuperMethodStatic = superModifierList.hasModifierProperty(PsiModifier.STATIC);
    if (isMethodStatic != isSuperMethodStatic) {
      TextRange textRange = includeRealPositionInfo ? HighlightUtil.getMethodDeclarationTextRange(method) : new TextRange(0, 0);
      String message = MessageFormat.format("{0} method ''{1}'' in ''{2}'' cannot override {3} method ''{4}'' in ''{5}''",
                                            new Object[]{
                                              isMethodStatic ? "static" : "instance",
                                              HighlightUtil.formatMethod(method),
                                              HighlightUtil.formatClass(aClass),
                                              isSuperMethodStatic ? "static" : "instance",
                                              HighlightUtil.formatMethod(superMethod),
                                              HighlightUtil.formatClass(superClass),
                                            });
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            textRange,
                                                                            message);
      if (!isSuperMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, modifierList) == null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(method, PsiModifier.STATIC, isSuperMethodStatic));
      }
      if (manager.isInProject(superMethod) &&
          (!isMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, superModifierList) == null)) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(superMethod, PsiModifier.STATIC, isMethodStatic, true));
      }
      return highlightInfo;
    }

    return null;
  }

  private static HighlightInfo checkInterfaceInheritedMethodsReturnTypes(
    final List<MethodSignatureBackedByPsiMethod> superMethodSignatures) {
    if (superMethodSignatures.size() < 2) return null;
    MethodSignatureBackedByPsiMethod returnTypeSubstitutable = superMethodSignatures.get(0);
    for (int i = 1; i < superMethodSignatures.size(); i++) {
      final PsiMethod currentMethod = returnTypeSubstitutable.getMethod();
      PsiType currentType = returnTypeSubstitutable.getSubstitutor().substitute(currentMethod.getReturnType());

      final MethodSignatureBackedByPsiMethod otherSuperSignature = superMethodSignatures.get(i);
      PsiMethod otherSuperMethod = otherSuperSignature.getMethod();
      PsiType otherSuperReturnType = otherSuperSignature.getSubstitutor().substitute(otherSuperMethod.getReturnType());

      final PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(returnTypeSubstitutable,
                                                                                                        otherSuperSignature);
      if (unifyingSubstitutor != null) {
        otherSuperReturnType = unifyingSubstitutor.substitute(otherSuperReturnType);
        currentType = unifyingSubstitutor.substitute(currentType);
      }

      if (otherSuperReturnType == null || currentType == null) continue;
      if (otherSuperReturnType.equals(currentType)) continue;

      if (LanguageLevel.JDK_1_5.compareTo(currentMethod.getManager().getEffectiveLanguageLevel()) <= 0) {
        if (otherSuperReturnType.isAssignableFrom(currentType)) continue;
        if (currentType.isAssignableFrom(otherSuperReturnType)) {
          returnTypeSubstitutable = otherSuperSignature;
          continue;
        }
      }
      return createIncompatibleReturnTypeMessage(currentMethod, currentMethod, otherSuperMethod, false, otherSuperReturnType,
                                                 currentType, "methods have unrelated return types");
    }
    return null;
  }

  //@@top
  // JLS 9.4.1, 8.4.6.4: compile-time error occurs if, for any two inherited methods with the same signature ,
  //  either they have different return types or one has a return type and the other is void, with
  // corrections about covariant return types in JLS3
  public static HighlightInfo checkInheritedMethodsWithSameSignature(PsiClass aClass) {
    PsiClass[] superClasses = aClass.getSupers();
    // conflicts possible only if there are at least two super classes
    if (superClasses.length <= 1) return null;

    final PsiManager manager = aClass.getManager();
    PsiClass objectClass = manager.findClass("java.lang.Object", aClass.getResolveScope());
    final MethodSignatureUtil.MethodSignatureToMethods allMethods = MethodSignatureUtil.getSameSignatureMethods(aClass);

    for (Iterator<List<MethodSignatureBackedByPsiMethod>> iterator = allMethods.values().iterator(); iterator.hasNext();) {
      ProgressManager.getInstance().checkCanceled();

      List<MethodSignatureBackedByPsiMethod> superMethodSignatures = new ArrayList<MethodSignatureBackedByPsiMethod>(iterator.next());
      final MethodSignatureBackedByPsiMethod firstMethodSignature = superMethodSignatures.get(0);
      if (firstMethodSignature.getMethod().isConstructor()) continue;

      removeNotInheritedMethods(superMethodSignatures, aClass);
      if (superMethodSignatures.size() < 2) continue;

      // find overriding method (first non-abstract method, Method from non-Object class takes precedence)
      MethodSignatureBackedByPsiMethod overridingMethodSignature = null;
      int overridingSignatureIndex = -1;
      for (int i = 0; i < superMethodSignatures.size(); i++) {
        final MethodSignatureBackedByPsiMethod superMethodSignature = superMethodSignatures.get(i);
        PsiMethod superMethod = superMethodSignature.getMethod();
        if (!manager.getResolveHelper().isAccessible(superMethod, aClass, null)) {
          superMethodSignatures.remove(i);
          continue;
        }

        if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;

        if (overridingSignatureIndex < 0 || !manager.areElementsEquivalent(superMethod.getContainingClass(), objectClass)) {
          overridingMethodSignature = superMethodSignature;
          overridingSignatureIndex = i;
        }
      }

      HighlightInfo highlightInfo = null;
      if (overridingMethodSignature != null) {
        PsiMethod overridingMethod = overridingMethodSignature.getMethod();
        PsiClass overridingClass = overridingMethod.getContainingClass();

        if (!manager.areElementsEquivalent(overridingClass, objectClass) ||
            !aClass.isInterface()) {
//Put overridingMethodSignature first
          superMethodSignatures.set(0, overridingMethodSignature);
          superMethodSignatures.set(overridingSignatureIndex, firstMethodSignature);

          highlightInfo = checkMethodIncompatibleReturnType(overridingMethodSignature, superMethodSignatures, false);


          if (highlightInfo == null) {
            for (int i = 1; i < superMethodSignatures.size(); i++) {
              final MethodSignatureBackedByPsiMethod superMethodSignature = superMethodSignatures.get(i);
              PsiMethod superMethod = superMethodSignature.getMethod();
              // interface can "override" methods from Object
              if (aClass.isInterface() && manager.areElementsEquivalent(overridingClass, objectClass)) continue;
              // nonInterfaceMethod is considered to implement superMethod, check for exception/access conflicts
              final List<MethodSignatureBackedByPsiMethod> list = new ArrayList<MethodSignatureBackedByPsiMethod>();
              list.add(superMethodSignature);
              highlightInfo = checkMethodIncompatibleThrows(overridingMethodSignature, list, false);
              if (highlightInfo == null) {
                // do not fetch real positions - optimization to not load tree
                highlightInfo = checkMethodWeakerPrivileges(overridingMethodSignature, list, false);
              }
              if (highlightInfo == null) {
                highlightInfo = checkStaticMethodOverride(overridingClass,
                                                          overridingMethod,
                                                          superMethod.getContainingClass(),
                                                          superMethod, false);
              }
              if (highlightInfo != null) break;
            }
          }
        }
      }
      else {
        if (aClass.isInterface()) {
          //Check there is one return type substitutable method
          highlightInfo = checkInterfaceInheritedMethodsReturnTypes(superMethodSignatures);
        }
        else {
          //Check all inherited methods are pairwise return type substitutable
          highlightInfo = checkAllAbstractInheritedMethodsReturnTypes(superMethodSignatures);
        }
      }
      if (highlightInfo != null) {
        // show error info at the class level
        final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
        String message = highlightInfo.description;
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 textRange,
                                                 message);
      }
    }
    return null;
  }

  public static void removeNotInheritedMethods(List<MethodSignatureBackedByPsiMethod> sameSignatureMethods,
                                               PsiClass contextClass) {
    final PsiManager manager = contextClass.getManager();
    PsiResolveHelper helper = manager.getResolveHelper();
    NextMethod:
    for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
      final MethodSignatureBackedByPsiMethod signature1 = sameSignatureMethods.get(i);
      PsiMethod method1 = signature1.getMethod();
      final PsiClass class1 = method1.getContainingClass();
      if (manager.areElementsEquivalent(class1, contextClass) ||
          !helper.isAccessible(method1, contextClass, null)) {
        sameSignatureMethods.remove(i);
        continue;
      }
      // check if method1 is overridden
      for (int j = 0; j < sameSignatureMethods.size(); j++) {
        if (i != j) {
          final MethodSignatureBackedByPsiMethod signature2 = sameSignatureMethods.get(j);
          final PsiClass class2 = signature2.getMethod().getContainingClass();
          if (InheritanceUtil.isInheritorOrSelf(class2, class1, true)
              //if the class is interface then the method is inherited all the same
              && (!manager.areElementsEquivalent(class2, contextClass) || !contextClass.isInterface())
              // method from interface cannot override method from Object
              && (!"java.lang.Object".equals(class1.getQualifiedName()) || !class2.isInterface())) {
            sameSignatureMethods.remove(i);
            continue NextMethod;
          }
        }
      }
    }
  }

  private static HighlightInfo checkAllAbstractInheritedMethodsReturnTypes(
    final List<MethodSignatureBackedByPsiMethod> superMethodSignatures) {
    if (superMethodSignatures.size() < 2) return null;
    for (int i = 0; i < superMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod superSignature = superMethodSignatures.get(i);
      final PsiMethod currentMethod = superSignature.getMethod();
      PsiType currentType = superSignature.getSubstitutor().substitute(currentMethod.getReturnType());

      for (int j = i; j < superMethodSignatures.size(); j++) {
        final MethodSignatureBackedByPsiMethod otherSuperSignature = superMethodSignatures.get(j);
        PsiMethod otherSuperMethod = otherSuperSignature.getMethod();
        PsiType otherSuperReturnType = otherSuperSignature.getSubstitutor().substitute(otherSuperMethod.getReturnType());
        final PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(superSignature,
                                                                                                          otherSuperSignature);
        if (unifyingSubstitutor != null) {
          otherSuperReturnType = unifyingSubstitutor.substitute(otherSuperReturnType);
          currentType = unifyingSubstitutor.substitute(currentType);
        }

        if (otherSuperReturnType == null || currentType == null) continue;
        if (otherSuperReturnType.equals(currentType)) continue;

        if (LanguageLevel.JDK_1_5.compareTo(currentMethod.getManager().getEffectiveLanguageLevel()) <= 0) {
          if (otherSuperReturnType.isAssignableFrom(currentType) ||
              currentType.isAssignableFrom(otherSuperReturnType)) {
            continue;
          }
        }
        return createIncompatibleReturnTypeMessage(currentMethod, currentMethod, otherSuperMethod, false, otherSuperReturnType,
                                                   currentType, "methods have unrelated return types");
      }
    }
    return null;
  }

  //@top
  static HighlightInfo checkMethodOverridesDeprecated(MethodSignatureBackedByPsiMethod methodSignature,
                                                      List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                      DaemonCodeAnalyzerSettings settings) {
    if (!settings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.DEPRECATED_SYMBOL)) return null;
    final PsiMethod method = methodSignature.getMethod();
    PsiElement methodName = method.getNameIdentifier();
    for (int i = 0; i < superMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod superMethodSignature = superMethodSignatures.get(i);
      PsiMethod superMethod = superMethodSignature.getMethod();
      final PsiClass aClass = superMethod.getContainingClass();
      if (aClass == null) continue;
      // do not show deprecated warning for class implementing deprecated methods
      if (!aClass.isDeprecated() && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (superMethod.isDeprecated()) {
        String description = MessageFormat.format("Overrides deprecated method in ''{0}''", new Object[]{
          HighlightMessageUtil.getSymbolName(aClass, PsiSubstitutor.EMPTY)});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.DEPRECATED, methodName, description);
      }
    }
    return null;
  }

  //@top
  public static HighlightInfo checkConstructorHandleSuperClassExceptions(PsiMethod method) {
    if (!method.isConstructor()) {
      return null;
    }
    final PsiCodeBlock body = method.getBody();
    final PsiStatement[] statements = body == null ? null : body.getStatements();
    if (statements == null) return null;

    // if we have unhandled exception inside method body, we could not have been called here,
    // so the only problem it can catch here is with super ctr only
    final PsiClassType[] unhandled = ExceptionUtil.collectUnhandledExceptions(method, method.getContainingClass());
    if (unhandled == null || unhandled.length == 0) return null;
    final String description = HighlightUtil.getUnhandledExceptionsDescriptor(unhandled);
    TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
    final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                          textRange,
                                                                          description);
    for (int i = 0; i < unhandled.length; i++) {
      PsiClassType exception = unhandled[i];
      QuickFixAction.registerQuickFixAction(highlightInfo, new MethodThrowsFix(method, exception, true));
    }
    return highlightInfo;
  }

  //@top
  public static HighlightInfo checkMethodSameNameAsConstructor(final PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    if (Comparing.equal(aClass.getName(), method.getName(), true)
        && method.getReturnTypeElement() != null
        && method.getReturnType() != null
        && !method.hasModifierProperty(PsiModifier.STATIC)
        && !TypeConversionUtil.isNullType(method.getReturnType())) {
      TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING,
                                                                            textRange,
                                                                            "Method should be constructor");
      QuickFixAction.registerQuickFixAction(highlightInfo, new MakeMethodConstructorFix(method));
      return highlightInfo;
    }
    return null;
  }

  //@top
  public static HighlightInfo checkRecursiveConstructorInvocation(PsiMethod method) {
    if (HighlightControlFlowUtil.isRecursivelyCalledConstructor(method)) {
      TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            textRange,
                                                                            "Recursive constructor invocation");
      return highlightInfo;
    }
    return null;
  }

  public static TextRange getFixRange(PsiElement element) {
    TextRange range = element.getTextRange();
    int start = range.getStartOffset(), end = range.getEndOffset();

    PsiElement nextSibling = element.getNextSibling();
    if (nextSibling instanceof PsiJavaToken && ((PsiJavaToken)nextSibling).getTokenType() == JavaTokenType.SEMICOLON) {
      return new TextRange(start, end + 1);
    }
    return range;
  }

  //@top
  static HighlightInfo checkNewExpression(PsiNewExpression expression, DaemonCodeAnalyzerSettings settings) {
    PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) return null;
    PsiClassType.ClassResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
    PsiClass aClass = typeResult.getElement();
    if (aClass == null) return null;
    if (aClass instanceof PsiAnonymousClass) {
      type = ((PsiAnonymousClass)aClass).getBaseClassType();
      typeResult = ((PsiClassType)type).resolveGenerics();
      aClass = typeResult.getElement();
      if (aClass == null) return null;
    }

    return checkConstructorCall(aClass, expression, type, settings, expression.getClassReference());
  }

  //@top
  public static HighlightInfo checkConstructorCall(PsiClass aClass,
                                                   PsiConstructorCall constructorCall,
                                                   PsiType type,
                                                   DaemonCodeAnalyzerSettings settings,
                                                   final PsiJavaCodeReferenceElement classReference) {
    final PsiExpressionList list = constructorCall.getArgumentList();
    if (list == null) return null;
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      if (list.getExpressions().length != 0) {
        String constructorName = aClass.getName() + "()";
        String containerName = HighlightMessageUtil.getSymbolName(aClass, PsiSubstitutor.EMPTY);
        String argTypes = HighlightUtil.buildArgTypesList(list);
        String description = MessageFormat.format(WRONG_CONSTRUCTOR_ARGUMENTS, new Object[]{constructorName, containerName, argTypes});
        final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description);
        QuickFixAction.registerQuickFixAction(info, constructorCall.getTextRange(), new CreateConstructorFromCallAction(constructorCall));
        info.navigationShift = +1;
        return info;
      }
    }
    else {
      ResolveResult[] results = constructorCall.getManager().getResolveHelper().multiResolveConstructor((PsiClassType)type, list, list);
      MethodCandidateInfo result = null;
      if (results.length == 1) result = (MethodCandidateInfo)results[0];

      final PsiMethod constructor = result == null ? null : (PsiMethod)result.getElement();
      if (constructor == null) {
        String name = aClass.getName();
        name += HighlightUtil.buildArgTypesList(list);
        String description = MessageFormat.format(CANNOT_RESOLVE_CONSTRUCTOR, new Object[]{name});
        final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description);
        QuickFixAction.registerQuickFixAction(info, constructorCall.getTextRange(), new CreateConstructorFromCallAction(constructorCall));
        if (classReference != null) {
          CastConstructorParametersFix.registerCastActions(classReference, list, info);
        }
        WrapExpressionFix.registerWrapAction(results, list.getExpressions(), info);
        info.navigationShift = +1;
        return info;
      }
      else {
        if (!result.isAccessible() || callingProtectedConstructorFromDerivedClass(constructor, constructorCall)) {
          String description = HighlightUtil.buildProblemWithAccessDescription(constructor, classReference, result);
          final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description);
          info.navigationShift = +1;
          if (classReference != null && result.isStaticsScopeCorrect()) {
            HighlightUtil.registerAccessQuickFixAction(constructor, classReference, info);
          }
          return info;
        }
        else if (!result.isApplicable()) {
          String constructorName = HighlightMessageUtil.getSymbolName(constructor, result.getSubstitutor());
          String containerName = HighlightMessageUtil.getSymbolName(constructor.getParent(), result.getSubstitutor());
          String argTypes = HighlightUtil.buildArgTypesList(list);
          String description = MessageFormat.format(WRONG_METHOD_ARGUMENTS, new Object[]{constructorName, containerName, argTypes});
          String toolTip = createMismatchedArgumentsHtmlTooltip(result, list);
          PsiElement infoElement = list.getTextLength() > 0 ? (PsiElement)list : constructorCall;
          final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, infoElement, description, toolTip);
          QuickFixAction.registerQuickFixAction(info, constructorCall.getTextRange(), new CreateConstructorFromCallAction(constructorCall));
          if (classReference != null) {
            CastConstructorParametersFix.registerCastActions(classReference, list, info);
            ChangeMethodSignatureFromUsageFix.registerIntentions(results, list, info, null);
          }
          info.navigationShift = +1;
          return info;
        }
        else {
          HighlightInfo highlightInfo = GenericsHighlightUtil.checkUncheckedCall(result, constructorCall);
          if (highlightInfo != null) return highlightInfo;
          if (constructorCall instanceof PsiNewExpression) {
            highlightInfo = GenericsHighlightUtil.checkGenericCallWithRawArguments(result, (PsiCallExpression)constructorCall);
          }
          if (highlightInfo != null) return highlightInfo;

          if (classReference != null) {
            return HighlightUtil.checkDeprecated(constructor, classReference, settings);
          }
        }
      }
    }
    return null;
  }

  private static boolean callingProtectedConstructorFromDerivedClass(PsiMethod constructor, PsiConstructorCall place) {
    if (!constructor.hasModifierProperty(PsiModifier.PROTECTED)) return false;
    PsiClass constructorClass = constructor.getContainingClass();
    if (constructorClass == null) return false;
    // indirect instantiation via anonymous class is ok
    if (place instanceof PsiNewExpression && ((PsiNewExpression)place).getAnonymousClass() != null) return false;
    PsiElement curElement = place;
    while (true) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(curElement, PsiClass.class);
      if (aClass == null) return false;
      curElement = aClass;
      if (aClass.isInheritor(constructorClass, true) && !aClass.getManager().arePackagesTheSame(aClass, constructorClass)) {
        return true;
      }
    }
  }
}
