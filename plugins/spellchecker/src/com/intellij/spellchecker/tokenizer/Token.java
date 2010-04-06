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
package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiElement;


public class Token<T extends PsiElement> {

  private String text;
  private String description;
  private T element;
  private boolean useRename;
  private int offset;

  public Token(T element, String text, boolean useRename) {
    this.element = element;
    this.text = text;
    this.useRename = useRename;
  }

  public Token(T element, String text, boolean useRename, int offset) {
    this.element = element;
    this.text = text;
    this.useRename = useRename;
    this.offset = offset;
  }

  public Token(T element, String text, String description, boolean useRename) {
    this.element = element;
    this.text = text;
    this.description = description;
    this.useRename = useRename;
  }

  public String getText() {
    return text;
  }

  public String getDescription() {
    return description;
  }

  public T getElement() {
    return element;
  }

  public boolean isUseRename() {
    return useRename;
  }

  public int getOffset() {
    return offset;
  }
}
