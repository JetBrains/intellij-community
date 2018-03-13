// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PyCharmLanguageCustomization extends IdeLanguageCustomization {
  @NotNull
  @Override
  public List<Language> getPrimaryIdeLanguages() {
    return Collections.singletonList(PythonLanguage.getInstance());
  }
}
