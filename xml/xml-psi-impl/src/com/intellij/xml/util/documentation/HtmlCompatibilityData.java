// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util.documentation;

import com.google.gson.Gson;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HtmlCompatibilityData {
  private static final Map<String, Object> ourTagsCache = new HashMap<>();
  private static final Ref<Map> ourGlobalAttributesCache = new Ref<>();

  @Nullable
  public static Map getTagData(@NotNull String tagName) {
    String key = tagName.equals("input") ? "input/text" : tagName;
    if (!ourTagsCache.containsKey(key)) {
      URL resource = HtmlCompatibilityData.class.getResource("compatData/elements/" + key + ".json");
      if (resource == null) return null;
      try {
        Object json = new Gson().fromJson(new InputStreamReader(resource.openStream()), Object.class);
        Object tagHolder = ((Map)json).get("html");
        if (tagHolder == null) ((Map)json).get("svg");
        if (tagHolder == null) ((Map)json).get("mathml");
        if (tagHolder == null) {
          ourTagsCache.put(key, null);
          return null;
        }

        Object elements = ((Map)tagHolder).get("elements");
        int subKey = key.indexOf("/");
        Object data;
        if (subKey > 0) {
          Object element = ((Map)elements).get(key.substring(0, subKey));
          data = ((Map)element).get(key.replace('/', '-'));
        } else {
          data = ((Map)elements).get(key);
        }
        ourTagsCache.put(key, data);
      }
      catch (IOException e) {
        ourTagsCache.put(key, null);
        return null;
      }
    }
    return (Map)ourTagsCache.get(key);
  }

  @Nullable
  public static Map getTagData(@Nullable XmlTag tag) {
    if (tag == null) return null;
    String key = tag.getName().toLowerCase(Locale.US);
    if ("input".equals(key)) {
      String type = tag.getAttributeValue("type");
      if (type != null) {
        key += type;
      }
    }
    return getTagData(key);
  }

  @Nullable
  public static Map getAttributeData(@Nullable XmlTag tag, String attributeName) {
    Object data = getTagData(tag);
    if (data != null) {
      Object attributeData = ((Map)data).get(attributeName.toLowerCase(Locale.US));
      if (attributeData != null) return (Map)attributeData;
    }
    if (ourGlobalAttributesCache.get() == null) {
      Object json = new Gson().fromJson(new InputStreamReader(HtmlCompatibilityData.class.getResourceAsStream("compatData/global_attributes.json")), Object.class);
      Object html = ((Map)json).get("html");
      Object globalAttributes = ((Map)html).get("global_attributes");
      ourGlobalAttributesCache.set((Map)globalAttributes);
    }
    return (Map)ourGlobalAttributesCache.get().get(attributeName);
  }
}
