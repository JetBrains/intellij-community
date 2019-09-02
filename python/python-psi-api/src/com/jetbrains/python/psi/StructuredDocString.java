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
package com.jetbrains.python.psi;

import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public interface StructuredDocString {

  String getSummary();
  @NotNull
  String getDescription(); // for formatter

  @NotNull
  List<String> getParameters();

  /**
   * @return all names of parameters mentioned in the docstring as substrings.
   */
  @NotNull
  List<Substring> getParameterSubstrings();

  /**
   * @param paramName {@code null} can be used for unnamed parameters descriptors, e.g. in docstring following class attribute
   * @return {@code null} if specified parameter was omitted in the docstring completely, empty string if there was place for its type, 
   * but it was unfilled and trimmed type text otherwise.
   */
  @Nullable
  String getParamType(@Nullable String paramName);

  /**
   * @param paramName {@code null} can be used for unnamed parameters descriptors, e.g. in docstring following class attribute
   * @return {@code null} if specified parameter was omitted in the docstring completely, empty substring if there was place for its type, 
   * but it was unfilled and trimmed type substring otherwise.
   */
  @Nullable
  Substring getParamTypeSubstring(@Nullable String paramName);

  /**
   * @param paramName {@code null} can be used for unnamed parameters descriptors, e.g. in docstring following class attribute
   */
  @Nullable
  String getParamDescription(@Nullable String paramName);
  /**
   * Keyword arguments are those arguments that usually don't exist in function signature, 
   * but are passed e.g. via {@code **kwargs} mechanism. 
   */
  @NotNull
  List<String> getKeywordArguments();
  @NotNull
  List<Substring> getKeywordArgumentSubstrings();

  // getKeywordArgumentType(name)
  // getKeywordArgumentTypeString(name)  
  @Nullable
  String getKeywordArgumentDescription(@Nullable String paramName);

  /**
   * @return {@code null} if return type was omitted in the docstring completely, empty string if there was place for its type,
   * but it was unfilled and trimmed type text otherwise.
   */
  @Nullable
  String getReturnType();

  /**
   * @return {@code null} if return type was omitted in the docstring completely, empty substring if there was place for its type,
   * but it was unfilled and trimmed type substring otherwise.
   */  @Nullable
  Substring getReturnTypeSubstring();

  @Nullable
  String getReturnDescription(); // for formatter
  @NotNull
  List<String> getRaisedExceptions(); // for formatter

  @Nullable
  String getRaisedExceptionDescription(@Nullable String exceptionName); // for formatter

  // getAttributes
  // getAttributeSubstrings
  // getAttributeType(name)
  // getAttributeTypeSubstring(name)
  @Nullable
  String getAttributeDescription(); // for formatter

  // Tags related methods
}
