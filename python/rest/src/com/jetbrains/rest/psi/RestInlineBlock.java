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
package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.rest.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestInlineBlock extends RestElement {
  public RestInlineBlock(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestInlineBlock";
  }
  public boolean validBlock() {
    String text = getText();
    if (text.endsWith("\n")) {
      text = text.substring(0, text.length()-1);
      final int index = text.lastIndexOf("\n");
      if (index > -1 && StringUtil.isEmptyOrSpaces(text.substring(index)))
        return true;
    }
    return false;
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitInlineBlock(this);
  }
}
