package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.JspFileImpl;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspImplicitVariable;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlFile;

public class PsiResolveHelperImpl implements PsiResolveHelper, Constants {
  private final PsiManager myManager;

  public PsiResolveHelperImpl(PsiManager manager) {
    myManager = manager;
  }

  public ResolveResult resolveConstructor(PsiClassType classType, PsiExpressionList argumentList, PsiElement place) {
    ResolveResult[] result = multiResolveConstructor(classType, argumentList, place);
    if (result.length != 1) return ResolveResult.EMPTY;
    return result[0];
  }

  public ResolveResult[] multiResolveConstructor(PsiClassType type, PsiExpressionList argumentList, PsiElement place) {
    final MethodResolverProcessor processor;
    PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
    final PsiClass aClass = classResolveResult.getElement();
    final ResolveResult[] result;
    if (aClass == null) {
      result = ResolveResult.EMPTY_ARRAY;
    }
    else {
      if (argumentList.getParent() instanceof PsiAnonymousClass) {
        processor = new MethodResolverProcessor((PsiClass)argumentList.getParent(), argumentList, place);
      }
      else {
        processor = new MethodResolverProcessor(aClass, argumentList, place);
      }
      PsiScopesUtil.processScope(aClass, processor, classResolveResult.getSubstitutor(), aClass, place);

      // getting the most suitable myResult
      result = processor.getResult();
    }
    return result;
  }

  public PsiClass resolveReferencedClass(String referenceText, PsiElement context) {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement ref = Parsing.parseJavaCodeReferenceText(myManager, referenceText.toCharArray(), holderElement.getCharTable());
    if (ref == null) return null;
    if (!SourceTreeToPsiMap.hasTreeElement(context)) { //???
      return myManager.findClass(referenceText, context.getResolveScope());
    }
    TreeUtil.addChildren(holderElement, ref);

    return ResolveClassUtil.resolveClass((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref));
  }

