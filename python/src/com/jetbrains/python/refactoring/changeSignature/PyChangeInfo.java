package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */

public class PyChangeInfo implements ChangeInfo {

  private final PyFunction myFunction;
  private PyParameterInfo[] myNewParameterInfo;
  private final String myNewName;

  private boolean myIsParameterSetOrOrderChanged;
  private boolean myIsParametersNameChanged;
  private final boolean myIsNameChanged;


  public PyChangeInfo(PyFunction function,
                      PyParameterInfo[] newParameterInfo,
                      String newName) {

    myFunction = function;
    myNewParameterInfo = newParameterInfo;
    myNewName = newName;

    myIsNameChanged = !newName.equals(function.getName());

    final PyParameter[] oldParameters = function.getParameterList().getParameters();

    if (oldParameters.length != newParameterInfo.length) {
      myIsParameterSetOrOrderChanged = true;
      myIsParametersNameChanged = true;
    }
    else {
      myIsParameterSetOrOrderChanged = false;
      for (int i = 0; i < newParameterInfo.length; i++) {
        PyParameterInfo parameterInfo = newParameterInfo[i];

        if (i != parameterInfo.getOldIndex()) {
          myIsParameterSetOrOrderChanged = true;
        }
        if (!parameterInfo.getName().equals(oldParameters[i].getName())) {
          myIsParameterSetOrOrderChanged = true;
          myIsParametersNameChanged = true;
        }
        final String defaultValue = parameterInfo.getDefaultValue();
        final PyExpression oldDefaultValue = oldParameters[i].getDefaultValue();
        if ((oldDefaultValue == null && defaultValue != null) || (defaultValue == null && oldDefaultValue != null)) {
          myIsParameterSetOrOrderChanged = true;
        }
        if (oldDefaultValue != null && !oldDefaultValue.getText().equals(defaultValue)) {
          myIsParameterSetOrOrderChanged = true;
        }
        if (parameterInfo.getDefaultInSignature() != oldParameters[i].hasDefaultValue()) {
          myIsParameterSetOrOrderChanged = true;
        }
      }
    }
  }

  @Override
  public PyFunction getMethod() {
    return myFunction;
  }
  @NotNull
  @Override
  public PyParameterInfo[] getNewParameters() {
    return myNewParameterInfo;
  }

  @Override
  public String getNewName() {
    return myNewName;
  }

  @Override
  public boolean isParameterSetOrOrderChanged() {
    return myIsParameterSetOrOrderChanged;
  }

  @Override
  public boolean isParameterTypesChanged() {
    return false;
  }

  @Override
  public boolean isParameterNamesChanged() {
    return myIsParametersNameChanged;
  }

  @Override
  public boolean isNameChanged() {
    return myIsNameChanged;
  }

  @Override
  public boolean isGenerateDelegate() {
    return false;
  }

  @Override
  public boolean isReturnTypeChanged() {
    return false;
  }

  @Override
  public Language getLanguage() {
    return PythonFileType.INSTANCE.getLanguage();
  }
}
