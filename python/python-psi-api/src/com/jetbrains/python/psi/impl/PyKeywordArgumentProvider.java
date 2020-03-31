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
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Allows to provide a custom list of keyword arguments for a function that uses **kwargs.
 *
 * @author yole
 */
public interface PyKeywordArgumentProvider {
  ExtensionPointName<PyKeywordArgumentProvider> EP_NAME = ExtensionPointName.create("Pythonid.keywordArgumentProvider");

  @NotNull
  List<String> getKeywordArguments(PyFunction function, PyCallExpression callExpr);
}
