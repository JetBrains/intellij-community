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
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract part of a multipart statement.
 */
@ApiStatus.Experimental
public interface PyAstStatementPart extends PyAstStatementListContainer {
  PyAstStatementPart[] EMPTY_ARRAY = new PyAstStatementPart[0];

  @Override
  @NotNull
  default PyAstStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }
}
