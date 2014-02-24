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
package com.intellij.xml.util;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public abstract class TagSetRuleProvider extends XmlTagRuleProviderBase {

  private final Map<String, TagsRuleMap> map = Collections.synchronizedMap(new HashMap<String, TagsRuleMap>());

  @Nullable
  protected abstract String getNamespace(@NotNull XmlTag tag);

  protected abstract void initMap(TagsRuleMap map, @NotNull String version);

  @Override
  public Rule[] getTagRule(@NotNull XmlTag tag) {
    String namespace = getNamespace(tag);
    if (namespace == null) return Rule.EMPTY_ARRAY;

    return getTagRule(tag, namespace);
  }

  public Rule[] getTagRule(@NotNull XmlTag tag, String namespace) {
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
