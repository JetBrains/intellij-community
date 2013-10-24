package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class PySignature {
  private final String myFile;
  private final String myFunctionName;

  private final List<NamedParameter> myArgs = Lists.newArrayList();

  public PySignature(@NotNull String file, @NotNull String name) {
    myFile = file;
    myFunctionName = name;
  }

  @Nullable
  public String getArgTypeQualifiedName(@NotNull String name) {
    for (NamedParameter param : myArgs) {
      if (name.equals(param.getName())) {
        return param.getTypeQualifiedName();
      }
    }
    return null;
  }

  @NotNull
  public String getFile() {
    return myFile;
  }

  @NotNull
  public String getFunctionName() {
    return myFunctionName;
  }

  @NotNull
  public List<NamedParameter> getArgs() {
    return myArgs;
  }

  @NotNull
  public PySignature addAllArgs(@NotNull PySignature signature) {
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

  @Nullable
  private NamedParameter getArgForName(String name) {
    for (NamedParameter param : myArgs) {
      if (param.getName().equals(name)) {
        return param;
      }
    }

    return null;
  }


  public static class NamedParameter {
    private final String myName;
    private final List<String> myTypes;

    private NamedParameter(@NotNull String name, @NotNull String type) {
      myName = name;

      myTypes = parseTypes(type);
    }

    @NotNull
    private static List<String> parseTypes(@NotNull String type) {
      String[] parts = type.split(" or ");
      return Lists.newArrayList(parts);
    }

    public String getName() {
      return myName;
    }

    public String getTypeQualifiedName() {
      if (myTypes.size() == 1) {
        return myTypes.get(0);
      }
      else {
        return StringUtil.join(myTypes, " or ");
      }
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
