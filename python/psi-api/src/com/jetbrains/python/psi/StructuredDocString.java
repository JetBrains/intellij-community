/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public interface StructuredDocString {
  String getDescription();

  String getSummary();

  @Nullable
  Substring getTagValue(String... tagNames);

  @Nullable
  Substring getTagValue(String tagName, @NotNull String argName);

  @Nullable
  Substring getTagValue(String[] tagNames, @NotNull String argName);

  List<Substring> getTagArguments(String... tagNames);

  List<Substring> getParameterSubstrings();

  @Nullable
  Substring getParamByNameAndKind(@NotNull String name, String kind);

  List<String> getParameters();

  List<String> getKeywordArguments();

  @Nullable
  String getReturnType();

  @Nullable
  String getReturnDescription();

  @Nullable
  String getParamType(@Nullable String paramName);

  @Nullable
  String getParamDescription(@Nullable String paramName);

  @Nullable
  String getKeywordArgumentDescription(@Nullable String paramName);

  List<String> getRaisedExceptions();

  @Nullable
  String getRaisedExceptionDescription(@Nullable String exceptionName);

  @Nullable
  String getAttributeDescription();

  List<String> getAdditionalTags();

  List<Substring> getKeywordArgumentSubstrings();

  @Nullable
  Substring getReturnTypeSubstring();

  @Nullable
  Substring getParamTypeSubstring(@Nullable String paramName);
}
