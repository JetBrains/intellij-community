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
package com.intellij.lang.xml;

import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class XMLLanguage extends CompositeLanguage {

  public final static XMLLanguage INSTANCE = new XMLLanguage();

  private XMLLanguage() {
    super("XML", "application/xml", "text/xml");
  }

  @Deprecated
  protected XMLLanguage(@NonNls String name, @NonNls String... mime) {
    super(name, mime);
  }

  protected XMLLanguage(Language baseLanguage, @NonNls String name, @NonNls String... mime) {
    super(baseLanguage, name, mime);
  }
}
