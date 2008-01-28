package com.intellij.refactoring.openapi.impl;

import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.inline.InlineHandler;
import com.intellij.refactoring.typeCook.TypeCookHandler;
import com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsHandler;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryHandler;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;
import com.intellij.refactoring.makeStatic.MakeStaticHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.tempWithQuery.TempWithQueryHandler;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperHandler;
import com.intellij.refactoring.memberPushDown.PushDownHandler;
import com.intellij.refactoring.memberPullUp.PullUpHandler;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;

public class JavaRefactoringActionHandlerFactoryImpl extends JavaRefactoringActionHandlerFactory {
  public RefactoringActionHandler createAnonymousToInnerHandler() {
    return new AnonymousToInnerHandler();
  }

  public RefactoringActionHandler createPullUpHandler() {
    return new PullUpHandler();
  }

  public RefactoringActionHandler createPushDownHandler() {
    return new PushDownHandler();
  }

  public RefactoringActionHandler createTurnRefsToSuperHandler() {
    return new TurnRefsToSuperHandler();
  }

  public RefactoringActionHandler createTempWithQueryHandler() {
    return new TempWithQueryHandler();
  }

  public RefactoringActionHandler createIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  public RefactoringActionHandler createMakeMethodStaticHandler() {
    return new MakeStaticHandler();
  }

  public RefactoringActionHandler createConvertToInstanceMethodHandler() {
    return new ConvertToInstanceMethodHandler();
  }

  public RefactoringActionHandler createReplaceConstructorWithFactoryHandler() {
    return new ReplaceConstructorWithFactoryHandler();
  }

  public RefactoringActionHandler createEncapsulateFieldsHandler() {
    return new EncapsulateFieldsHandler();
  }

  public RefactoringActionHandler createMethodDuplicatesHandler() {
    return new MethodDuplicatesHandler();
  }

  public RefactoringActionHandler createChangeSignatureHandler() {
    return new ChangeSignatureHandler();
  }

  public RefactoringActionHandler createExtractSuperclassHandler() {
    return new ExtractSuperclassHandler();
  }

  public RefactoringActionHandler createTypeCookHandler() {
    return new TypeCookHandler();
  }

  public RefactoringActionHandler createInlineHandler() {
    return new InlineHandler();
  }

  public RefactoringActionHandler createExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  public RefactoringActionHandler createInheritanceToDelegationHandler() {
    return new InheritanceToDelegationHandler();
  }

  public RefactoringActionHandler createExtractInterfaceHandler() {
    return new ExtractInterfaceHandler();
  }

  public RefactoringActionHandler createIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  public RefactoringActionHandler createIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  public RefactoringActionHandler createIntroduceConstantHandler() {
    return new IntroduceConstantHandler();
  }
}
