/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.xml;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public interface XmlTagValue {

  XmlTagChild @NotNull [] getChildren();

  XmlText @NotNull [] getTextElements();

  /**
   * @return raw tag value text
   * @see #getTrimmedText()
   */
  @NotNull @NlsSafe String getText();
  
  @NotNull
  TextRange getTextRange();

  /**
   * @return concatenated child XmlTexts values.
   * @see XmlText#getValue()
   */
  @NotNull @NlsSafe String getTrimmedText();

  void setText(@NlsSafe String value);

  void setEscapedText(@NlsSafe String value);

  boolean hasCDATA();
}
