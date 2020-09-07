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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public interface XmlText extends XmlTagChild {
  @Override
  String getText();
  /**
   * Substituted text
   */
  @NlsSafe String getValue();
  void setValue(@NlsSafe String s) throws IncorrectOperationException;

  XmlElement insertAtOffset(XmlElement element, int displayOffset) throws IncorrectOperationException;

  void insertText(@NlsSafe String text, int displayOffset) throws IncorrectOperationException;
  void removeText(int displayStart, int displayEnd) throws IncorrectOperationException;

  int physicalToDisplay(int offset);
  int displayToPhysical(int offset);

  @Nullable
  XmlText split(int displayIndex);
  XmlText[] EMPTY_ARRAY = new XmlText[0];
}
