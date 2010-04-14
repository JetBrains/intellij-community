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
package com.intellij.spellchecker.inspections;

public class SplitterFactory {

  private static final SplitterFactory ourInstance = new SplitterFactory();

  private final IdentifierSplitter identifierSplitter = new IdentifierSplitter();
  private final PlainTextSplitter plainTextSpl = new PlainTextSplitter();
  private final PropertiesSplitter prSpl = new PropertiesSplitter();
  private final WordSplitter wordSplitter = new WordSplitter();
  private final CommentSplitter comment = new CommentSplitter();
  private final TextSplitter textSpl = new TextSplitter();


  private SplitterFactory() {
  }

  public static SplitterFactory getInstance() {
    return ourInstance;
  }

  public IdentifierSplitter getIdentifierSplitter() {
    return identifierSplitter;
  }

  public WordSplitter getWordSplitter() {
    return wordSplitter;
  }

  public Splitter getAttributeValueSplitter() {
    return textSpl;
  }

  public Splitter getPlainTextSplitter() {
    return plainTextSpl;
  }

  public CommentSplitter getCommentSplitter() {
    return comment;
  }

  public Splitter getStringLiteralSplitter() {
    return plainTextSpl;
  }

  public Splitter getPropertiesSplitter() {
    return prSpl;
  }

  public TextSplitter getTextSplitterNew() {
    return textSpl;
  }
}
