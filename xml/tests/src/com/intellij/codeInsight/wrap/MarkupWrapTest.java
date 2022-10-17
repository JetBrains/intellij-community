// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.wrap;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;

/**
 * @author Denis Zhdanov
 */
public class MarkupWrapTest extends AbstractWrapTest {


  public void testWrapInAttributeValue() {
    FileType[] MARKUP_FILE_TYPES = {XmlFileType.INSTANCE, HtmlFileType.INSTANCE, XHtmlFileType.INSTANCE};
    mySettings.setDefaultRightMargin(60);
    for (FileType fileType : MARKUP_FILE_TYPES) {
      checkWrapOnTyping(
        fileType,
        "12345",
        "<my-tag-with-long-name attr='this.is.my.attribute.that.is.already.<caret>rather.long'>\n" +
        "</my-tag-with-long-name>",
        """
          <my-tag-with-long-name\s
                  attr='this.is.my.attribute.that.is.already.12345<caret>rather.long'>
          </my-tag-with-long-name>"""
      );
    }
  }
}
