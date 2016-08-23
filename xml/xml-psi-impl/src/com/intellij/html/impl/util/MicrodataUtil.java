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
package com.intellij.html.impl.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.DependentNSReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PairFunction;
import com.intellij.util.text.StringTokenizer;
import com.intellij.xml.util.HtmlUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author: Fedor.Korotkov
 */
public class MicrodataUtil {
  public static final Key<List<String>> ITEM_PROP_KEYS = Key.create("microdata.prop");
  public static final String ITEM_REF = "itemref";
  public static final String ITEM_SCOPE = "itemscope";
  public static final String ITEM_TYPE = "itemtype";
  public static final String ITEM_PROP = "itemprop";
  public static final String ITEM_ID = "itemid";

  public static boolean hasScopeTag(@Nullable XmlTag tag) {
    return findScopeTag(tag) != null;
  }

  @Nullable
  public static XmlTag findScopeTag(@Nullable XmlTag context) {
    Map<String, XmlTag> id2tag = findScopesWithItemRef(context != null ? context.getContainingFile() : null);
    XmlTag tag = context;
    while (tag != null) {
      if (tag != context && tag.getAttribute(ITEM_SCOPE) != null) return tag;
      final String id = getStripedAttributeValue(tag, HtmlUtil.ID_ATTRIBUTE_NAME);
      if (id != null && id2tag.containsKey(id)) return id2tag.get(id);
      tag = tag.getParentTag();
    }
    return null;
  }

  private static Map<String, XmlTag> findScopesWithItemRef(@Nullable final PsiFile file) {
    if (!(file instanceof XmlFile)) return Collections.emptyMap();
    return CachedValuesManager.getCachedValue(file, new CachedValueProvider<Map<String, XmlTag>>() {
      @Nullable
      @Override
      public Result<Map<String, XmlTag>> compute() {
        final Map<String, XmlTag> result = new THashMap<>();
        file.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlTag(final XmlTag tag) {
            super.visitXmlTag(tag);
            XmlAttribute refAttr = tag.getAttribute(ITEM_REF);
            if (refAttr != null && tag.getAttribute(ITEM_SCOPE) != null) {
              getReferencesForAttributeValue(refAttr.getValueElement(), (t, v) -> {
                result.put(t, tag);
                return null;
              });
            }
          }
        });
        return Result.create(result, file);
      }
    });
  }

  public static List<String> extractProperties(PsiFile file, String type) {
    final VirtualFile virtualFile = file.getVirtualFile();
    List<String> result = virtualFile != null ? virtualFile.getUserData(ITEM_PROP_KEYS) : null;
    if (virtualFile != null && result == null) {
      result = collectNames(file, type);
      virtualFile.putUserData(ITEM_PROP_KEYS, result);
    }
    return result;
  }

  private static List<String> collectNames(PsiFile file, String type) {
    if (file instanceof XmlFile) {
      final CollectNamesVisitor collectNamesVisitor = getVisitorByType(type);
      file.accept(collectNamesVisitor);
      return collectNamesVisitor.getValues();
    }
    return Collections.emptyList();
  }

  private static CollectNamesVisitor getVisitorByType(String type) {
    if (type.contains("schema.org")) {
      return new CollectNamesFromSchemaOrgVisitor();
    }
    return new CollectNamesByMicrodataVisitor(type);
  }

  public static PsiReference[] getUrlReferencesForAttributeValue(final XmlAttributeValue element) {
    return getReferencesForAttributeValue(element, (token, offset) -> {
      if (HtmlUtil.hasHtmlPrefix(token)) {
        final TextRange range = TextRange.from(offset, token.length());
        final URLReference urlReference = new URLReference(element, range, true);
        return new DependentNSReference(element, range, urlReference, true);
      }
      return null;
    });
  }

  public static PsiReference[] getReferencesForAttributeValue(@Nullable XmlAttributeValue element,
                                                              PairFunction<String, Integer, PsiReference> refFun) {
    if (element == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    String text = element.getText();
    String urls = StringUtil.unquoteString(text);
    StringTokenizer tokenizer = new StringTokenizer(urls);
    List<PsiReference> result = new ArrayList<>();
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      int index = text.indexOf(token);
      PsiReference ref = refFun.fun(token, index);
      if (ref != null) {
        result.add(ref);
      }
    }
    return result.toArray(new PsiReference[result.size()]);
  }

  @Nullable
  public static String getStripedAttributeValue(@Nullable XmlTag tag, @Nls String attributeName) {
    String value = tag != null ? tag.getAttributeValue(attributeName) : null;
    return value != null ? StringUtil.unquoteString(value) : null;
  }

  private static class CollectNamesVisitor extends XmlRecursiveElementVisitor {
    protected final Set<String> myValues = new THashSet<>();

    public List<String> getValues() {
      return new ArrayList<>(myValues);
    }
  }

  public static class CollectNamesByMicrodataVisitor extends CollectNamesVisitor {
    protected final String myType;
    private boolean myCollecting = false;

    public CollectNamesByMicrodataVisitor(String type) {
      myType = type;
    }

    @Override
    public void visitXmlTag(XmlTag tag) {
      String value = getStripedAttributeValue(tag, ITEM_ID);
      final boolean isTypeTag = myType.equalsIgnoreCase(value);
      if (isTypeTag) {
        myCollecting = true;
      }

      if (myCollecting && "name".equalsIgnoreCase(getStripedAttributeValue(tag, ITEM_PROP))) {
        myValues.add(tag.getValue().getTrimmedText());
      }

      super.visitXmlTag(tag);

      if (isTypeTag) {
        myCollecting = false;
      }
    }
  }

  public static class CollectNamesFromSchemaOrgVisitor extends CollectNamesVisitor {
    @Override
    public void visitXmlTag(XmlTag tag) {
      super.visitXmlTag(tag);
      if ("prop-nam".equalsIgnoreCase(getStripedAttributeValue(tag, HtmlUtil.CLASS_ATTRIBUTE_NAME))) {
        final String code = tag.getSubTagText("code");
        if (code != null) {
          myValues.add(StringUtil.stripHtml(code, false));
        }
      }
    }
  }
}
