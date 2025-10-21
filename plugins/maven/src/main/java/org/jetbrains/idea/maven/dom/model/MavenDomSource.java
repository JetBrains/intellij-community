// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model;

import com.intellij.openapi.paths.PathReference;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyTypeConverter;
import org.jetbrains.idea.maven.dom.converters.MavenParentRelativePathConverter;
import org.jetbrains.idea.maven.dom.converters.MavenSourceLangConverter;
import org.jetbrains.idea.maven.dom.converters.MavenSourceScopeConverter;
import org.jetbrains.idea.maven.dom.references.MavenDirectoryPathReferenceConverter;

import java.util.List;

public interface MavenDomSource {

  @NotNull
  @Required(value = true, nonEmpty = true)
  @Convert(value = MavenDirectoryPathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getDirectory();

  @NotNull
  MavenDomIncludes getIncludes();

  @NotNull
  MavenDomExcludes getExcludes();

  @NotNull
  @Convert(MavenSourceScopeConverter.class)
  GenericDomValue<String> getScope();

  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenSourceLangConverter.class)
  GenericDomValue<String> getLang();

  @NotNull
  GenericDomValue<String> getModule();

  @NotNull
  GenericDomValue<String> getTargetVersion();

  @NotNull
  @Convert(value = MavenDirectoryPathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getTargetPath();

  @NotNull
  GenericDomValue<Boolean> isFiltering();

  @NotNull
  GenericDomValue<Boolean> isEnabled();
}
