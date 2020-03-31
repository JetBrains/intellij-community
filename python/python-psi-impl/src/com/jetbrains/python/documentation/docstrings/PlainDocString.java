/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Stub docstring that is capable only of extracting summary and description
 * @author Mikhail Golubev
 */
public class PlainDocString extends DocStringLineParser implements StructuredDocString {
  private final String mySummary;
  private final String myDescription;

  public PlainDocString(@NotNull Substring content) {
    super(content);
    final int firstNonEmpty = consumeEmptyLines(0);
    if (firstNonEmpty < getLineCount() && isEmptyOrDoesNotExist(firstNonEmpty + 1)) {
      mySummary = getLine(firstNonEmpty).trim().toString();
      final int next = consumeEmptyLines(firstNonEmpty + 1);
      if (next < getLineCount()) {
        final String remaining = getLine(next).union(getLine(getLineCount() - 1)).toString();
        myDescription = PyIndentUtil.removeCommonIndent(remaining, false);
      }
      else {
        myDescription = "";
      }
    }
    else {
      mySummary = "";
      myDescription = PyIndentUtil.removeCommonIndent(myDocStringContent.toString(), true);
    }
  }

  @Override
  public String getSummary() {
    return mySummary;
  }

  @NotNull
  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  protected boolean isBlockEnd(int lineNum) {
    return false;
  }

  @NotNull
  @Override
  public List<String> getParameters() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Substring> getParameterSubstrings() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public String getParamType(@Nullable String paramName) {
    return null;
  }

  @Nullable
  @Override
  public Substring getParamTypeSubstring(@Nullable String paramName) {
    return null;
  }

  @Nullable
  @Override
  public String getParamDescription(@Nullable String paramName) {
    return null;
  }

  @NotNull
  @Override
  public List<String> getKeywordArguments() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Substring> getKeywordArgumentSubstrings() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public String getKeywordArgumentDescription(@Nullable String paramName) {
    return null;
  }

  @Nullable
  @Override
  public String getReturnType() {
    return null;
  }

  @Nullable
  @Override
  public Substring getReturnTypeSubstring() {
    return null;
  }

  @Nullable
  @Override
  public String getReturnDescription() {
    return null;
  }

  @NotNull
  @Override
  public List<String> getRaisedExceptions() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public String getRaisedExceptionDescription(@Nullable String exceptionName) {
    return null;
  }

  @Nullable
  @Override
  public String getAttributeDescription() {
    return null;
  }
}
