// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.arrangement;

import com.intellij.psi.codeStyle.arrangement.ArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HtmlRearranger extends XmlRearranger {
  private static final List<StdArrangementMatchRule> DEFAULT_MATCH_RULES = new ArrayList<>();
  private static final StdArrangementSettings DEFAULT_SETTINGS;

  static {
    StdArrangementMatchRule rule = new StdArrangementMatchRule(
      new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(StdArrangementTokens.EntryType.XML_ATTRIBUTE)),
      StdArrangementTokens.Order.BY_NAME);
    DEFAULT_MATCH_RULES.add(rule);

    DEFAULT_SETTINGS = StdArrangementSettings.createByMatchRules(Collections.emptyList(), DEFAULT_MATCH_RULES);
  }

  private static final DefaultArrangementSettingsSerializer SETTINGS_SERIALIZER = new DefaultArrangementSettingsSerializer(DEFAULT_SETTINGS);
  @Nullable
  @Override
  public StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @NotNull
  @Override
  public ArrangementSettingsSerializer getSerializer() {
    return SETTINGS_SERIALIZER;
  }
}
