/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Language extension to block script injection into HTML tag &lt;script&gt; when this language exists on file's
 * {@link com.intellij.psi.FileViewProvider}.
 *
 * @author Ilya.Kazakevich
 */
public final class HtmlScriptInjectionBlockerExtension extends LanguageExtension<HtmlScriptInjectionBlocker> {
  private static final HtmlScriptInjectionBlockerExtension INSTANCE = new HtmlScriptInjectionBlockerExtension();

  private HtmlScriptInjectionBlockerExtension() {
    super("com.intellij.html.htmlScriptInjectionBlocker");
  }


  /**
   * Finds language to be injected into script tag and checks if is blocked by some extension point
   *
   * @param xmlTag tag that may be script tag.
   * @return null if tag is not script tag at all, info otherwise
   */
  @Nullable
  public static InjectionInfo getInjectionInfo(@NotNull XmlTag xmlTag) {
    if (!HtmlUtil.isScriptTag(xmlTag)) {
      return null;
    }
    String mimeType = xmlTag.getAttributeValue("type");
    Collection<Language> languages = Language.findInstancesByMimeType(mimeType);
    Language language = !languages.isEmpty() ? languages.iterator().next() : Language.ANY;


    Collection<Language> allFileLanguages = xmlTag.getContainingFile().getViewProvider().getLanguages();
    for (Language fileLanguage : allFileLanguages) {
      for (final HtmlScriptInjectionBlocker blocker : INSTANCE.allForLanguage(fileLanguage)) {
        if (blocker.isDenyLanguageInjection(xmlTag, language)) {
          // Language exists, but denied by EP
          return new InjectionInfo(true, language);
        }
      }
    }
    // Language exists and not denied
    return new InjectionInfo(false, language);
  }
}
