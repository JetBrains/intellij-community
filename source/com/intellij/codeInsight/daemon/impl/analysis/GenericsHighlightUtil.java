package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.HashMap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */

public abstract class GenericsHighlightUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil");

  public static HighlightInfo checkInferredTypeArguments(PsiMethod genericMethod,
                                                         PsiMethodCallExpression call,
                                                         PsiSubstitutor substitutor) {
    PsiTypeParameter[] typeParameters = genericMethod.getTypeParameterList().getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted == null) return null;
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      for (int j = 0; j < extendsTypes.length; j++) {
        PsiType extendsType = substitutor.substitute(extendsTypes[j]);
        if (!TypeConversionUtil.isAssignable(extendsType, substituted)) {
          PsiClass boundClass = extendsType instanceof PsiClassType ? ((PsiClassType)extendsType).resolve() : null;
          String description = MessageFormat.format(
            "Inferred type ''{3}'' for type parameter ''{0}'' is not within its bound; should {1} ''{2}''",
            new Object[]{
              HighlightUtil.formatClass(typeParameter),
              (boundClass == null || typeParameter.isInterface() == boundClass.isInterface()
               ? "extend"
               : "implement"),
              HighlightUtil.formatType(extendsType),
              HighlightUtil.formatType(substituted)
            });
          final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                                call,
                                                                                description);
          return highlightInfo;
        }
      }
    }

    return null;
  }

  public static HighlightInfo checkParameterizedReferenceTypeArguments(PsiElement resolved,
                                                                       final PsiJavaCodeReferenceElement referenceElement,
                                                                       final PsiSubstitutor substitutor) {
    if (!(resolved instanceof PsiTypeParameterListOwner)) return null;
    final PsiTypeParameterListOwner typeParameterListOwner = (PsiTypeParameterListOwner)resolved;
    return checkReferenceTypeParametersList(typeParameterListOwner, referenceElement, substitutor, true);
  }

  public static HighlightInfo checkReferenceTypeParametersList(final PsiTypeParameterListOwner typeParameterListOwner,
                                                               final PsiJavaCodeReferenceElement referenceElement,
                                                               final PsiSubstitutor substitutor, boolean registerIntentions) {
    final PsiTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
    final int targetParametersNum = typeParameterList == null ? 0 : typeParameterList.getTypeParameters().length;
    final PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
    final int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeParameterElements().length;

    if (targetParametersNum != refParametersNum && refParametersNum != 0) {
      final String message = targetParametersNum == 0 ?
                             "{0} ''{1}'' does not have type parameters" :
                             "Wrong number of type arguments: {2}; required: {3}";
      String description = MessageFormat.format(message,
                                                new Object[]{
                                                  typeParameterListOwnerCategoryDescription(typeParameterListOwner),
                                                  typeParameterListOwnerDescription(typeParameterListOwner),
                                                  new Integer(refParametersNum),
                                                  new Integer(targetParametersNum),
                                                });
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            referenceParameterList,
                                                                            description);
      if (registerIntentions) {
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiTypeElement) {
          PsiElement variable = parent.getParent();
          if (variable instanceof PsiVariable) {
            VariableParameterizedTypeFix.registerIntentions(highlightInfo, (PsiVariable) variable, referenceElement);
          }
        }
      }
      return highlightInfo;
    }

    // bounds check
    if (targetParametersNum > 0 && refParametersNum != 0) {
      final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
      final PsiTypeElement[] referenceElements = referenceParameterList.getTypeParameterElements();
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter classParameter = typeParameters[i];
        final PsiTypeElement typeElement = referenceElements[i];
        final PsiType type = typeElement.getType();
        if (!(type instanceof PsiClassType)) continue;
        final PsiClass referenceClass = ((PsiClassType)type).resolve();
        final PsiClassType[] bounds = classParameter.getSuperTypes();
        for (int j = 0; j < bounds.length; j++) {
          PsiType bound = substitutor.substitute(bounds[j]);
          if (!bound.equalsToText("java.lang.Object") && !TypeConversionUtil.isAssignable(bound, type)) {
            PsiClass boundClass = bound instanceof PsiClassType ? ((PsiClassType)bound).resolve() : null;
            String description = MessageFormat.format("Type parameter ''{0}'' is not within its bound; should {1} ''{2}''",
                                                      new Object[]{
                                                        HighlightUtil.formatClass(referenceClass),
                                                        (boundClass == null || referenceClass.isInterface() == boundClass.isInterface()
                                                         ? "extend"
                                                         : "implement"),
                                                        HighlightUtil.formatType(bound),
                                                      });
            final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                                  typeElement,
                                                                                  description);
            if (bound instanceof PsiClassType) {
              QuickFixAction.registerQuickFixAction(highlightInfo, new ExtendsListFix(referenceClass, (PsiClassType)bound, true));
            }
            return highlightInfo;
          }
        }
      }
    }

    return null;
  }

  private static String typeParameterListOwnerDescription(final PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass) {
      return HighlightUtil.formatClass((PsiClass)typeParameterListOwner);
    }
    else if (typeParameterListOwner instanceof PsiMethod) {
      return HighlightUtil.formatMethod((PsiMethod)typeParameterListOwner);
    }
    else {
      LOG.error("Unknown " + typeParameterListOwner);
      return "?";
    }
  }

  private static String typeParameterListOwnerCategoryDescription(final PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass) {
      return "Type";
    }
    else if (typeParameterListOwner instanceof PsiMethod) {
      return "Method";
    }
    else {
      LOG.error("Unknown " + typeParameterListOwner);
      return "?";
    }
  }

  public static HighlightInfo checkTypeParameterExtendsList(PsiReferenceList referenceList, ResolveResult resolveResult, PsiElement context) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    final PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
    HighlightInfo errorResult = null;
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (!extendFrom.isInterface() && referenceElements.length != 0 && context != referenceElements[0]) {
      final String description = HighlightClassUtil.INTERFACE_EXPECTED;
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, context, description);
      PsiClassType type = aClass.getManager().getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, new MoveBoundClassToFrontFix(aClass, type));
    }
    return errorResult;
  }

  public static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass) {
    if (aClass instanceof PsiTypeParameter) return null;
    final PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return null;
    Map<PsiClass, PsiSubstitutor> inheritedClasses = new HashMap<PsiClass, PsiSubstitutor>();
    final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
    return checkInterfaceMultipleInheritance(aClass,
                                             PsiSubstitutor.EMPTY, inheritedClasses,
                                             textRange);
  }

  private static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass,
                                                                 PsiSubstitutor parentSubstitutor,
                                                                 Map<PsiClass, PsiSubstitutor> inheritedClasses,
                                                                 TextRange textRange) {
    final PsiClassType[] types = aClass.getSuperTypes();
    for (int i = 0; i < types.length; i++) {
      PsiClassType superType = types[i];
      final PsiClassType.ClassResolveResult result = superType.resolveGenerics();
      final PsiClass superClass = result.getElement();
      if (superClass == null) continue;
      PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
      superTypeSubstitutor = MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, parentSubstitutor);

      final PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
      if (inheritedSubstitutor != null) {
        final PsiTypeParameter[] typeParameters = superClass.getTypeParameterList().getTypeParameters();
        for (int j = 0; j < typeParameters.length; j++) {
          PsiTypeParameter typeParameter = typeParameters[j];
          PsiType type1 = inheritedSubstitutor.substitute(typeParameter);
          PsiType type2 = superTypeSubstitutor.substitute(typeParameter);

          if (!Comparing.equal(type1, type2)) {
            String description = MessageFormat.format("''{0}'' cannot be inherited with different type arguments: ''{1}'' and ''{2}''",
                                                      new Object[]{HighlightUtil.formatClass(superClass), HighlightUtil.formatType(type1),
                                                                   HighlightUtil.formatType(type2)});
            HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            textRange,
                                                                            description);
            return highlightInfo;
          }
        }
      }
      inheritedClasses.put(superClass, superTypeSubstitutor);
      final HighlightInfo highlightInfo = checkInterfaceMultipleInheritance(superClass, superTypeSubstitutor, inheritedClasses,
                                                                            textRange);
      if (highlightInfo != null) return highlightInfo;
    }
    return null;
  }

  public static HighlightInfo checkSameErasureMethods(PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    final List<MethodSignatureBackedByPsiMethod> sameNameMethodSignatures = MethodSignatureUtil.findMethodSignaturesByName(aClass,
                                                                                                                           method.getName(),
                                                                                                                           false);
    for (int i = 0; i < sameNameMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod methodSignature = sameNameMethodSignatures.get(i);
      PsiMethod otherMethod = methodSignature.getMethod();
      if (otherMethod == method || method.isConstructor() != otherMethod.isConstructor()) continue;
      if (MethodSignatureUtil.areParametersErasureEqual(otherMethod, method)) {
        String description = MessageFormat.format("{0}; both methods have same erasure.",
                                     new Object[]{HighlightMethodUtil.createClashMethodMessage(method,otherMethod, false)});
        TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
      }
    }
    return null;
  }

  public static HighlightInfo checkSameErasureSuperMethods(PsiMethod method) {
    if (method.isConstructor()) return null;
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    /**
     * It is a compile time error if a type declaration T has a member method m1 and there exists a method
     * m2 declared in T or a supertype of T such that all of the following conditions hold:
     * - m1 and m2 have the same name.
     * - m2 is accessible from T.
     * - m1 and m2 have different signatures.
     * - m1 or some method m1 overrides (directly or indirectly) has the same erasure as m2 or some
     *    method m2 overrides (directly or indirectly).
     */
    final List<MethodSignatureBackedByPsiMethod> sameNameMethodSignatures = MethodSignatureUtil.findMethodSignaturesByName(aClass,
                                                                                                                           method.getName(),
                                                                                                                           true);
    final MethodSignatureUtil.MethodSignatureToMethods sameSignatureMethods = MethodSignatureUtil.getSameSignatureMethods(aClass);
    MethodSignature originalMethodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    final List<MethodSignatureBackedByPsiMethod> sameSignatureMethodList1 = sameSignatureMethods.get(originalMethodSignature);
    if (sameSignatureMethodList1 == null) return null;
    // find among sameNameMethods method m2 such that it has different signature and there is(are) supermethod(s) with the same erasure
    for (int i = 0; i < sameNameMethodSignatures.size(); i++) {
      final MethodSignatureBackedByPsiMethod methodSignature = sameNameMethodSignatures.get(i);

      if (methodSignature.equals(originalMethodSignature)) continue;
      final List<MethodSignatureBackedByPsiMethod> sameSignatureMethodList2 = sameSignatureMethods.get(methodSignature);
      // if there are methods in sameSignatureMethodList1 and sameSignatureMethodList2 with the same erasure
      for (int j = 0; j < sameSignatureMethodList1.size(); j++) {
        final MethodSignatureBackedByPsiMethod methodSignature1 = sameSignatureMethodList1.get(j);
        if (methodSignature1 == null) continue;
        PsiMethod method1 = methodSignature1.getMethod();
        for (int k = 0; k < sameSignatureMethodList2.size(); k++) {
          final MethodSignatureBackedByPsiMethod methodSignature2 = sameSignatureMethodList2.get(k);
          if (methodSignature2 == null) continue;
          PsiMethod method2 = methodSignature2.getMethod();
          if (method1 != method2 && MethodSignatureUtil.areParametersErasureEqual(method1, method2)) {
            String description = MessageFormat.format("{0}; both methods have same erasure, yet neither overrides the other",
                                                      new Object[]{HighlightMethodUtil.createClashMethodMessage(method1, method2, true)});
            TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
            HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            textRange, 
                                                                            description);
            return highlightInfo;
          }
        }
      }
    }
    return null;
  }

  public static HighlightInfo checkTypeParameterInstantiation(PsiNewExpression expression) {
    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    if (classReference == null) {
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) classReference = anonymousClass.getBaseClassReference();
    }
    if (classReference == null) return null;
    final ResolveResult result = classReference.advancedResolve(false);
    if (result.getElement() instanceof PsiTypeParameter) {
      final PsiTypeParameter typeParameter = (PsiTypeParameter)result.getElement();
      String description = MessageFormat.format("Type parameter ''{0}'' cannot be instantiated directly",
                                                new Object[]{HighlightUtil.formatClass(typeParameter)});
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      classReference,
                                                                      description);
      return highlightInfo;
    }
    return null;
  }

  private static PsiElement getSuperParent(PsiReferenceParameterList paramList) {
    PsiElement parent = paramList.getParent();
    LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement);
    return parent.getParent();
  }

  public static HighlightInfo checkWildcardUsage(PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type instanceof PsiWildcardType) {
      if (typeElement.getParent() instanceof PsiReferenceParameterList) {
        PsiElement refParent = getSuperParent((PsiReferenceParameterList)typeElement.getParent());
        if (refParent instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)refParent;
          if (!(newExpression.getType() instanceof PsiArrayType)) {
            String description = MessageFormat.format("Wildcard type ''{0}'' cannot be instantiated directly", new Object[]{HighlightUtil.formatType(type)});
            HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            typeElement,
                                                                            description);
            return highlightInfo;
          }
        }
        else if (refParent instanceof PsiReferenceList) {
          PsiElement refPParent = refParent.getParent();
          if (!(refPParent instanceof PsiTypeParameter) || refParent != ((PsiTypeParameter)refPParent).getExtendsList()) {
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     typeElement,
                                                     "No wildcard expected");
          }
        }
      }
      else {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 typeElement,
                                                 "Wildcards may be used only as reference parameters");
      }
    }

    return null;
  }

  public static HighlightInfo checkClassUsedAsTypeParameter(PsiTypeElement typeElement) {
    final PsiType type = typeElement.getType();
    if (!(type instanceof PsiPrimitiveType)) return null;

    final PsiElement element = new PsiMatcherImpl(typeElement)
      .parent(PsiMatcherImpl.hasClass(PsiReferenceParameterList.class))
      .parent(PsiMatcherImpl.hasClass(PsiJavaCodeReferenceElement.class))
      .getElement();
    if (element == null) return null;

    String description = MessageFormat.format("Type parameter cannot be of primitive type",
                                              new Object[]{});
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                    typeElement,
                                                                    description);
    return highlightInfo;
  }

  /**
   * precondition: TypeConversionUtil.isAssignable(lType, rType) || expressionAssignable
   */
  public static HighlightInfo checkRawToGenericAssignment(PsiType lType, PsiType rType, PsiElement elementToHighlight) {
    if (elementToHighlight.getManager().getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    if (!isGenericToRaw(lType, rType)) return null;
    String description = MessageFormat.format("Unchecked assignment: ''{0}'' to ''{1}''",
                                              new Object[]{HighlightUtil.formatType(rType), HighlightUtil.formatType(lType)});
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING,
                                                                    elementToHighlight,
                                                                    description);
    QuickFixAction.registerQuickFixAction(highlightInfo, new GenerifyFileFix(elementToHighlight.getContainingFile()));
    return highlightInfo;
  }

  private static boolean isGenericToRaw(PsiType lType, PsiType rType) {
    if (lType == null || rType == null) return false;
    if (!(lType instanceof PsiClassType) || !(rType instanceof PsiClassType)) return false;
    if (!((PsiClassType)rType).isRaw()) return false;
    final PsiClassType lClassType = (PsiClassType)lType;
    if (!lClassType.hasNonTrivialParameters()) return false;
    return true;
  }

  public static HighlightInfo checkUncheckedTypeCast(PsiTypeCastExpression typeCast) {
    if (typeCast.getManager().getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    final PsiTypeElement typeElement = typeCast.getCastType();
    if (typeElement == null) return null;
    final PsiType castType = typeElement.getType();
    final PsiExpression expression = typeCast.getOperand();
    if (expression == null || castType == null) return null;
    final PsiType exprType = expression.getType();
    if (exprType == null) return null;
    if (isUncheckedTypeCast(castType, exprType)) {
      String description = MessageFormat.format("Unchecked cast: ''{0}'' to ''{1}''",
                                                new Object[]{HighlightUtil.formatType(exprType), HighlightUtil.formatType(castType)});
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING,
                                                                      typeCast,
                                                                      description);
      QuickFixAction.registerQuickFixAction(highlightInfo, new GenerifyFileFix(expression.getContainingFile()));
      return highlightInfo;
    }
    return null;
  }

  private static boolean isUncheckedTypeCast(PsiType castType, PsiType exprType) {
    if (exprType instanceof PsiPrimitiveType || castType instanceof PsiPrimitiveType) return false;
    if (exprType.equals(castType)) return false;
    if (exprType instanceof PsiArrayType && castType instanceof PsiArrayType) {
      return isUncheckedTypeCast(((PsiArrayType)castType).getComponentType(), ((PsiArrayType)exprType).getComponentType());
    }
    if (exprType instanceof PsiArrayType || castType instanceof PsiArrayType) return false;
    LOG.assertTrue(exprType instanceof PsiClassType && castType instanceof PsiClassType, "Invalid types: castType =" + castType + ", exprType=" + exprType);
    PsiClassType.ClassResolveResult resolveResult1 = ((PsiClassType)exprType).resolveGenerics();
    PsiClassType.ClassResolveResult resolveResult2 = ((PsiClassType)castType).resolveGenerics();
    PsiClass aClass = resolveResult1.getElement();
    PsiClass bClass = resolveResult2.getElement();
    PsiSubstitutor substitutor1 = resolveResult1.getSubstitutor();
    PsiSubstitutor substitutor2 = resolveResult2.getSubstitutor();
    if (aClass == null || bClass == null) return false;
    if (aClass instanceof PsiTypeParameter || bClass instanceof PsiTypeParameter) return true;
    PsiClass base;
    if (!aClass.getManager().areElementsEquivalent(aClass, bClass)) {
      if (aClass.isInheritor(bClass, true)) {
        base = bClass;
        substitutor1 = TypeConversionUtil.getSuperClassSubstitutor(bClass, aClass, substitutor1);
      } else if (bClass.isInheritor(aClass, true)) {
        base = aClass;
        substitutor2 = TypeConversionUtil.getSuperClassSubstitutor(aClass, bClass, substitutor2);
      } else return false;
    } else base = aClass;

    LOG.assertTrue(substitutor1 != null && substitutor2 != null);
    Iterator<PsiTypeParameter> it = PsiUtil.typeParametersIterator(base);
    while (it.hasNext()) {
      PsiTypeParameter parameter = it.next();
      PsiType typeArg1 = substitutor1.substitute(parameter);
      PsiType typeArg2 = substitutor2.substitute(parameter);
      if (typeArg2 != null && typeArg1 == null) return true;
      if (typeArg2 == null) continue;
      if (isUncheckedTypeArgumentConversion(typeArg1, typeArg2)) return true;
    }
    return false;
  }

  private static boolean isUncheckedTypeArgumentConversion (PsiType type1, PsiType type2) {
    if (type1 instanceof PsiPrimitiveType || type2 instanceof PsiPrimitiveType) return false;
    if (type1.equals(type2)) return false;
    if (type1 instanceof PsiWildcardType || type2 instanceof PsiWildcardType) return true;
    if (type1 instanceof PsiCapturedWildcardType || type2 instanceof PsiCapturedWildcardType) return true;
    if (type1 instanceof PsiArrayType && type2 instanceof PsiArrayType) {
      return isUncheckedTypeArgumentConversion(((PsiArrayType)type2).getComponentType(), ((PsiArrayType)type1).getComponentType());
    }
    if (type1 instanceof PsiArrayType || type2 instanceof PsiArrayType) return false;
    LOG.assertTrue(type1 instanceof PsiClassType && type2 instanceof PsiClassType);
    return ((PsiClassType)type1).resolve() instanceof PsiTypeParameter ||
           ((PsiClassType)type2).resolve() instanceof PsiTypeParameter;
  }

  public static HighlightInfo checkUncheckedCall(ResolveResult resolveResult, PsiCall call) {
    if (call.getManager().getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    final PsiMethod method = (PsiMethod)resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType parameterType = parameter.getType();
      if (parameterType == null) continue;
      if (parameterType.accept(new PsiTypeVisitor<Boolean>() {
        public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
          return Boolean.FALSE;
        }

        public Boolean visitArrayType(PsiArrayType arrayType) {
          return arrayType.getComponentType().accept(this);
        }

        public Boolean visitClassType(PsiClassType classType) {
          PsiClass psiClass = classType.resolve();
          if (psiClass instanceof PsiTypeParameter) {
            return substitutor.substitute((PsiTypeParameter)psiClass) == null ? Boolean.TRUE : Boolean.FALSE;
          }
          PsiType[] parameters = classType.getParameters();
          for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].accept(this).booleanValue()) return Boolean.TRUE;

          }
          return Boolean.FALSE;
        }

        public Boolean visitWildcardType(PsiWildcardType wildcardType) {
          PsiType bound = wildcardType.getBound();
          if (bound != null) return bound.accept(this);
          return Boolean.FALSE;
        }

        public Boolean visitEllipsisType(PsiEllipsisType ellipsisType) {
          return ellipsisType.getComponentType().accept(this);
        }
      }).booleanValue()) {
        final PsiElementFactory elementFactory = method.getManager().getElementFactory();
        PsiType type = elementFactory.createType(method.getContainingClass(), substitutor);
        String description = MessageFormat.format("Unchecked call to ''{0}'' as a member of raw type ''{1}''",
                                                  new Object[]{HighlightUtil.formatMethod(method), HighlightUtil.formatType(type)});
        PsiElement element = call instanceof PsiMethodCallExpression ? (PsiElement)((PsiMethodCallExpression)call).getMethodExpression() : call;
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING, element, description);
        QuickFixAction.registerQuickFixAction(highlightInfo, new GenerifyFileFix(element.getContainingFile()));
        return highlightInfo;
      }
    }
    return null;
  }

  public static HighlightInfo checkForeachLoopParameterType(PsiForeachStatement statement) {
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression expression = statement.getIteratedValue();
    if (expression == null) return null;
    if (parameter == null) return null;
    final PsiType itemType = getCollectionItemType(expression);
    if (itemType == null) {
      String description = MessageFormat.format("foreach not applicable to type ''{0}''.",
                                                new Object[]{HighlightUtil.formatType(expression.getType())});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, description);
    }
    final int start = parameter.getTextRange().getStartOffset();
    final int end = expression.getTextRange().getEndOffset();
    final PsiType parameterType = parameter.getType();
    final HighlightInfo highlightInfo = HighlightUtil.checkAssignability(parameterType, itemType, null, new TextRange(start, end));
    if (highlightInfo != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(parameter, itemType));
    }
    return highlightInfo;
  }

  private static PsiType getCollectionItemType(PsiExpression expression) {
    final PsiType type = expression.getType();
    if (type == null) return null;
    if (type instanceof PsiArrayType) {
      return ((PsiArrayType)type).getComponentType();
    }
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return null;
      final PsiManager manager = aClass.getManager();
      final PsiClass iterable = manager.findClass("java.lang.Iterable", aClass.getResolveScope());
      if (iterable == null) return null;
      final PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(iterable, aClass, PsiSubstitutor.EMPTY);
      if (substitutor == null) return null;
      final PsiTypeParameter itemTypeParameter = iterable.getTypeParameters()[0];
      PsiType itemType = substitutor.substitute(itemTypeParameter);
      itemType = resolveResult.getSubstitutor().substitute(itemType);
      return itemType == null ? PsiType.getJavaLangObject(manager, aClass.getResolveScope()) : itemType;
    }
    return null;
  }

  public static HighlightInfo checkAccessStaticFieldFromEnumConstructor(PsiReferenceExpression expr, ResolveResult result) {
    final PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiField)) return null;
    if (!((PsiField)resolved).hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiMember constructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
    if (constructorOrInitializer == null) return null;
    if (constructorOrInitializer.hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiClass aClass = constructorOrInitializer.getContainingClass();
    if (aClass == null) return null;
    if (!aClass.isEnum()) return null;
    final PsiField field = (PsiField)resolved;
    if (field.getContainingClass() != aClass) return null;
    final PsiType type = field.getType();

    //TODO is access to enum constant is allowed ?
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) return null;

    if (PsiUtil.isCompileTimeConstant(field)) return null;

    String description = MessageFormat.format(
      "It is illegal to access static member ''{0}'' from enum constructor or instance initializer",
      new Object[]{
        HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor())
      });
    final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                          expr,
                                                                          description);
    return highlightInfo;
  }

  public static HighlightInfo checkEnumInstantiation(PsiNewExpression expression) {
    final PsiType type = expression.getType();
    if (type instanceof PsiClassType) {
      final PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass != null && aClass.isEnum()) {
        String description = "Enum types cannot be instantiated";
        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                              expression,
                                                                              description);
        return highlightInfo;
      }
    }
    return null;
  }

  public static HighlightInfo checkGenericArrayCreation(PsiElement element, PsiType type) {
    if (type instanceof PsiArrayType) {
      PsiType componentType = type.getDeepComponentType();
      if (componentType instanceof PsiClassType) {
        PsiType[] parameters = ((PsiClassType)componentType).getParameters();
        for (int i = 0; i < parameters.length; i++) {
          PsiType parameter = parameters[i];
          if (!(parameter instanceof PsiWildcardType) || ((PsiWildcardType)parameter).getBound() != null) {
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     element,
                                                     "Generic array creation");
          }
        }
      }
    }

    return null;
  }

  private static final MethodSignature ourValuesEnumSyntheticMethod = MethodSignatureUtil.createMethodSignature("values",
                                                                                                                PsiType.EMPTY_ARRAY, null,
                                                                                                                PsiSubstitutor.EMPTY);

  public static boolean isEnumSyntheticMethod(MethodSignature methodSignature, Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    final PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    final MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString}, null,
                                                                                    PsiSubstitutor.EMPTY);
    return valueOfMethod.equals(methodSignature);
  }

  public static HighlightInfo checkTypeParametersList(PsiTypeParameterList parameterList) {
    PsiTypeParameter[] typeParameters = parameterList.getTypeParameters();
    if (typeParameters.length == 0) return null;
    final PsiElement parent = parameterList.getParent();
    if (parent instanceof PsiClass && ((PsiClass)parent).isEnum()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     parameterList,
                                                     "Enum may not have type parameters");
    }
    if (parent instanceof PsiAnnotationMethod) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameterList, "@interface members may not have type parameters");
    }
    else if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameterList, "@interface may not have type parameters");
    }

    for (int i = 0; i < typeParameters.length; i++) {
      final PsiTypeParameter typeParameter1 = typeParameters[i];
      String name1 = typeParameter1.getName();
      for (int j = i+1; j < typeParameters.length; j++) {
        final PsiTypeParameter typeParameter2 = typeParameters[j];
        String name2 = typeParameter2.getName();
        if (Comparing.strEqual(name1, name2)) {
          String message = MessageFormat.format("Duplicate type parameter: ''{0}''", new Object[]{name1});
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeParameter2, message);
        }
      }
    }
    return null;
  }

  public static HighlightInfo checkCatchParameterIsClass(PsiParameter parameter) {
    if (parameter.getDeclarationScope() instanceof PsiTryStatement) {
      PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)type).resolve();
        if (aClass instanceof PsiTypeParameter) {
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameter.getTypeElement(), "Cannot catch type parameters");
        }
      }
    }

    return null;
  }

  public static HighlightInfo checkInstanceOfGenericType(PsiInstanceOfExpression expression) {
    PsiType type = expression.getCheckType().getType();
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      if (resolveResult.getElement() == null) return null;
      if (resolveResult.getElement() instanceof PsiTypeParameter) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression.getCheckType(), "Class or array expected");
      }
      else {
        Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(resolveResult.getElement());
        while (iterator.hasNext()) {
          PsiType substituted = resolveResult.getSubstitutor().substitute(iterator.next());
          if (substituted != null && (!(substituted instanceof PsiWildcardType) || ((PsiWildcardType)substituted).getBound() != null)) {
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression.getCheckType(),
                                                     "Illegal generic type for instanceof");
          }
        }
        return null;
      }
    }
    return null;
  }

  public static HighlightInfo checkClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    PsiType type = expression.getOperand().getType();
    if (type instanceof PsiClassType) {
      PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass instanceof PsiTypeParameter) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression.getOperand(), "Cannot select from a type variable");
      }
    }

    return null;
  }
  public static HighlightInfo checkOverrideAnnotation(PsiMethod method) {
    PsiModifierList list = method.getModifierList();
    if (list.findAnnotation("java.lang.Override") != null) {
      PsiClass superClass = method.getContainingClass().getSuperClass();
      if (superClass != null) {
        PsiMethod[] superMethods = method.findSuperMethods();
        for (int i = 0; i < superMethods.length; i++) {
          PsiMethod superMethod = superMethods[i];
          if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return null;
        }
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, method.getNameIdentifier(),
                                                 "Method does not override method from its superclass");
      }
    }
    return null;
  }

  static HighlightInfo checkEnumConstantForConstructorProblems(PsiEnumConstant enumConstant,
                                                               DaemonCodeAnalyzerSettings settings) {
    PsiClass containingClass = enumConstant.getContainingClass();
    PsiClassType type = enumConstant.getManager().getElementFactory().createType(containingClass);
    if (enumConstant.getInitializingClass() == null) {
      HighlightInfo highlightInfo = HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, enumConstant.getNameIdentifier());
      if (highlightInfo != null) return highlightInfo;
    }
    return HighlightMethodUtil.checkConstructorCall(containingClass, enumConstant, type, settings, null);
  }

  public static HighlightInfo checkEnumSuperConstructorCall(PsiMethodCallExpression expr) {
    PsiReferenceExpression methodExpression = expr.getMethodExpression();
    if ("super".equals(methodExpression.getReferenceNameElement().getText())) {
      final PsiMember constructor = PsiUtil.findEnclosingConstructorOrInitializer(expr);
      if (constructor instanceof PsiMethod && constructor.getContainingClass().isEnum()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expr, "Call to super is not allowed in enum constructor");
      }
    }
    return null;
  }

  public static HighlightInfo checkVarArgParameterIsLast(PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiParameter[] params = ((PsiMethod)declarationScope).getParameterList().getParameters();
      if (parameter.isVarArgs() && params[params.length - 1] != parameter) {
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameter, "Vararg parameter must be the last in the list");
        QuickFixAction.registerQuickFixAction(info, new MakeVarargParameterLastFix(parameter));
        return info;
      }
    }
    return null;
  }

  public static List<HighlightInfo> checkEnumConstantModifierList(PsiModifierList modifierList) {
    List<HighlightInfo> list = null;
    PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      if (children[i] instanceof PsiKeyword) {
        if (list == null) {
          list = new ArrayList<HighlightInfo>();
        }
        list.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, children[i], "No modifiers allowed for enum constants"));
      }
    }
    return list;
  }

  public static HighlightInfo checkGenericCallWithRawArguments(ResolveResult resolveResult, PsiCallExpression callExpression) {
    final PsiMethod method = (PsiMethod)resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < expressions.length; i++) {
      PsiParameter parameter = parameters[Math.min(i, parameters.length - 1)];
      final PsiExpression expression = expressions[i];
      final PsiType parameterType = substitutor.substitute(parameter.getType());
      final PsiType expressionType = substitutor.substitute(expression.getType());
      final HighlightInfo highlightInfo = checkRawToGenericAssignment(parameterType, expressionType, expression);
      if (highlightInfo != null) return highlightInfo;
    }
    return null;
  }

  public static HighlightInfo checkParametersOnRaw(PsiReferenceParameterList refParamList) {
    if (refParamList.getTypeArguments().length == 0) return null;
    ResolveResult resolveResult = null;
    PsiElement parent = refParamList.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement) {
      resolveResult = ((PsiJavaCodeReferenceElement)parent).advancedResolve(false);
    } else if (parent instanceof PsiCallExpression) {
      resolveResult =  ((PsiCallExpression)parent).resolveMethodGenerics();
    }
    if (resolveResult != null) {
      PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiTypeParameterListOwner)) return null;
      if (((PsiTypeParameterListOwner)element).hasModifierProperty(PsiModifier.STATIC)) return null;
      PsiClass containingClass = ((PsiTypeParameterListOwner)element).getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutorForClass(containingClass, resolveResult.getSubstitutor())) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, refParamList, "Type arguments given on a raw " + (element instanceof PsiClass ? "type" : "method"));
      }
    }
    return null;
  }

  public static HighlightInfo checkCannotInheritFromEnum(PsiClass superClass, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (Comparing.strEqual("java.lang.Enum",superClass.getQualifiedName())) {
      String message = "Classes cannot directly extend 'java.lang.Enum'";
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, message);
    }
    return errorResult;
  }
}

