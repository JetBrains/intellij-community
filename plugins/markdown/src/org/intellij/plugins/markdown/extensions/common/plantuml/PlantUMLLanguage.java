// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml;

import com.intellij.lang.Language;

public class PlantUMLLanguage extends Language {
  public static final PlantUMLLanguage INSTANCE = new PlantUMLLanguage();

  protected PlantUMLLanguage() {
    super("PlantUML");
  }
}
