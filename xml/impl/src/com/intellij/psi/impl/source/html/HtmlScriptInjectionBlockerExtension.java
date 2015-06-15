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
import org.jetbrains.annotations.NotNull;

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
   * Checks if language injection to this tag is explicitly denied by one or more @link HtmlScriptInjectionBlocker extension points}
   *
   * @param xmlTag   &lt;script&gt; tag
   * @param language lang user wants to inject (probably obtained via {@link HtmlScriptLanguageInjector}
   * @return true if injection denied by extension point
   */

  public static boolean isInjectionBlocked(@NotNull XmlTag xmlTag, @NotNull Language language) {

    Collection<Language> allFileLanguages = xmlTag.getContainingFile().getViewProvider().getLanguages();

    for (Language fileLanguage : allFileLanguages) {
      for (HtmlScriptInjectionBlocker blocker : INSTANCE.allForLanguage(fileLanguage)) {
        if (blocker.isLanguageInjectionDenied(xmlTag, language)) {
          // Language exists, but denied by EP
          return true;
        }
      }
    }
    // Language exists and not denied
    return false;
  }
}
