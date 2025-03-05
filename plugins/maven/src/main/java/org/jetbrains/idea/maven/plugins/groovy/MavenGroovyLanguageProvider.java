// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.lang.Language;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.plugins.api.MavenParamLanguageProvider;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class MavenGroovyLanguageProvider extends MavenParamLanguageProvider {

  @Override
  public @Nullable Language getLanguage(@NotNull XmlText xmlText, @NotNull MavenDomConfiguration configuration) {
    // Parameter 'source' of gmaven-plugin can be a peace of groovy code or file path or URL.

    String text = xmlText.getText();

    if (text.indexOf('\n') >= 0) { // URL or file path can not be multiline so it's a groovy code
      return GroovyLanguage.INSTANCE;
    }
    if (text.indexOf('(') >= 0) { // URL or file path hardly contains '(', but code usually contain '('
      return GroovyLanguage.INSTANCE;
    }

    return null;
  }
}
