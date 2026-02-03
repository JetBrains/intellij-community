/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import org.intellij.lang.xpath.context.functions.Function;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class FunctionLookup extends AbstractLookup {
  private final String type;
  private final boolean hasParameters;

  FunctionLookup(String name, String _presentation) {
    this(name, _presentation, null, false);
  }

  FunctionLookup(String name, String _presentation, String type, boolean hasParams) {
    super(name, _presentation);
    this.type = type;
    this.hasParameters = hasParams;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTypeText(type);
    presentation.setIcon(AllIcons.Nodes.Function);
    presentation.setItemTextBold(type == null);
  }

  boolean hasParameters() {
    return hasParameters;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final FunctionLookup that = (FunctionLookup)o;

    if (!Objects.equals(myPresentation, that.myPresentation)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (type != null ? type.hashCode() : 0);
    return result;
  }

  public static LookupElement newFunctionLookup(String name, Function functionDecl) {
    final String presentation = functionDecl.buildSignature();
    final String returnType = functionDecl.getReturnType().getName();
    final boolean hasParams = functionDecl.getParameters().length > 0;
    return new FunctionLookup(name, presentation, returnType, hasParams);
  }
}
