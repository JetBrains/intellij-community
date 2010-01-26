/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class SimpleTokenizer<T extends PsiElement> extends Tokenizer<T> {
  private final boolean myUseRename;
  private final String mySeparator;

  public SimpleTokenizer(boolean useRename, String separator) {
    myUseRename = useRename;
    mySeparator = separator;
  }
  
  public SimpleTokenizer(boolean useRename) {
    this(useRename, null);
  }
  
  public SimpleTokenizer(String separator) {
    this(false, separator);
  }
  
  public SimpleTokenizer() {
    this(false, null);
  }

  @Override
  public Token[] tokenize(@NotNull T e) {
    final String text = getText(e);
    if (text == null) return null;
    if (mySeparator != null) {
      final List<Token> tokens = new ArrayList<Token>();
      int offset = text.startsWith(mySeparator) ? mySeparator.length() : 0;
      int next;
      while ((next = text.indexOf(mySeparator, offset)) != -1) {
        final String s = text.substring(offset, next);
        tokens.add(new Token<T>(e, s, myUseRename, offset));
        offset += s.length() + mySeparator.length();
      }
      if (offset < text.length()) {
        tokens.add(new Token<T>(e, text.substring(offset), myUseRename, offset));
      }
      return tokens.toArray(new Token[tokens.size()]);
    } else {
      return new Token[]{new Token<T>(e, text, myUseRename)};
    }
  }

  @Nullable
  public String getText(T element) {
    return element.getText();
  }
}
