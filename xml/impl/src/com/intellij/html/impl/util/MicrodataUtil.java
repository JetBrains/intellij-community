/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ManuallySetupExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URIReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringTokenizer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

  @NonNls private static final String HTTP = "http://";
  @NonNls private static final String HTTPS = "https://";

  public static boolean hasScopeTag(@Nullable XmlTag tag) {
    return findScopeTag(tag) != null;
  }

  @Nullable
  public static XmlTag findScopeTag(@Nullable XmlTag tag) {
    while (tag != null) {
      if (tag.getAttribute(ITEM_SCOPE) != null) return tag;
      XmlTag scopeTag = findInRefsById(tag);
      if (scopeTag != null) return scopeTag;
      tag = tag.getParentTag();
    }
    return null;
  }

  @Nullable
  private static XmlTag findInRefsById(XmlTag tag) {
    XmlAttribute idAttr = tag.getAttribute("id");
    final XmlAttributeValue idValue = idAttr != null ? idAttr.getValueElement() : null;
    if (idValue == null) {
      return null;
    }
    final String idToFind = StringUtil.stripQuotesAroundValue(idValue.getText());
    XmlTag parentTag = tag.getParentTag();
    while (parentTag != null) {
      XmlTag scopeTag = ContainerUtil.find(parentTag.getSubTags(), new Condition<XmlTag>() {
        @Override
        public boolean value(XmlTag tag) {
          String refValue = tag.getAttributeValue(ITEM_REF);
          return refValue != null && refValue.contains(idToFind);
        }
      });
      if (scopeTag != null) {
        return scopeTag;
      }
      parentTag = parentTag.getParentTag();
    }
    return null;
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
      final CollectNamesVisitor collectNamesVisitor = new CollectNamesVisitor(type);
      file.accept(collectNamesVisitor);
      return collectNamesVisitor.getValues();
    }
    return Collections.emptyList();
  }

  public static PsiReference[] getUrlReferencesForAttributeValue(final XmlAttributeValue element) {
    return getReferencesForAttributeValue(element, new PairFunction<String, Integer, PsiReference>() {
      @Nullable
      @Override
      public PsiReference fun(String token, Integer offset) {
        if (isUrl(token)) {
          final TextRange range = TextRange.from(offset, token.length());
          final URLReference urlReference = new URLReference(element, range, true);
          return new URIReferenceProvider.DependentNSReference(element, range, urlReference) {
            @Override
            public void registerQuickfix(HighlightInfo info, PsiReference reference) {
              QuickFixAction.registerQuickFixAction(info, new FetchMicrodataResourceAction());
              QuickFixAction.registerQuickFixAction(info, new ManuallySetupExtResourceAction());
              QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction());
            }
          };
        }
        return null;
      }
    });
  }

  public static PsiReference[] getReferencesForAttributeValue(XmlAttributeValue element,
                                                              PairFunction<String, Integer, PsiReference> refFun) {
    String text = element.getText();
    String urls = StringUtil.stripQuotesAroundValue(text);
    StringTokenizer tokenizer = new StringTokenizer(urls);
    List<PsiReference> result = new ArrayList<PsiReference>();
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

  private static boolean isUrl(String url) {
    return url.startsWith(HTTP) || url.startsWith(HTTPS);
  }

  @Nullable
  public static String getStripedAttributeValue(@Nullable XmlTag tag, @Nls String attributeName) {
    String value = tag != null ? tag.getAttributeValue(attributeName) : null;
    return value != null ? StringUtil.stripQuotesAroundValue(value) : null;
  }

  private static class CollectNamesVisitor extends XmlRecursiveElementVisitor {

    private final String myType;
    private boolean myCollecting = false;
    private final Set<String> myValues = new THashSet<String>();

    public CollectNamesVisitor(String type) {
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

    public List<String> getValues() {
      return new ArrayList<String>(myValues);
    }
  }

  private static class FetchMicrodataResourceAction extends FetchExtResourceAction {
    @Override
    protected boolean resultIsValid(Project project,
                                    ProgressIndicator indicator,
                                    String resourceUrl,
                                    FetchExtResourceAction.FetchResult result) {
      return true;
    }
  }
}
