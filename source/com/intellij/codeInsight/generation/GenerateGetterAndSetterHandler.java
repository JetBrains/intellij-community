package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

public class GenerateGetterAndSetterHandler extends GenerateGetterSetterHandlerBase{
  private final GenerateGetterHandler myGenerateGetterHandler = new GenerateGetterHandler();
  private final GenerateSetterHandler myGenerateSetterHandler = new GenerateSetterHandler();

  public GenerateGetterAndSetterHandler(){
    super("Select Fields to Generate Getters and Setters");
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, Object original) throws IncorrectOperationException {
    ArrayList array = new ArrayList();
    Object[] getters = myGenerateGetterHandler.generateMemberPrototypes(aClass, original);
    Object[] setters = myGenerateSetterHandler.generateMemberPrototypes(aClass, original);

    if (getters.length > 0 && setters.length > 0){
      array.add(getters[0]);
      array.add(setters[0]);
    }

    return array.toArray(new Object[array.size()]);
  }
}