/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyReturnSignature {
  private final String myFile;
  private final String myFunctionName;

  private List<String> myReturnTypes;

  public PyReturnSignature(@NotNull String file, @NotNull String name, @NotNull List<String> returnTypes) {
    myFile = file;
    myFunctionName = name;
    myReturnTypes = returnTypes;
  }

  public PyReturnSignature(@NotNull String file, @NotNull String name, @NotNull String type) {
    this(file, name, parseTypes(type));
  }

  public PyReturnSignature(@NotNull String file, @NotNull String name) {
    myFile = file;
    myFunctionName = name;
    myReturnTypes = Lists.newArrayList();
  }

  @NotNull
  private static List<String> parseTypes(@NotNull String type) {
    String[] parts = type.split(" or ");
    return Lists.newArrayList(parts);
  }

  public String getFile() {
    return myFile;
  }

  public String getFunctionName() {
    return myFunctionName;
  }

  public List<String> getReturnTypes() {
    return myReturnTypes;
  }

  public String getReturnTypeQualifiedName() {
    if (myReturnTypes.size() == 1) {
      return myReturnTypes.get(0);
    }
    else {
      return StringUtil.join(myReturnTypes, " or ");
    }
  }

  public PyReturnSignature addType(String type) {
    if (!myReturnTypes.contains(type)) {
      myReturnTypes.add(type);
    }
    return this;
  }

  public PyReturnSignature addAllTypes(PyReturnSignature returnSignature) {
    for (String returnType: returnSignature.getReturnTypes()) {
      addType(returnType);
    }
    return this;
  }
}