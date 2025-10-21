// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenSource;

import java.util.Collection;
import java.util.List;

public class MavenSourceLangConverter extends MavenConstantListConverter {

  @Override
  protected Collection<@NlsSafe String> getValues(@NotNull ConvertContext context) {
    return List.of(MavenSource.JAVA_LANG, MavenSource.RESOURCES_LANG);
  }
}
