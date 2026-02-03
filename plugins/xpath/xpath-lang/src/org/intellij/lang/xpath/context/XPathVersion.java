/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.xpath.context;

import com.intellij.lang.Language;
import org.intellij.lang.xpath.XPathFileType;

public enum XPathVersion {
  V1(XPathFileType.XPATH), V2(XPathFileType.XPATH2);

  private final Language myLanguage;
  XPathVersion(final XPathFileType type) {
    myLanguage = type.getLanguage();
  }

  public Language getLanguage() {
    return myLanguage;
  }
}