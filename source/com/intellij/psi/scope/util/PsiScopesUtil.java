/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Nov 25, 2002
 * Time: 2:05:49 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.psi.scope.util;

import com.intellij.openapi.diagnostic.Logger;
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
      if (!owner.isMetaEnough()) {
        if (!scope.processDeclarations(processor, substitutor, lastParent, place)) return false;
      }

      final PsiMetaData data = owner.getMetaData();
      if(data != null) {
        if(!data.processDeclarations(scope, processor, substitutor, lastParent, place))
          return false;
      }

      return true;
    }
    else{
      return scope.processDeclarations(processor, substitutor, lastParent, place);
    }
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
          final ResolveResult result = PsiUtil.resolveGenericsClassInType(type);
          target = result.getElement();
          substitutor = result.getSubstitutor();
        }

        if(type == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          // In case of class qualifier
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)qualifier;
          final ResolveResult result = referenceElement.advancedResolve(incompleteCode);
          target = result.getElement();
          substitutor = result.getSubstitutor();

          if(target instanceof PsiVariable){
            type = substitutor.substitute(((PsiVariable) target).getType());
            if(type instanceof PsiClassType){
              final ResolveResult typeResult = ((PsiClassType) type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.merge(typeResult.getSubstitutor());
            }
            else target = null;
          }
          else if(target instanceof PsiMethod){
            type = substitutor.substitute(((PsiMethod) target).getReturnType());
            if(type instanceof PsiClassType){
              final ResolveResult typeResult = ((PsiClassType) type).resolveGenerics();
              target = typeResult.getElement();
              substitutor = substitutor.merge(typeResult.getSubstitutor());
            }
            else target = null;
          }
          else if(target instanceof PsiClass){
            type = factory.createType((PsiClass)target);
            processor.handleEvent(PsiScopeProcessor.Event.START_STATIC, null);
          }
          final PsiType[] types = referenceElement.getTypeParameters();
          if(target instanceof PsiClass) {
            substitutor = ((PsiSubstitutorEx)substitutor).inplacePutAll((PsiClass)target, types);
          }
        }

        if (type instanceof PsiArrayType){
          target = factory.getArrayClass();
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
              throw new MethodProcessorSetupFailedException("Cant resolve class for this expression");
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
              throw new MethodProcessorSetupFailedException("Cant resolve class for super expression");
            }

            final PsiClass superClass = aClass.getSuperClass();
            if (superClass != null) {  //null for java.lang.Object only
              PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
              LOG.assertTrue(substitutor != null);
              processor.setIsConstructor(true);
              processor.setAccessClass(superClass);
              processScope(superClass, processor, substitutor, null, call);

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
          PsiScopesUtil.resolveAndWalk(processor, ref, null);
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
              final ResolveResult result = ((PsiJavaCodeReferenceElement) qualifier).advancedResolve(false);
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
            for (int i = 0; i < conjuncts.length; i++) {
              if (!processQualifierType (conjuncts[i], processor, manager, methodCall)) break;
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

      final ResolveResult result = classRef.advancedResolve(false);
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
      ResolveResult qualifierResult = ((PsiClassType)type).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }
    else if (type instanceof PsiArrayType) {
      ResolveResult qualifierResult = manager.getElementFactory().getArrayClassType(((PsiArrayType)type).getComponentType()).resolveGenerics();
      return processQualifierResult(qualifierResult, processor, call);
    }

    return true;
  }

  private static boolean processQualifierResult(ResolveResult qualifierResult,
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
      if (qualifier instanceof PsiSuperExpression) {
        processor.setAccessClass((PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement());
      }
      else
        processor.setAccessClass((PsiClass)resolve);
    }

    processor.setIsConstructor(false);
    processor.setName(methodCall.getMethodExpression().getReferenceName());
    return processScope(resolve, processor, qualifierResult.getSubstitutor(), methodCall, methodCall);
  }

  private static void processDummyConstructor(MethodsProcessor processor, PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return;
    try{
      PsiMethod[] methods = aClass.getMethods();
      for(int i = 0; i < methods.length; i++){
        PsiMethod method = methods[i];
        if(method.isConstructor()){
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
