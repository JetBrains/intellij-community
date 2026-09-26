// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class TagSetRuleProvider extends XmlTagRuleProviderBase {

  private final Map<String, TagsRuleMap> map = Collections.synchronizedMap(new HashMap<>());

  protected abstract @Nullable String getNamespace(@NotNull XmlTag tag);

  protected abstract void initMap(TagsRuleMap map, @NotNull String version);

  @Override
  public Rule @NotNull [] getTagRule(@NotNull XmlTag tag) {
    String namespace = getNamespace(tag);
    if (namespace == null) return Rule.EMPTY_ARRAY;

    return getTagRule(tag, namespace);
  }

  public Rule @NotNull [] getTagRule(@NotNull XmlTag tag, String namespace) {
    TagsRuleMap ruleMap = map.get(namespace);
    if (ruleMap == null) {
      ruleMap = new TagsRuleMap();
      initMap(ruleMap, namespace);
      map.put(namespace, ruleMap);
    }

    String tagName = tag.getLocalName();
    Rule[] rules = ruleMap.get(tagName);
    if (rules == null) return Rule.EMPTY_ARRAY;

    return rules;
  }

  protected static class TagsRuleMap extends HashMap<String, Rule[]> {
    public void add(String tagName, Rule ... rules) {
      assert rules.length > 0;
      Rule[] oldValue = put(tagName, rules);
      assert oldValue == null;
    }
  }
}
