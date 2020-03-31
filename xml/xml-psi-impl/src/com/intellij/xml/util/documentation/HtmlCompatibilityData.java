// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util.documentation;

import com.google.gson.Gson;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HtmlCompatibilityData {
  private static final Map<String, Object> ourTagsCache = new HashMap<>();
  private static final Ref<Map> ourGlobalAttributesCache = new Ref<>();
  public static final String MATHML = "mathml";
  public static final String SVG = "svg";
  public static final String MATH = "math";

  @Nullable
  public static Map getTagData(@NotNull String namespace, @NotNull String tagName) {
    if (tagName.equals(MATH)) {
      namespace = MATHML;
    } else if (tagName.equals("svg")) {
      namespace = SVG;
    }
    String key = tagName.equals("input") ? "input/text" : tagName;
    String cacheKey = namespace + key;
    if (!ourTagsCache.containsKey(cacheKey)) {
      URL resource = HtmlCompatibilityData.class.getResource("compatData" + (!namespace.isEmpty() ? "/" + namespace : "") + "/elements/" + key + ".json");
      if (resource == null) return null;
      try {
        Object json = new Gson().fromJson(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8), Object.class);
        Object tagHolder = ((Map)json).get(namespace.isEmpty() ? "html" : namespace);
        if (tagHolder == null) {
          ourTagsCache.put(cacheKey, null);
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
        ourTagsCache.put(cacheKey, data);
      }
      catch (IOException e) {
        ourTagsCache.put(cacheKey, null);
        return null;
      }
    }
    return (Map)ourTagsCache.get(cacheKey);
  }

  @Nullable
  public static Map getTagData(@Nullable XmlTag tag) {
    if (tag == null) return null;
    String key = tag.isCaseSensitive() ?
                 tag.getName() :
                 StringUtil.toLowerCase(tag.getName());
    if ("input".equals(key)) {
      String type = tag.getAttributeValue("type");
      if (type != null) {
        key += type;
      }
    }
    return getTagData(getNamespace(tag), key);
  }

  private static String getNamespace(XmlTag tag) {
    PsiElement element = tag;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof XmlTag) {
        String name = tag.isCaseSensitive() ?
                     ((XmlTag)element).getName() :
                     StringUtil.toLowerCase(((XmlTag)element).getName());

        if (MATH.equals(name)) {
          return MATHML;
        }
        if (SVG.equals(name)) {
          return SVG;
        }
      }
      element = element.getParent();
    }
    return "";
  }

  @Nullable
  public static Map getAttributeData(@Nullable XmlTag tag, String attributeName) {
    Object data = getTagData(tag);
    if (data != null) {
      Object attributeData = ((Map)data).get(attributeName);
      if (attributeData != null) return (Map)attributeData;
    }
    if (ourGlobalAttributesCache.get() == null) {
      Object json = new Gson().fromJson(new InputStreamReader(HtmlCompatibilityData.class.getResourceAsStream("compatData/global_attributes.json"),
                                                              StandardCharsets.UTF_8), Object.class);
      Object html = ((Map)json).get("html");
      Object globalAttributes = ((Map)html).get("global_attributes");
      ourGlobalAttributesCache.set((Map)globalAttributes);
    }
    return (Map)ourGlobalAttributesCache.get().get(attributeName);
  }
}
