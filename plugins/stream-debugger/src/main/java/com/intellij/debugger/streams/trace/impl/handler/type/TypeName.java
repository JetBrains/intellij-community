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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
final public class TypeName {
  private TypeName() {}

  final public static class Primitive {
    private Primitive() {}

    public static final String INT = "int";
    public static final String LONG = "long";
    public static final String DOUBLE = "double";
  }

  public static final String LONG = "java.lang.Long";
  public static final String INTEGER = "java.lang.Integer";
  public static final String DOUBLE = "java.lang.Double";

  public static final String OBJECT = "java.lang.Object";

  @NotNull
  @Contract(pure = true)
  public static String map(@NotNull String fromType, @NotNull String toType) {
    return genericMapType("java.util.Map", fromType, toType);
  }

  @NotNull
  @Contract(pure = true)
  public static String hashMap(@NotNull String fromType, @NotNull String toType) {
    return genericMapType("java.util.HashMap", fromType, toType);
  }

  @NotNull
  @Contract(pure = true)
  public static String linkedHashMap(@NotNull String fromType, @NotNull String toType) {
    return genericMapType("java.util.LinkedHashMap", fromType, toType);
  }

  @NotNull
  @Contract(pure = true)
  public static String arrayType(@NotNull String type) {
    return type + "[]";
  }

  @NotNull
  @Contract(pure = true)
  private static String genericMapType(@NotNull String mapName, @NotNull String fromType, @NotNull String toType) {
    return String.format("%s<%s, %s>", mapName, fromType, toType);
  }
}
