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
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * Interface to be implemented by extension point that wants to prevent some language from injection into script HTML tag
 *
 * @author Ilya.Kazakevich
 */
public interface HtmlScriptInjectionBlocker {
  /**
   * @param scriptTag &lt;script&gt; tag
   * @param language  language that should be injected according to <pre>type</pre> attribute
   * @return true if language <strong>should not</strong> be injected
   */
  boolean isLanguageInjectionDenied(@NotNull XmlTag scriptTag, @NotNull Language language);
}
