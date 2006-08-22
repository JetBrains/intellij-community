/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 01.07.2002
 * Time: 15:48:33
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.psi.PsiField;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public final class Settings {
  private final boolean myMakeClassParameter;
  private final String myClassParameterName;
  private final boolean myMakeFieldParameters;
  private final HashMap<PsiField,String> myFieldToNameMapping;
  private final ArrayList<FieldParameter> myFieldToNameList;
  private final boolean myReplaceUsages;


  public static final class FieldParameter {
    public FieldParameter(PsiField field, String name) {
      this.field = field;
      this.name = name;
    }

    public final PsiField field;
    public final String name;
  }


  public Settings(boolean replaceUsages, String classParameterName,
                  ParameterTablePanel.VariableData[] variableDatum) {
    myReplaceUsages = replaceUsages;
    myMakeClassParameter = classParameterName != null;
    myClassParameterName = classParameterName;
    myMakeFieldParameters = variableDatum != null;
    myFieldToNameList = new ArrayList<FieldParameter>();
    if(myMakeFieldParameters) {
      myFieldToNameMapping = new com.intellij.util.containers.HashMap<PsiField, String>();
      for (ParameterTablePanel.VariableData data : variableDatum) {
        if (data.passAsParameter) {
          myFieldToNameMapping.put((PsiField)data.variable, data.name);
          myFieldToNameList.add(new FieldParameter((PsiField)data.variable, data.name));
        }
      }
    }
    else {
      myFieldToNameMapping = null;
    }
  }

  public Settings(boolean replaceUsages, String classParameterName, 
                  PsiField[] fields, String[] names) {
    myReplaceUsages = replaceUsages;
    myMakeClassParameter = classParameterName != null;
    myClassParameterName = classParameterName;
    myMakeFieldParameters = fields.length > 0;
    myFieldToNameList = new ArrayList<FieldParameter>();
    if (myMakeFieldParameters) {
      myFieldToNameMapping = new HashMap<PsiField, String>();
      for (int i = 0; i < fields.length; i++) {
        final PsiField field = fields[i];
        final String name = names[i];
        myFieldToNameMapping.put(field, name);
        myFieldToNameList.add(new FieldParameter(field, name));
      }
    }
    else {
      myFieldToNameMapping = null;
    }
  }
  
  public boolean isReplaceUsages() {
    return myReplaceUsages;
  }

  public boolean isMakeClassParameter() {
    return myMakeClassParameter;
  }

  public String getClassParameterName() {
    return myClassParameterName;
  }

  public boolean isMakeFieldParameters() {
    return myMakeFieldParameters;
  }

  @Nullable
  public String getNameForField(PsiField field) {
    if (myFieldToNameMapping != null) {
      return myFieldToNameMapping.get(field);
    }
    return null;
  }

  public List<FieldParameter> getParameterOrderList() {
    return myFieldToNameList;
  }

  public boolean isChangeSignature() {
    return isMakeClassParameter() || isMakeFieldParameters();
  }

  public int getNewParametersNumber() {
    final int result = isMakeFieldParameters() ? myFieldToNameList.size() : 0;
    return result + (isMakeClassParameter() ? 1 : 0);
  }
}
