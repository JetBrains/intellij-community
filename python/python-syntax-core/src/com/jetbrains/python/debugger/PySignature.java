// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PySignature {
  private static final String UNION_PREFIX = "Union[";
  private final String myFile;
  private final String myFunctionName;

  private NamedParameter myReturnType = null;

  private final List<NamedParameter> myArgs = new ArrayList<>();

  public PySignature(@NotNull String file, @NotNull String name) {
    myFile = file;
    myFunctionName = name;
  }

  public @Nullable String getArgTypeQualifiedName(@NotNull String name) {
    for (NamedParameter param : myArgs) {
      if (name.equals(param.getName())) {
        return param.getTypeQualifiedName();
      }
    }
    return null;
  }

  public @NotNull String getFile() {
    return myFile;
  }

  public @NotNull String getFunctionName() {
    return myFunctionName;
  }

  public @NotNull List<NamedParameter> getArgs() {
    return myArgs;
  }

  public NamedParameter getReturnType() {
    return myReturnType;
  }

  public PySignature addReturnType(@Nullable String returnType) {
    if (StringUtil.isNotEmpty(returnType)) {
      if (myReturnType != null) {
        myReturnType.addType(returnType);
      }
      else {
        myReturnType = new NamedParameter("", returnType);
      }
    }
    return this;
  }

  public @NotNull PySignature addAllArgs(@NotNull PySignature signature) {
    for (NamedParameter param : signature.getArgs()) {
      NamedParameter ourParam = getArgForName(param.getName());
      if (ourParam != null) {
        ourParam.addTypes(param.getTypesList());
      }
      else {
        addArgument(param);
      }
    }
    return this;
  }

  private @Nullable NamedParameter getArgForName(String name) {
    for (NamedParameter param : myArgs) {
      if (param.getName().equals(name)) {
        return param;
      }
    }

    return null;
  }

  public @Nullable String getReturnTypeQualifiedName() {
    return myReturnType != null ? myReturnType.getTypeQualifiedName() : null;
  }


  public static final class NamedParameter {
    private final String myName;
    private final List<String> myTypes;

    private NamedParameter(@NotNull String name, @NotNull String type) {
      myName = name;

      myTypes = parseTypes(type);
    }

    private static @NotNull List<String> parseTypes(@NotNull String type) {
      if (type.startsWith(UNION_PREFIX) && type.endsWith("]")) {
        return new ArrayList<>(Arrays.asList(type.substring(UNION_PREFIX.length(), type.length() - 1).split("\\s*,\\s*")));
      }
      else {
        String[] parts = type.split(" or ");
        return new ArrayList<>(Arrays.asList(parts));
      }
    }

    public String getName() {
      return myName;
    }

    public String getTypeQualifiedName() {
      if (myTypes.size() == 1) {
        return noneTypeToNone(myTypes.get(0));
      }
      else {
        return UNION_PREFIX + StringUtil.join(myTypes, NamedParameter::noneTypeToNone, ", ") + "]";
      }
    }

    private static @Nullable String noneTypeToNone(@Nullable String type) {
      return "NoneType".equals(type) ? "None" : type;
    }

    public void addType(String type) {
      if (!myTypes.contains(type)) {
        myTypes.add(type);
      }
    }

    public void addTypes(List<String> newTypes) {
      for (String type : newTypes) {
        addType(type);
      }
    }

    public List<String> getTypesList() {
      return myTypes;
    }
  }


  public PySignature addArgument(String name, String type) {
    return addArgument(new NamedParameter(name, type));
  }

  public PySignature addArgument(NamedParameter argument) {
    myArgs.add(argument);
    return this;
  }
}
