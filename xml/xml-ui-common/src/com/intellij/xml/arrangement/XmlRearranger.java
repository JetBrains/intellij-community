// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.*;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.XML_ATTRIBUTE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.XML_TAG;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.General.ORDER;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.General.TYPE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.KEEP;

public class XmlRearranger
  implements Rearranger<XmlElementArrangementEntry>,
             ArrangementStandardSettingsAware {

  private static final Set<ArrangementSettingsToken> SUPPORTED_TYPES = ContainerUtil.newLinkedHashSet(XML_TAG, XML_ATTRIBUTE);
  private static final List<StdArrangementMatchRule> DEFAULT_MATCH_RULES = new ArrayList<>();

  private static final StdArrangementSettings DEFAULT_SETTINGS;

  static {
    DEFAULT_MATCH_RULES.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(
      new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, "xmlns:.*"))));
    DEFAULT_SETTINGS = StdArrangementSettings.createByMatchRules(
      Collections.emptyList(), DEFAULT_MATCH_RULES);
  }

  private static final DefaultArrangementSettingsSerializer SETTINGS_SERIALIZER = new DefaultArrangementSettingsSerializer(DEFAULT_SETTINGS);

  public static @NotNull StdArrangementMatchRule attrArrangementRule(@NotNull String nameFilter,
                                                                     @NotNull String namespaceFilter,
                                                                     @NotNull ArrangementSettingsToken orderType) {
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(ArrangementUtil.combine(
      new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, nameFilter),
      new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.XML_NAMESPACE, namespaceFilter)
    )), orderType);
  }

  @Override
  public @NotNull ArrangementSettingsSerializer getSerializer() {
    return SETTINGS_SERIALIZER;
  }

  @Override
  public @Nullable StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current) {
    return SUPPORTED_TYPES.contains(token)
        || StdArrangementTokens.Regexp.NAME.equals(token)
        || StdArrangementTokens.Regexp.XML_NAMESPACE.equals(token)
        || KEEP.equals(token)
        || BY_NAME.equals(token);
  }

  @Override
  public @NotNull Collection<Set<ArrangementSettingsToken>> getMutexes() {
    return Collections.singleton(SUPPORTED_TYPES);
  }

  @Override
  public @Nullable Pair<XmlElementArrangementEntry, List<XmlElementArrangementEntry>> parseWithNew(@NotNull PsiElement root,
                                                                                                   @Nullable Document document,
                                                                                                   @NotNull Collection<? extends TextRange> ranges,
                                                                                                   @NotNull PsiElement element,
                                                                                                   @NotNull ArrangementSettings settings)
  {
    final XmlArrangementParseInfo newEntryInfo = new XmlArrangementParseInfo();
    element.accept(new XmlArrangementVisitor(newEntryInfo, Collections.singleton(element.getTextRange())));

    if (newEntryInfo.getEntries().size() != 1) {
      return null;
    }
    final XmlElementArrangementEntry entry = newEntryInfo.getEntries().get(0);
    final XmlArrangementParseInfo existingEntriesInfo = new XmlArrangementParseInfo();
    root.accept(new XmlArrangementVisitor(existingEntriesInfo, ranges));
    return Pair.create(entry, existingEntriesInfo.getEntries());
  }

  @Override
  public @NotNull List<XmlElementArrangementEntry> parse(@NotNull PsiElement root,
                                                         @Nullable Document document,
                                                         @NotNull Collection<? extends TextRange> ranges,
                                                         @NotNull ArrangementSettings settings) {
    final XmlArrangementParseInfo parseInfo = new XmlArrangementParseInfo();
    root.accept(new XmlArrangementVisitor(parseInfo, ranges));
    return parseInfo.getEntries();
  }

  @Override
  public int getBlankLines(@NotNull CodeStyleSettings settings,
                           @Nullable XmlElementArrangementEntry parent,
                           @Nullable XmlElementArrangementEntry previous,
                           @NotNull XmlElementArrangementEntry target) {
    return -1;
  }

  @Override
  public @Nullable List<CompositeArrangementSettingsToken> getSupportedGroupingTokens() {
    return null;
  }

  @Override
  public @Nullable List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    return List.of(
      new CompositeArrangementSettingsToken(TYPE, SUPPORTED_TYPES),
      new CompositeArrangementSettingsToken(StdArrangementTokens.Regexp.NAME),
      new CompositeArrangementSettingsToken(StdArrangementTokens.Regexp.XML_NAMESPACE),
      new CompositeArrangementSettingsToken(ORDER, KEEP, BY_NAME)
    );
  }

  @Override
  public @NotNull ArrangementEntryMatcher buildMatcher(@NotNull ArrangementMatchCondition condition) throws IllegalArgumentException {
    throw new IllegalArgumentException("Can't build a matcher for condition " + condition);
  }
}
