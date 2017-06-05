/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.trace.impl.handler.type;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Vitaliy.Bibaev
 */
public class GenericTypeImpl implements GenericType {
  private final String myPrimitiveName;

  @Override
  public int hashCode() {
    return Objects.hash(myPrimitiveName, myGenericName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof GenericType) {
      final GenericType type = (GenericType)obj;
      return myPrimitiveName.equals(type.getVariableTypeName()) && myGenericName.equals(type.getGenericTypeName());
    }

    return false;
  }

  private final String myGenericName;

  GenericTypeImpl(@NotNull String primitiveName, @NotNull String genericName) {
    myPrimitiveName = primitiveName;
    myGenericName = genericName;
  }

  @NotNull
  @Override
  public String getVariableTypeName() {
    return myPrimitiveName;
  }

  @NotNull
  @Override
  public String getGenericTypeName() {
    return myGenericName;
  }
}