  public PsiVariable resolveReferencedVariable(String referenceText, PsiElement context) {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement ref = Parsing.parseJavaCodeReferenceText(myManager, referenceText.toCharArray(), holderElement.getCharTable());
    if (ref == null) return null;
    TreeUtil.addChildren(holderElement, ref);
    PsiJavaCodeReferenceElement psiRef = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref);
    return ResolveVariableUtil.resolveVariable(psiRef, null, null);
  }

  public boolean isAccessible(PsiMember member, PsiElement place, PsiClass accessObjectClass) {
    return isAccessible(member, member.getModifierList(), place, accessObjectClass);
  }


  public boolean isAccessible(PsiMember member, PsiModifierList modifierList, PsiElement place, PsiClass accessObjectClass) {
    if (modifierList == null) return true;
    final PsiFile placeContainingFile = place.getContainingFile();
    final PsiManager manager = placeContainingFile.getManager();
    if (placeContainingFile instanceof PsiCodeFragment) {
      PsiCodeFragment fragment = (PsiCodeFragment)placeContainingFile;
      PsiCodeFragment.VisibilityChecker visibilityChecker = fragment.getVisibilityChecker();
      if (visibilityChecker != null) {
        PsiCodeFragment.VisibilityChecker.Visibility visibility = visibilityChecker.isDeclarationVisible(member, place);
        if (visibility == PsiCodeFragment.VisibilityChecker.Visibility.VISIBLE) return true;
        if (visibility == PsiCodeFragment.VisibilityChecker.Visibility.NOT_VISIBLE) return false;
      }
    }
    else if (placeContainingFile instanceof XmlFile) return true;
    // We don't care about access rights in javadoc
    if (ResolveUtil.findParentContextOfClass(place, PsiDocComment.class, false) != null) return true;

    // access modifier of member as it seen from the place.
    // In case of methods it can be different from member modifier,
    //  if method is overridden in subclass with different access modifier. See SCR 9547
    if (accessObjectClass != null && !isAccessible(accessObjectClass, place, null)) return false;

    int effectiveAccessLevel = PsiUtil.getAccessLevel(modifierList);
    PsiFile file = ResolveUtil.getContextFile(place); //TODO: implementation method!!!!
    if (file instanceof JspFile && (member.getContainingFile() instanceof JspFile || member instanceof JspImplicitVariable)) return true;
    if (file instanceof XmlFile) return true;
    if (effectiveAccessLevel == PsiUtil.ACCESS_LEVEL_PUBLIC) {
      return true;
    }
    else if (effectiveAccessLevel == PsiUtil.ACCESS_LEVEL_PROTECTED) {
      if (manager.arePackagesTheSame(member, place)) return true;
      PsiClass memberClass = member.getContainingClass();
      if (memberClass == null) return false;

      for (PsiElement placeParent = place; placeParent != null; placeParent = placeParent.getContext()) {
        if (placeParent instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)placeParent, memberClass, true)) {
          if (member instanceof PsiClass || modifierList.hasModifierProperty(PsiModifier.STATIC)) return true;
          if (accessObjectClass == null) return true;
          if (manager.areElementsEquivalent(accessObjectClass, placeParent)
              || accessObjectClass.isInheritor((PsiClass)placeParent, true)) {
            return true;
          }
        }
        if (placeParent instanceof JspFileImpl && InheritanceUtil.isInheritorOrSelf(((JspFileImpl)placeParent).getBaseClass(), memberClass, true)) {
          if (accessObjectClass == null) return true;
          if (accessObjectClass.isInheritor(((JspFileImpl)placeParent).getBaseClass(), true)) return true;
        }
      }
      return false;
    }
    else if (effectiveAccessLevel == PsiUtil.ACCESS_LEVEL_PRIVATE) {
      final PsiClass memberClass = member.getContainingClass();
      if (accessObjectClass != null) {
        if (!manager.areElementsEquivalent(memberClass, accessObjectClass)) return false;
      }

      boolean isConstructor = member instanceof PsiMethod && ((PsiMethod)member).isConstructor();
      PsiClass placeClass = getPlaceTopLevelClass (place, memberClass, isConstructor);
      if (placeClass != null) {
        for (PsiElement memberClassParent = memberClass; memberClassParent != null; memberClassParent = memberClassParent.getContext()) {
          if (manager.areElementsEquivalent(placeClass, memberClassParent)) return true;
        }
        for (PsiElement placeClassParent = placeClass; placeClassParent != null; placeClassParent = placeClassParent.getContext()) {
          if (manager.areElementsEquivalent(memberClass, placeClassParent)) return true;
        }
      }

      return false;
    }
    else {
      if (!manager.arePackagesTheSame(member, place)) return false;
      if (modifierList.hasModifierProperty(PsiModifier.STATIC)) return true;
      // maybe inheritance lead through package local class in other package ?
      final PsiClass memberClass = member.getContainingClass();
      final PsiClass placeClass = ResolveUtil.getContextClass(place);
      if (memberClass == null || placeClass == null) return true;
      // check only classes since interface members are public,  and if placeClass is interface,
      // then its members are static, and cannot refer to nonstatic members of memberClass
      if (memberClass.isInterface() || placeClass.isInterface()) return true;
      if (placeClass.isInheritor(memberClass, true)) {
        PsiClass superClass = placeClass.getSuperClass();
        while (!manager.areElementsEquivalent(superClass, memberClass)) {
          if (!manager.arePackagesTheSame(superClass, memberClass)) return false;
          superClass = superClass.getSuperClass();
        }
      }

      return true;
    }
  }

  private PsiClass getPlaceTopLevelClass(PsiElement place, PsiClass memberClass, boolean isPrivateConstructor) {
    PsiManager manager = place.getManager();
    PsiClass lastClass = null;
    for (PsiElement placeParent = place; placeParent != null; placeParent = placeParent.getContext()) {
      if (placeParent instanceof PsiClass) {
        PsiClass aClass = (PsiClass)placeParent;

        //what we see is the member from the base class rather than enclosing one
        //Private constructors are the exceptions: they ARE accessible from decendants
        if (!isPrivateConstructor && manager.areElementsEquivalent(memberClass, aClass.getSuperClass())) return aClass;
        lastClass = aClass;
      }
    }

    return lastClass;
  }

  public CandidateInfo[] getReferencedMethodCandidates(PsiCallExpression expr, boolean dummyImplicitConstructor) {
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(expr);
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, expr, dummyImplicitConstructor);
    }
    catch (MethodProcessorSetupFailedException e) {
      return CandidateInfo.EMPTY_ARRAY;
    }
    return processor.getCandidates();
  }

  public PsiType inferTypeForMethodTypeParameter(final PsiTypeParameter typeParameter,
                                                 final PsiParameter[] parameters,
                                                 PsiExpression[] arguments, PsiSubstitutor partialSubstitutor, PsiElement parent) {
    PsiType substitution = PsiType.NULL;
    if (parameters.length > 0) {
      for (int j = 0; j < arguments.length; j++) {
        PsiExpression argument = arguments[j];
        final PsiParameter parameter = parameters[Math.min(j, parameters.length - 1)];
        if (j >= parameters.length && !parameter.isVarArgs()) break;
        final PsiType currentSubstitution = getSubstitutionForTypeParameter(typeParameter, parameter.getType(),
                                                                                         argument.getType(), true);
        if (currentSubstitution == null) {
          substitution = null;
          break;
        } else if (currentSubstitution instanceof PsiWildcardType) {
          if (substitution instanceof PsiWildcardType) return PsiType.NULL;
        } else if (currentSubstitution == PsiType.NULL) continue;

        if (substitution == PsiType.NULL) {
          substitution = currentSubstitution;
          continue;
        }
        if (!substitution.equals(currentSubstitution) && !substitution.isAssignableFrom(currentSubstitution)) {
          substitution = GenericsUtil.getLeastUpperBound(substitution, currentSubstitution, typeParameter.getManager());
          if (substitution == null) {
            break;
          }
        }
      }
    }

    if (substitution == PsiType.NULL) {
      substitution = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent);
    }
    return substitution;
  }

  private PsiType processArgType(PsiType arg, boolean captureWildcard) {
    if (arg instanceof PsiWildcardType) {
      return captureWildcard ? arg : PsiType.NULL;
    } else {
      if (arg == null || arg.getDeepComponentType() instanceof PsiPrimitiveType ||
          PsiUtil.resolveClassInType(arg) != null) return arg;
    }

    return PsiType.NULL;
  }

  private PsiType inferMethodTypeParameterFromParent(final PsiTypeParameter typeParameter,
                                                            PsiSubstitutor substitutor,
                                                            PsiElement parent) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    PsiType substitution = PsiType.NULL;
    if (owner instanceof PsiMethod) {
      if (parent instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
        substitution = inferMethodTypeParameterFromParent(methodCall.getParent(), methodCall, typeParameter, substitutor);
      }
    }
    return substitution;
  }

  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                        PsiType param,
                                                        PsiType arg,
                                                        boolean isContraVariantPosition) {
    if (param instanceof PsiEllipsisType) {
      if (arg instanceof PsiArrayType) arg = ((PsiArrayType)arg).getComponentType();
      return getSubstitutionForTypeParameter(typeParam, ((PsiEllipsisType)param).getComponentType(), arg, isContraVariantPosition);
    }

    if (param instanceof PsiArrayType && arg instanceof PsiArrayType) {
      return getSubstitutionForTypeParameter(typeParam, ((PsiArrayType)param).getComponentType(), ((PsiArrayType)arg).getComponentType(),
                                             isContraVariantPosition);
    }

    if (param instanceof PsiClassType) {
      PsiManager manager = typeParam.getManager();
      if (arg instanceof PsiPrimitiveType) {
        arg = ((PsiPrimitiveType)arg).getBoxedType(manager, typeParam.getResolveScope());
        if (arg == null) return PsiType.NULL;
      }

      ResolveResult paramResult = ((PsiClassType)param).resolveGenerics();
      PsiClass paramClass = (PsiClass)paramResult.getElement();
      if (typeParam == paramClass) {
        return arg == null || arg.getDeepComponentType() instanceof PsiPrimitiveType ||
               PsiUtil.resolveClassInType(arg) != null ? arg : PsiType.NULL;
      }
      if (paramClass == null) return PsiType.NULL;

      if (arg instanceof PsiClassType) {
        ResolveResult argResult = ((PsiClassType)arg).resolveGenerics();
        PsiClass argClass = (PsiClass)argResult.getElement();
        if (argClass == null) return PsiType.NULL;

        PsiElementFactory factory = manager.getElementFactory();
        PsiType patternType = factory.createType(typeParam);
        if (isContraVariantPosition) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(paramClass, argClass, argResult.getSubstitutor());
          if (substitutor == null) return PsiType.NULL;
          arg = factory.createType(paramClass, substitutor);
        }
        else {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(argClass, paramClass, paramResult.getSubstitutor());
          if (substitutor == null) return PsiType.NULL;
          param = factory.createType(argClass, substitutor);
        }

        PsiType substitution = getSubstitutionForTypeParameterInner(param, arg, patternType, false);
        return substitution != PsiType.NULL ? substitution : getSubstitutionForTypeParameterInner(param, arg, patternType, true);
      }
    }

    return PsiType.NULL;
  }

  private PsiType getSubstitutionForTypeParameterInner(PsiType param,
                                                              PsiType arg,
                                                              PsiType patternType,
                                                              boolean captureWildcard) {
    if (param instanceof PsiWildcardType) {
      if (arg instanceof PsiWildcardType && ((PsiWildcardType)arg).isExtends() == ((PsiWildcardType)param).isExtends()) {
        PsiType res = getSubstitutionForTypeParameterInner(((PsiWildcardType)param).getBound(), ((PsiWildcardType)arg).getBound(),
                                                           patternType, captureWildcard);
        if (res != PsiType.NULL) return res;
      }
      else if (patternType.equals(((PsiWildcardType)param).getBound())) {
        if (((PsiWildcardType)param).isExtends()) {
          return processArgType(arg, captureWildcard);
        }
      }
    }
    else if (patternType.equals(param)) {
      return processArgType(arg, captureWildcard);
    }

    if (param instanceof PsiArrayType && arg instanceof PsiArrayType) {
      return getSubstitutionForTypeParameterInner(((PsiArrayType)param).getComponentType(), ((PsiArrayType)arg).getComponentType(),
                                                  patternType, captureWildcard);
    }

    if (param instanceof PsiClassType && arg instanceof PsiClassType) {
      PsiClass paramClass = ((PsiClassType)param).resolve();
      if (paramClass == null) return PsiType.NULL;

      PsiClass argClass = ((PsiClassType)arg).resolve();
      if (argClass != paramClass) return PsiType.NULL;

      PsiType[] paramTypes = ((PsiClassType)param).getParameters();
      PsiType[] argTypes = ((PsiClassType)arg).getParameters();
      if (paramTypes.length != argTypes.length) return PsiType.NULL;
      PsiType capturedWildcard = PsiType.NULL;
      for (int i = 0; i < argTypes.length; i++) {
        PsiType res = getSubstitutionForTypeParameterInner(paramTypes[i], argTypes[i], patternType, captureWildcard);
        if (res != PsiType.NULL) {
          if (!captureWildcard) {
            return res;
          }
          else {
            if (capturedWildcard != PsiType.NULL) {
              return PsiType.NULL;
            }
            else {
              capturedWildcard = res;
            }
          }
        }
      }

      return capturedWildcard;
    }

    return PsiType.NULL;
  }

  private PsiType inferMethodTypeParameterFromParent(PsiElement parent,
                                                     PsiMethodCallExpression methodCall,
                                                     final PsiTypeParameter typeParameter,
                                                     PsiSubstitutor substitutor) {
    PsiType type = null;

    if (parent instanceof PsiVariable && methodCall.equals(((PsiVariable)parent).getInitializer())) {
      type = ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression && methodCall.equals(((PsiAssignmentExpression)parent).getRExpression())) {
      type = ((PsiAssignmentExpression)parent).getLExpression().getType();
    }
    else if (parent instanceof PsiTypeCastExpression && methodCall.equals(((PsiTypeCastExpression)parent).getOperand())) {
      type = ((PsiTypeCastExpression)parent).getType();
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        type = method.getReturnType();
      }
    }

    if (type == null) {
      type = PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope());
    }

    PsiType returnType = ((PsiMethod)typeParameter.getOwner()).getReturnType();
    PsiType guess = getSubstitutionForTypeParameter(typeParameter, returnType, type, false);

    if (guess == PsiType.NULL) {
      PsiType superType = substitutor.substitute(typeParameter.getSuperTypes()[0]);
      return superType == null ? PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope()) : superType;
    }

    //The following code is the result of deep thought, do not shit it out before discussing with [ven]
    if (returnType instanceof PsiClassType && typeParameter.equals(((PsiClassType)returnType).resolve())) {
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      PsiSubstitutor newSubstitutor = substitutor.put(typeParameter, guess);
      for (int i = 0; i < extendsTypes.length; i++) {
        PsiType extendsType = newSubstitutor.substitute(extendsTypes[i]);
        if (!extendsType.isAssignableFrom(guess)) {
          if (guess.isAssignableFrom(extendsType)) {
            guess = extendsType;
            newSubstitutor = substitutor.put(typeParameter, guess);
          }
          else {
            break;
          }
        }
      }
    }

    return guess;
  }
}
