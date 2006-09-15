/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 2:05:49 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.psi.scope.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorEx;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

public class PsiScopesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.scope.util.PsiScopesUtil");

  private PsiScopesUtil() {
  }

  public static boolean treeWalkUp(PsiScopeProcessor processor, PsiElement entrance, PsiElement maxScope) {
    PsiElement prevParent = entrance;
    PsiElement scope = entrance;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;

    while(scope != null){
      if(scope instanceof PsiClass){
        processor.handleEvent(PsiScopeProcessor.Event.SET_CURRENT_FILE_CONTEXT, scope);
      }
      if (!processScope(scope, processor, substitutor, prevParent, entrance)) return false;

      if (scope instanceof PsiModifierListOwner && !(scope instanceof PsiParameter/* important for not loading tree! */)){
        PsiModifierList modifierList = ((PsiModifierListOwner)scope).getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)){
          processor.handleEvent(PsiScopeProcessor.Event.START_STATIC, null);
        }
      }
      if (scope == maxScope) break;
      prevParent = scope;
      scope = prevParent.getContext();
      processor.handleEvent(PsiScopeProcessor.Event.CHANGE_LEVEL, null);
    }

    return true;
  }

  public static boolean processScope(PsiElement scope, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place){
    if(scope instanceof PsiMetaOwner){
      final PsiMetaOwner owner = (PsiMetaOwner) scope;
      if (!owner.isMetaEnough() && !scope.processDeclarations(processor, substitutor, lastParent, place)) {
        return false;
      }

      final PsiMetaData data = owner.getMetaData();
      return data == null || data.processDeclarations(scope, processor, substitutor, lastParent, place);

    }
    return scope.processDeclarations(processor, substitutor, lastParent, place);
  }

  public static boolean walkChildrenScopes(PsiElement thisElement, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    PsiElement child = null;
    if (lastParent != null && lastParent.getParent() == thisElement){
      child = lastParent.getPrevSibling();
      if (child == null) return true; // first element
    }

    if (child == null){
      child = thisElement.getLastChild();
    }

    while(child != null){
      if (!processScope(child, processor, substitutor, null, place)) return false;
      child = child.getPrevSibling();
    }

    return true;
  }

  public static boolean resolveAndWalk(PsiScopeProcessor processor, PsiJavaCodeReferenceElement ref, PsiElement maxScope) {
    return resolveAndWalk(processor, ref, maxScope, false);
  }

  public static boolean resolveAndWalk(PsiScopeProcessor processor, PsiJavaCodeReferenceElement ref, PsiElement maxScope, boolean incompleteCode) {
    final PsiElement qualifier = ref.getQualifier();
    final PsiElement classNameElement = ref.getReferenceNameElement();
    if(classNameElement == null) return true;
    if (qualifier != null){
      // Composite expression
      final PsiElementFactory factory = ref.getManager().getElementFactory();
      PsiElement target = null;
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      PsiType type = null;
      if (qualifier instanceof PsiExpression || qualifier instanceof PsiJavaCodeReferenceElement){
        if(qualifier instanceof PsiExpression){
          type = ((PsiExpression)qualifier).getType();
          if (type instanceof PsiArrayType) {
            LanguageLevel languageLevel = PsiUtil.getLanguageLevel(qualifier);
            final PsiClass arrayClass = factory.getArrayClass(languageLevel);
            target = arrayClass;
            final PsiTypeParameter[] arrayTypeParameters = arrayClass.getTypeParameters();
            if (arrayTypeParameters.length > 0) {
              substitutor = substitutor.put(arrayTypeParameters[0], ((PsiArrayType)type).getComponentType());
            }
          } else {
            final JavaResolveResult result = PsiUtil.resolveGenericsClassInType(type);
            target = result.getElement();
            substitutor = result.getSubstitutor();
          }
        }

        if(type == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          // In case of class qualifier
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)qualifier;
          final JavaResolveResult result = referenceElement.advancedResolve(incompleteCode);
          target = result.getElement();
          substitutor = result.getSubstitutor();

          if(target instanceof PsiVariable){
            type = substitutor.substitute(((PsiVariable) target).getType());
            if(type instanceof PsiClassType){
              final JavaResolveResult typeResult = ((PsiClassType) type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.putAll(typeResult.getSubstitutor());
            }
            else target = null;
          }
          else if(target instanceof PsiMethod){
            type = substitutor.substitute(((PsiMethod) target).getReturnType());
            if(type instanceof PsiClassType){
              final JavaResolveResult typeResult = ((PsiClassType) type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.putAll(typeResult.getSubstitutor());
            }
            else target = null;
          }
          else if(target instanceof PsiClass){
            processor.handleEvent(PsiScopeProcessor.Event.START_STATIC, null);
          }
          final PsiType[] types = referenceElement.getTypeParameters();
          if(target instanceof PsiClass) {
            substitutor = ((PsiSubstitutorEx)substitutor).inplacePutAll((PsiClass)target, types);
          }
        }
      }

      if(target != null) return processScope(target, processor, substitutor, target, ref);
    }
    else{
      // simple expression -> trying to resolve variable or method
      return treeWalkUp(processor, ref, maxScope);
    }

    return true;
  }

  public static void setupAndRunProcessor(MethodsProcessor processor, PsiCallExpression call, boolean dummyImplicitConstructor)
  throws MethodProcessorSetupFailedException{
    if (call instanceof PsiMethodCallExpression){
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)call;
      final PsiJavaCodeReferenceElement ref = methodCall.getMethodExpression();

      processor.setArgumentList(methodCall.getArgumentList());
      processor.obtainTypeArguments(methodCall);
      if (!ref.isQualified() || ref.getReferenceNameElement() instanceof PsiKeyword){
        final PsiElement referenceNameElement = ref.getReferenceNameElement();
        if (referenceNameElement == null) return;
        if (referenceNameElement instanceof PsiKeyword){
          final PsiKeyword keyword = (PsiKeyword)referenceNameElement;

          if (keyword.getTokenType() == JavaTokenType.THIS_KEYWORD){
            final PsiClass aClass = ResolveUtil.getContextClass(methodCall);
            if (aClass == null) {
              throw new MethodProcessorSetupFailedException("Can't resolve class for this expression");
            }

            processor.setIsConstructor(true);
            processor.setAccessClass(aClass);
            processScope(aClass, processor, PsiSubstitutor.EMPTY, null, call);

            if (dummyImplicitConstructor){
              processDummyConstructor(processor, aClass);
            }
          }
          else if (keyword.getTokenType() == JavaTokenType.SUPER_KEYWORD){
            PsiClass aClass = ResolveUtil.getContextClass(methodCall);
            if (aClass == null) {
              throw new MethodProcessorSetupFailedException("Can't resolve class for super expression");
            }

            final PsiClass superClass = aClass.getSuperClass();
            if (superClass != null) {
              PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
              PsiClass runSuper = superClass;
              do {
                if (runSuper != null) {
                  PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(runSuper, aClass, PsiSubstitutor.EMPTY);
                  LOG.assertTrue(superSubstitutor != null);
                  substitutor = substitutor.putAll(superSubstitutor);
                }
                if (aClass.hasModifierProperty(PsiModifier.STATIC)) break;
                aClass = ResolveUtil.getContextClass(aClass);
                if (aClass != null) runSuper = aClass.getSuperClass();
              } while (aClass != null);

              processor.setIsConstructor(true);
              processor.setAccessClass(null);
              final PsiMethod[] constructors = superClass.getConstructors();
              for (PsiMethod constructor : constructors) {
                if (!processor.execute(constructor, substitutor)) return;
              }

              if (dummyImplicitConstructor) processDummyConstructor(processor, superClass);
            }
          }
          else{
            LOG.assertTrue(false, "Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
          }
        }
        else if (referenceNameElement instanceof PsiIdentifier){
          processor.setIsConstructor(false);
          processor.setName(referenceNameElement.getText());
          processor.setAccessClass(null);
          resolveAndWalk(processor, ref, null);
        }
        else{
          LOG.assertTrue(false, "Unknown name element " + referenceNameElement + " in reference " + ref.getText() + "(" + ref + ")");
        }
      }
      else{
        // Complex expression
        final PsiElement referenceName = methodCall.getMethodExpression().getReferenceNameElement();
        final PsiManager manager = call.getManager();
        final PsiElement qualifier = ref.getQualifier();

        if (referenceName instanceof PsiIdentifier && qualifier instanceof PsiExpression){
          PsiType type = ((PsiExpression) qualifier).getType();
          if (type == null) {
            if (qualifier instanceof PsiJavaCodeReferenceElement) {
              final JavaResolveResult result = ((PsiJavaCodeReferenceElement) qualifier).advancedResolve(false);
              if (result.getElement() instanceof PsiClass) {
                processor.handleEvent(PsiScopeProcessor.Event.START_STATIC, null);
                processQualifierResult(result, processor, methodCall);
              }
            }
            else {
              throw new MethodProcessorSetupFailedException("Cant determine qualifier type!");
            }
          } else if (type instanceof PsiIntersectionType) {
            final PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
            for (PsiType conjunct : conjuncts) {
              if (!processQualifierType(conjunct, processor, manager, methodCall)) break;
            }
          } else {
            processQualifierType(type, processor, manager, methodCall);
          }
        }
        else{
          LOG.assertTrue(false);
        }
      }
    } else{
      LOG.assertTrue(call instanceof PsiNewExpression);
      PsiNewExpression newExpr = (PsiNewExpression)call;
      PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
      if (classRef == null) {
        PsiAnonymousClass anonymousClass = newExpr.getAnonymousClass();
        if (anonymousClass != null) {
          classRef = anonymousClass.getBaseClassReference();
        }
        if (classRef == null) {
          throw new MethodProcessorSetupFailedException("Cant get reference to class in new expression");
        }
      }

      final JavaResolveResult result = classRef.advancedResolve(false);
      PsiClass aClass = (PsiClass) result.getElement();
      if (aClass == null)
        throw new MethodProcessorSetupFailedException("Cant resolve class in new expression");
      processor.setIsConstructor(true);
      processor.setAccessClass(aClass);
      processor.setArgumentList(newExpr.getArgumentList());
      processor.obtainTypeArguments(newExpr);
      processScope(aClass, processor, result.getSubstitutor(), null, call);

      if (dummyImplicitConstructor){
        processDummyConstructor(processor, aClass);
      }
    }
  }

  private static boolean processQualifierType(final PsiType type,
                                         final MethodsProcessor processor,
                                         PsiManager manager,
                                         PsiMethodCallExpression call) throws MethodProcessorSetupFailedException {
    if (type instanceof PsiClassType) {
      JavaResolveResult qualifierResult = ((PsiClassType)type).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }
    else if (type instanceof PsiArrayType) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
      JavaResolveResult qualifierResult = manager.getElementFactory().getArrayClassType(((PsiArrayType)type).getComponentType(),
                                                                                        languageLevel).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }
    else if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        if (!processQualifierType(conjunct, processor, manager, call)) return false;
      }
    }

    return true;
  }

  private static boolean processQualifierResult(JavaResolveResult qualifierResult,
                                           final MethodsProcessor processor,
                                           PsiMethodCallExpression methodCall) throws MethodProcessorSetupFailedException {
    PsiElement resolve = qualifierResult.getElement();

    if (resolve == null)
      throw new MethodProcessorSetupFailedException("Cant determine qualifier class!");

    if (resolve instanceof PsiTypeParameter) {
      final PsiType paramType = qualifierResult.getSubstitutor().substitute((PsiTypeParameter)resolve);
      qualifierResult = PsiUtil.resolveGenericsClassInType(paramType);
      resolve = qualifierResult.getElement();
    } else if (resolve instanceof PsiClass) {
      PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
      //if (qualifier instanceof PsiSuperExpression) {
        processor.setAccessClass((PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement());
      //}
      //else
      //  processor.setAccessClass((PsiClass)resolve);
    }

    processor.setIsConstructor(false);
    processor.setName(methodCall.getMethodExpression().getReferenceName());
    return processScope(resolve, processor, qualifierResult.getSubstitutor(), methodCall, methodCall);
  }

  private static void processDummyConstructor(MethodsProcessor processor, PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return;
    try{
      PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        if (method.isConstructor()) {
          return;
        }
      }
      final PsiElementFactory factory = aClass.getManager().getElementFactory();
      final PsiMethod dummyConstructor = factory.createConstructor();
      if(aClass.getNameIdentifier() != null){
        dummyConstructor.getNameIdentifier().replace(aClass.getNameIdentifier());
      }
      processor.forceAddResult(dummyConstructor);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }
}
