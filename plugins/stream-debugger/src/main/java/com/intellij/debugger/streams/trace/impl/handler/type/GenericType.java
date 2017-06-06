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

import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface GenericType {
  @NotNull
  String getVariableTypeName();

  @NotNull
  String getGenericTypeName();

  @NotNull
  String getDefaultValue();

  GenericType BOOLEAN = new GenericTypeImpl("boolean", "java.lang.Boolean", "false");
  GenericType INT = new GenericTypeImpl("int", "java.lang.Integer", "0");
  GenericType DOUBLE = new GenericTypeImpl("double", "java.lang.Double", "0.");
  GenericType LONG = new GenericTypeImpl("long", "java.lang.Long", "0L");
  GenericType OBJECT = new ClassTypeImpl("java.lang.Object");
  GenericType VOID = new GenericTypeImpl("void", "java.lang.Void", "null");

  GenericType OPTIONAL = new ClassTypeImpl(CommonClassNames.JAVA_UTIL_OPTIONAL);
  GenericType OPTIONAL_INT = new ClassTypeImpl("java.util.OptionalInt");
  GenericType OPTIONAL_LONG = new ClassTypeImpl("java.util.OptionalLong");
  GenericType OPTIONAL_DOUBLE = new ClassTypeImpl("java.util.OptionalDouble");
}
