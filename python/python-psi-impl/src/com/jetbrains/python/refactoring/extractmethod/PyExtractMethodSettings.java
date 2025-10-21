package com.jetbrains.python.refactoring.extractmethod;

import com.intellij.refactoring.extractMethod.ExtractMethodSettings;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NotNullByDefault
public class PyExtractMethodSettings implements ExtractMethodSettings<Object> {
  private final String myMethodName;
  private final PyVariableData[] myVariableData;
  private final @Nullable String myReturnTypeName;
  private final Set<PyType> myReturnTypes;
  private final boolean myUseTypeAnnotations;

  public PyExtractMethodSettings(String methodName,
                                 PyVariableData[] variableData,
                                 @Nullable String returnTypeName,
                                 Set<PyType> returnTypes,
                                 boolean useTypeAnnotations) {
    myMethodName = methodName;
    myVariableData = variableData;
    myReturnTypeName = returnTypeName;
    myReturnTypes = returnTypes;
    myUseTypeAnnotations = useTypeAnnotations;
  }

  @Override
  public String getMethodName() {
    return myMethodName;
  }

  @Override
  public PyVariableData[] getAbstractVariableData() {
    return myVariableData;
  }

  public @Nullable String getReturnTypeName() {
    return myReturnTypeName;
  }

  public Set<PyType> getReturnTypeFqns() {
    return myReturnTypes;
  }

  public boolean isUseTypeAnnotations() {
    return myUseTypeAnnotations;
  }

  @Override
  public @Nullable Object getVisibility() {
    return null;
  }

  List<PyType> getAllTypes() {
    List<PyType> result = new ArrayList<>();
    for (PyVariableData variableData : myVariableData) {
      if (variableData.type != null) {
        result.add(variableData.type);
      }
    }
    result.addAll(myReturnTypes);
    return result;
  }
}
