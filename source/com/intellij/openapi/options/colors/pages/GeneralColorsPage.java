/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.options.colors.pages;

import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileHighlighter;
import com.intellij.openapi.fileTypes.PlainFileHighlighter;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;

import javax.swing.*;
import java.util.Map;

public class GeneralColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATT_DESCRIPTORS = new AttributesDescriptor[] {
    new AttributesDescriptor("Default text", HighlighterColors.TEXT),

    new AttributesDescriptor("Folded text", EditorColors.FOLDED_TEXT_ATTRIBUTES),
    new AttributesDescriptor("Search result", EditorColors.SEARCH_RESULT_ATTRIBUTES),
    new AttributesDescriptor("Search result (write access)", EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES),
    new AttributesDescriptor("Text search result", EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES),

    new AttributesDescriptor("Template variable", TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES)
  };

  private static final ColorDescriptor[] COLOR_DESCRIPTORS = new ColorDescriptor[] {
    new ColorDescriptor("Background", EditorColors.BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor("Background in readonly files", EditorColors.READONLY_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor("Readonly fragment background", EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),


    new ColorDescriptor("Selection Background", EditorColors.SELECTION_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor("Selection Foreground", EditorColors.SELECTION_FOREGROUND_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor("Caret", EditorColors.CARET_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor("Caret row", EditorColors.CARET_ROW_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor("Right margin", EditorColors.RIGHT_MARGIN_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor("Whitespaces", EditorColors.WHITESPACES_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor("Line number", EditorColors.LINE_NUMBERS_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor("CVS annotations", EditorColors.ANNOTATIONS_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor("Folding outline", EditorColors.FOLDING_TREE_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor("Selected folding outline", EditorColors.SELECTED_FOLDING_TREE_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor("Added lines", EditorColors.ADDED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor("Modified lines", EditorColors.MODIFIED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND),
  };

  public String getDisplayName() {
    return "General";
  }

  public Icon getIcon() {
    return StdFileTypes.PLAIN_TEXT.getIcon();
  }

  public AttributesDescriptor[] getAttributeDescriptors() {
    return new AttributesDescriptor[0];
  }

  public ColorDescriptor[] getColorDescriptors() {
    return COLOR_DESCRIPTORS;
  }

  public FileHighlighter getHighlighter() {
    return new PlainFileHighlighter();
  }

  public String getDemoText() {
    String text =
      "IntelliJ IDEA is a full-featured Java IDE\n" +
      "with a high level of usability and outstanding\n" +
      "advanced code editing and refactoring support.\n";

    return text;
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}