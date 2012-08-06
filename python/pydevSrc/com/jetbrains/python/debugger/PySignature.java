package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PySignature {
  private final String myFile;
  private final String myFunctionName;

  private final List<NamedParameter> myArgs = Lists.newArrayList();
  private final Map<String, String> myTypeMap = Maps.newHashMap();

  public PySignature(String file, String name) {
    myFile = file;
    myFunctionName = name;
  }

  public String getArgType(String name) {
    return myTypeMap.get(name);
  }

  public String getFile() {
    return myFile;
  }

  public String getFunctionName() {
    return myFunctionName;
  }

  public List<NamedParameter> getArgs() {
    return myArgs;
  }

  public static class NamedParameter {
    private final String myName;
    private final String myType;

    private NamedParameter(String name, String type) {
      myName = name;
      myType = type;
    }

    public String getName() {
      return myName;
    }

    public String getType() {
      return myType;
    }
  }

  public PySignature addArgumentVar(String name, String type) {
    myArgs.add(new NamedParameter(name, type));
    myTypeMap.put(name, type);
    return this;
  }
}
