// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.lang.Language;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

public abstract class MavenParamLanguageProvider {

  public abstract @Nullable Language getLanguage(@NotNull XmlText xmlText, @NotNull MavenDomConfiguration configuration);

}
