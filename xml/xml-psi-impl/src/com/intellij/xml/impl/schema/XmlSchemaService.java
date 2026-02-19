// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.schema;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

@ApiStatus.Internal
public interface XmlSchemaService {
  static XmlSchemaService getInstance() {
    return ApplicationManager.getApplication().getService(XmlSchemaService.class);
  }

  @Nullable XmlFile guessDtd(String dtdUri, @NotNull PsiFile baseFile);

  @Nullable XmlFile guessSchema(String namespace,
                                final @Nullable String tagName,
                                final @Nullable String version,
                                @Nullable String schemaLocation,
                                @NotNull PsiFile file);

  @Unmodifiable @NotNull Collection<XmlFile> findNSFilesByURI(@NotNull String namespace, @NotNull Project project, @Nullable Module module);
}
