/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.jetbrains.typoscript.lang.highlighter;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.jetbrains.typoscript.TypoScriptBundle;
import com.jetbrains.typoscript.lang.TypoScriptFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;


public class TypoScriptColorsAndFontsPage implements ColorSettingsPage, DisplayPrioritySortable {

  @NonNls private static final String DEMO_TEXT = "/*\n" +
                                                  " *   Menu description\n" +
                                                  " */\n" +
                                                  "<INCLUDE_TYPOSCRIPT: source=\"FILE: folder/html/typoscript.txt\">\n" +
                                                  "temp.menu = COA\n" +
                                                  "temp.menu { some ignored text\n" +
                                                  "  10 = HMENU\n" +
                                                  "  10.entryLevel = 0\n" +
                                                  "  10.1 = TMENU\n" +
                                                  "  10.1 {\n" +
                                                  "    wrap = <div class=\"menuBar\" style=\"width:80%;\">|</div>\n" +
                                                  "    NO.ATagParams = class=\"menuButton\"\n" +
                                                  "  } \n" +
                                                  "} \n" +
                                                  "\n" +
                                                  "# Default PAGE object:\n" +
                                                  "page = PAGE\n" +
                                                  "[GLOBAL]\n" +
                                                  "page.typeNum = 0\n" +
                                                  "page.10 < temp.menu \n" +
                                                  "&text = my text";


  private static final AttributesDescriptor[] ATTRIBUTES =
    new AttributesDescriptor[]{
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.one.line.comment"), TypoScriptHighlightingData.ONE_LINE_COMMENT),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.multiline.comment"), TypoScriptHighlightingData.MULTILINE_COMMENT),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.ignored.text"), TypoScriptHighlightingData.IGNORED_TEXT),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.operator"), TypoScriptHighlightingData.OPERATOR_SIGN),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.string"), TypoScriptHighlightingData.STRING_VALUE),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.object.path"), TypoScriptHighlightingData.OBJECT_PATH_ENTITY),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.object.path.separator"),
                               TypoScriptHighlightingData.OBJECT_PATH_SEPARATOR),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.assigned.value"), TypoScriptHighlightingData.ASSIGNED_VALUE),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.condition"), TypoScriptHighlightingData.CONDITION),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.include"), TypoScriptHighlightingData.INCLUDE_STATEMENT),
      new AttributesDescriptor(TypoScriptBundle.message("color.settings.bad.character"), TypoScriptHighlightingData.BAD_CHARACTER)
    };

  @NotNull
  public String getDisplayName() {
    return TypoScriptBundle.message("color.settings.name");
  }

  @Nullable
  public Icon getIcon() {
    return TypoScriptFileType.INSTANCE.getIcon();
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRIBUTES;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new TypoScriptSyntaxHighlighter();
  }

  @NonNls
  @NotNull
  public String getDemoText() {
    return DEMO_TEXT;
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.LANGUAGE_SETTINGS;
  }
}
