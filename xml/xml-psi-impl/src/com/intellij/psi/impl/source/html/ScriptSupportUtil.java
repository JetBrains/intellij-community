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
package com.intellij.psi.impl.source.html;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.HtmlPsiUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class ScriptSupportUtil {
  private static final Key<CachedValue<XmlTag[]>> CachedScriptTagsKey = Key.create("script tags");
  private static final ThreadLocal<String> ProcessingDeclarationsFlag = new ThreadLocal<>();

  private ScriptSupportUtil() {
  }

  public static void clearCaches(XmlFile element) {
    element.putUserData(CachedScriptTagsKey,null);
  }

  public static boolean processDeclarations(final XmlFile element,
                                            PsiScopeProcessor processor,
                                            ResolveState state,
                                            PsiElement lastParent,
                                            PsiElement place) {
    CachedValue<XmlTag[]> myCachedScriptTags = element.getUserData(CachedScriptTagsKey);
    if (myCachedScriptTags == null) {
      myCachedScriptTags = CachedValuesManager.getManager(element.getProject())
          .createCachedValue(() -> {
            final List<XmlTag> scriptTags = new ArrayList<>();
            final XmlDocument document = HtmlPsiUtil.getRealXmlDocument(element.getDocument());

            if (document != null) {
              PsiElementProcessor psiElementProcessor = new PsiElementProcessor() {
                @Override
                public boolean execute(@NotNull final PsiElement element1) {
                  if (element1 instanceof XmlTag) {
                    final XmlTag tag = (XmlTag)element1;

                    if (HtmlUtil.SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getName())) {
                      final XmlElementDescriptor descriptor = tag.getDescriptor();
                      if (descriptor != null && HtmlUtil.SCRIPT_TAG_NAME.equals(descriptor.getName())) {
                        scriptTags.add(tag);
                      }
                    }
                  }
                  return true;
                }
              };
              XmlPsiUtil.processXmlElements(document, psiElementProcessor, true);
            }

            return new CachedValueProvider.Result<>(scriptTags.toArray(new XmlTag[scriptTags.size()]), element);
          }, false);
      element.putUserData(CachedScriptTagsKey, myCachedScriptTags);
    }

    if (ProcessingDeclarationsFlag.get() != null) return true;

    try {
      ProcessingDeclarationsFlag.set("");

      for (XmlTag tag : myCachedScriptTags.getValue()) {
        final XmlTagChild[] children = tag.getValue().getChildren();
        for (XmlTagChild child : children) {
          if (!child.processDeclarations(processor, state, null, place)) return false;
        }

        if (tag.getAttributeValue("src") != null) {
          final XmlAttribute attribute = tag.getAttribute("src", null);

          if (attribute != null) {
            final PsiFile psiFile = FileReferenceUtil.findFile(attribute.getValueElement());

            if (psiFile != null && psiFile.isValid()) {
              if (!psiFile.processDeclarations(processor, state, null, place)) {
                return false;
              }
            }
          }
        }
      }
    }
    finally {
      ProcessingDeclarationsFlag.set(null);
    }

    return true;
  }
}
