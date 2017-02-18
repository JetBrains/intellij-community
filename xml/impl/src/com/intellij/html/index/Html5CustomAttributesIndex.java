/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.html.index;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlHighlightingLexer;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class Html5CustomAttributesIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> INDEX_ID = ID.create("html5.custom.attributes.index");

  private final DataIndexer<String, Void, FileContent> myIndexer = new DataIndexer<String, Void, FileContent>() {
    @Override
    @NotNull
    public Map<String, Void> map(@NotNull FileContent inputData) {
      CharSequence input = inputData.getContentAsText();
      Language language = ((LanguageFileType)inputData.getFileType()).getLanguage();
      if (language == HTMLLanguage.INSTANCE || language == XHTMLLanguage.INSTANCE) {
        final Lexer lexer = (language == HTMLLanguage.INSTANCE ? new HtmlHighlightingLexer(FileTypeManager.getInstance().getStdFileType("CSS")) : new XHtmlHighlightingLexer());
        lexer.start(input);
        Map<String, Void> result = new HashMap<>();
        IElementType tokenType = lexer.getTokenType();
        while (tokenType != null) {
          if (tokenType == XmlTokenType.XML_NAME) {
            String xmlName = input.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
            if (HtmlUtil.isCustomHtml5Attribute(xmlName)) {
              result.put(xmlName, null);
            }
          }
          else if (tokenType == XmlTokenType.XML_DOCTYPE_PUBLIC || tokenType == XmlTokenType.XML_DOCTYPE_SYSTEM) {
            // this is not an HTML5 context
            break;
          }
          lexer.advance();
          tokenType = lexer.getTokenType();
        }
        return result;
      }
      return Collections.emptyMap();
    }
  };

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.HTML, StdFileTypes.XHTML) {
      @Override
      public boolean acceptInput(@NotNull final VirtualFile file) {
        return file.isInLocalFileSystem();
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 1;
  }
}
