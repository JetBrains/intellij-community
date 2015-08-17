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

import com.jetbrains.python.documentation.TagBasedDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class TagBasedDocStringUpdater<T extends TagBasedDocString> extends DocStringUpdater<T>{

  private final String myTagPrefix;

  public TagBasedDocStringUpdater(@NotNull T docString, @NotNull String prefix) {
    super(docString);
    myTagPrefix = prefix;
  }

  @Override
  protected void scheduleUpdates() {
    final int anchorLine = firstLineWithTag();
    final int anchorLineIndent = myOriginalDocString.getLineIndent(anchorLine);
    for (AddParameter paramReq : myAddParameterRequests) {
      if (!myOriginalDocString.getParameters().contains(paramReq.name)) {
        insertAfterLine(anchorLine, createParameterNameLine(paramReq.name, anchorLineIndent));
      }
      if (paramReq.type != null) {
        final Substring typeSub = myOriginalDocString.getParamTypeSubstring(paramReq.name);
        if (typeSub != null) {
          replace(typeSub.getTextRange(), paramReq.type);
        }
        else {
          insertAfterLine(anchorLine, createParameterTypeLine(paramReq.name, paramReq.type, anchorLineIndent));
        }
      }
    }
    for (AddReturnType returnReq : myAddReturnTypeRequests) {
      final Substring typeSub = myOriginalDocString.getReturnTypeSubstring();
      if (typeSub != null) {
        replace(typeSub.getTextRange(), returnReq.type);
      }
      else {
        insertAfterLine(anchorLine, createReturnTypeLine(returnReq.type, anchorLineIndent));
      }
    }
  }

  @NotNull
  private String createParameterNameLine(@NotNull String name, int indent) {
    return createDocStringBuilder().addParameter(name, null).buildContent(indent, true);
  }

  @NotNull
  private String createParameterTypeLine(@NotNull String name, @NotNull String type, int indent) {
    return createDocStringBuilder().addParameterType(name, type).buildContent(indent, true);
  }

  @NotNull
  private String createReturnTypeLine(@NotNull String type, int indent) {
    return createDocStringBuilder().addReturnValue(null, type).buildContent(indent, true);
  }

  private int firstLineWithTag() {
    for (int i = 0; i < myOriginalDocString.getLineCount(); i++) {
      final Substring line = myOriginalDocString.getLine(i);
      if (line.contains(myTagPrefix)) {
        return i;
      }
    }
    return myOriginalDocString.getLineCount() - 1;
  }

  public abstract DocStringBuilder createDocStringBuilder();
}
