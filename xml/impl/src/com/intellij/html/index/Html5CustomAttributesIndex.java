// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.index;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lexer.HtmlLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlLexer;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Html5CustomAttributesIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> INDEX_ID = ID.create("html5.custom.attributes.index");

  private final DataIndexer<String, Void, FileContent> myIndexer = new DataIndexer<>() {
    @Override
    public @NotNull Map<String, Void> map(@NotNull FileContent inputData) {
      CharSequence input = inputData.getContentAsText();
      Language language = ((LanguageFileType)inputData.getFileType()).getLanguage();
      if (language == HTMLLanguage.INSTANCE || language == XHTMLLanguage.INSTANCE) {
        final Lexer lexer = (language == HTMLLanguage.INSTANCE
                             ? new HtmlLexer(true)
                             : new XHtmlLexer(true));
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

  @Override
  public @NotNull ID<String, Void> getName() {
    return INDEX_ID;
  }

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(HtmlFileType.INSTANCE, XHtmlFileType.INSTANCE) {
      @Override
      public boolean acceptInput(final @NotNull VirtualFile file) {
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
