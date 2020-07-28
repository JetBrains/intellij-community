// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlantUMLCodeFenceLanguageProvider implements CodeFenceLanguageProvider {
  private static final String PLANTUML = "plantuml";
  private static final String PUML = "puml";
  private static final List<String> PLANT_UML_LANGS = Arrays.asList(PLANTUML, PUML);

  @Nullable
  @Override
  public Language getLanguageByInfoString(@NotNull String infoString) {
    return PLANT_UML_LANGS.contains(infoString) ? PlantUMLLanguage.INSTANCE : null;
  }

  @NotNull
  @Override
  public List<LookupElement> getCompletionVariantsForInfoString(@NotNull CompletionParameters parameters) {
    return Collections.singletonList(LookupElementBuilder.create(PUML));
  }
}
