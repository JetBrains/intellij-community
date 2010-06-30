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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.CheckArea;
import com.intellij.spellchecker.inspections.Splitter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class Token<T extends PsiElement> {

  private final String text;
  private final String description;
  private final boolean useRename;
  private int offset;


  private final T element;
  private final Splitter splitter;

  private TextRange range;

  public Token(T element, String text, boolean useRename, Splitter splitter) {
    this.element = element;
    this.text = text;
    this.useRename = useRename;
    this.splitter = splitter;
    this.offset = 0;
    this.description = null;
  }

  public Token(T element, String text, boolean useRename, int offset, Splitter splitter) {
    this(element, text, useRename, splitter);
    this.offset = offset;
  }

  public Token(T element, String text, boolean useRename, int offset, TextRange textRange, Splitter splitter) {
    this(element, text, useRename, offset, splitter);
    this.range = textRange;
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

  @NotNull
  public TextRange getRange() {
    if (range==null){
      range = new TextRange(0,(text!=null?text.length():0));
    }
    return range;
  }

  @Nullable
  public List<CheckArea> getAreas() {
    if (splitter == null || text == null) {
      return null;
    }
    return splitter.split(text, getRange());
  }


}
