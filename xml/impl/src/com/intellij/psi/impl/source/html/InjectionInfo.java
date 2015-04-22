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
import org.jetbrains.annotations.NotNull;

/**
 * Information about language injection
 *
 * @author Ilya.Kazakevich
 */
public class InjectionInfo {
  private final boolean myIsDeniedByExtensionPoint;
  @NotNull
  private final Language myLanguage;

  InjectionInfo(boolean deniedByExtensionPoint, @NotNull Language language) {
    myIsDeniedByExtensionPoint = deniedByExtensionPoint;
    myLanguage = language;
  }

  /**
   * @return true if injection is denied by {@link HtmlScriptInjectionBlocker}
   */
  public boolean isDeniedByExtensionPoint() {
    return myIsDeniedByExtensionPoint;
  }

  /**
   * @return language to inject
   */
  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }
}
