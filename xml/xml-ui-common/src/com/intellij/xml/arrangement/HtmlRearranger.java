// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.arrangement;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.arrangement.*;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.xml.util.BasicHtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HtmlRearranger extends XmlRearranger {
  private static final StdArrangementSettings DEFAULT_SETTINGS;

  static {
    List<StdArrangementMatchRule> DEFAULT_MATCH_RULES = new ArrayList<>();
    StdArrangementMatchRule rule = new StdArrangementMatchRule(
      new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(StdArrangementTokens.EntryType.XML_ATTRIBUTE)),
      StdArrangementTokens.Order.BY_NAME);
    DEFAULT_MATCH_RULES.add(rule);

    DEFAULT_SETTINGS = StdArrangementSettings.createByMatchRules(Collections.emptyList(), DEFAULT_MATCH_RULES);
  }

  private static final DefaultArrangementSettingsSerializer SETTINGS_SERIALIZER =
    new DefaultArrangementSettingsSerializer(DEFAULT_SETTINGS);

  @Override
  public @Nullable StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @Override
  public @NotNull ArrangementSettingsSerializer getSerializer() {
    return SETTINGS_SERIALIZER;
  }

  @Override
  public @NotNull List<XmlElementArrangementEntry> parse(@NotNull PsiElement root,
                                                         @Nullable Document document,
                                                         @NotNull Collection<? extends TextRange> ranges,
                                                         @NotNull ArrangementSettings settings) {
    final XmlArrangementParseInfo parseInfo = new XmlArrangementParseInfo();
    root.accept(new XmlArrangementVisitor(parseInfo, ranges) {
      @Override
      protected void postProcessTag(@NotNull XmlTag xmlTag, @NotNull XmlElementArrangementEntry xmlTagEntry) {
        addEntriesForEmbeddedContent(xmlTag, xmlTagEntry, document, ranges);
      }
    });
    return parseInfo.getEntries();
  }

  private static void addEntriesForEmbeddedContent(@NotNull XmlTag xmlTag,
                                                   @NotNull XmlElementArrangementEntry xmlTagEntry,
                                                   @Nullable Document document,
                                                   @NotNull Collection<? extends TextRange> ranges) {
    if (!StringUtil.equals(xmlTag.getName(), BasicHtmlUtil.SCRIPT_TAG_NAME) && !StringUtil.equals(xmlTag.getName(), BasicHtmlUtil.STYLE_TAG_NAME)) {
      return;
    }
    for (XmlTagChild child : xmlTag.getValue().getChildren()) {
      Language childLanguage = child.getLanguage();
      if (childLanguage.isKindOf(XMLLanguage.INSTANCE)) {
        continue;
      }
      Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(childLanguage);

      ArrangementSettings arrangementSettingsForLanguage =
        ArrangementUtil.getArrangementSettings(CodeStyle.getSettings(child.getContainingFile()), childLanguage);
      if (arrangementSettingsForLanguage == null || rearranger == null) {
        continue;
      }

      List<? extends ArrangementEntry> foreignEntries = rearranger.parse(child, document, ranges, arrangementSettingsForLanguage);
      if (!foreignEntries.isEmpty()) {
        ForeignEntry foreignParent = new ForeignEntry(xmlTagEntry, child.getTextRange());
        for (ArrangementEntry foreignEntry : foreignEntries) {
          foreignParent.addChild(foreignEntry);
        }
        xmlTagEntry.addChild(foreignParent);
      }
    }
  }

  private static class ForeignEntry extends XmlElementArrangementEntry {
    ForeignEntry(@Nullable ArrangementEntry parent,
                 @NotNull TextRange range) {
      super(parent, range, StdArrangementTokens.EntryType.XML_TAG, null, null, false);
    }
  }
}
