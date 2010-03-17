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

  
  private static IdentifierSplitter idSpl = new IdentifierSplitter();
  private static PlainTextSplitter textSpl = new PlainTextSplitter();
  private static PropertiesSplitter prSpl = new PropertiesSplitter();
  private static WordSplitter wordSpl = new WordSplitter();
  private static Splitter common = new PlainTextSplitter();
  private static CommentSplitter commentSpl = new CommentSplitter();

  public static IdentifierSplitter getIdentifierSplitter() {
    return idSpl;
  }

   public static WordSplitter getWordSplitter() {
    return wordSpl;
  }

  public static Splitter getAttributeValueSplitter() {
    return common;
  }

  public static Splitter getPlainTextSplitter() {
    return textSpl;
  }

  public static CommentSplitter getCommentSplitter() {
    return commentSpl;
  }

  public static Splitter getStringLiteralSplitter() {
    return common;
  }

  public static Splitter getPropertiesSplitter() {
    return prSpl;
  }
}
