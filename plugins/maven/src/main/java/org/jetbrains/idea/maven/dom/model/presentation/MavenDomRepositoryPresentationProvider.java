// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomRepository;

/**
 *
 */
public class MavenDomRepositoryPresentationProvider extends PresentationProvider<MavenDomRepository> {
  @Override
  public @Nullable String getName(MavenDomRepository mavenDomRepository) {
    return "Repository (id=" + mavenDomRepository.getId().getStringValue()
           + ", url=" + mavenDomRepository.getUrl().getStringValue()
           + ')';
  }
}
