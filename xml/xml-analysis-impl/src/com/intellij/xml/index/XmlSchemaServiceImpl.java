// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.index;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.impl.schema.XmlSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

public class XmlSchemaServiceImpl implements XmlSchemaService {
  @Override
  public @Nullable XmlFile guessDtd(String dtdUri, @NotNull PsiFile baseFile) {
    return XmlNamespaceIndex.guessDtd(dtdUri, baseFile);
  }

  @Override
  public @Nullable XmlFile guessSchema(String namespace,
                                       @Nullable String tagName,
                                       @Nullable String version,
                                       @Nullable String schemaLocation,
                                       @NotNull PsiFile file) {
    return XmlNamespaceIndex.guessSchema(namespace, tagName, version, schemaLocation, file);
  }

  @Override
  public @Unmodifiable @NotNull Collection<XmlFile> findNSFilesByURI(@NotNull String namespace,
                                                                     @NotNull Project project,
                                                                     @Nullable Module module) {
    final List<IndexedRelevantResource<String, XsdNamespaceBuilder>>
      resources = XmlNamespaceIndex.getResourcesByNamespace(namespace, project, module);
    final PsiManager psiManager = PsiManager.getInstance(project);
    return ContainerUtil.mapNotNull(resources,
                                    (NullableFunction<IndexedRelevantResource<String, XsdNamespaceBuilder>, XmlFile>)resource -> {
                                      PsiFile file = psiManager.findFile(resource.getFile());
                                      return file instanceof XmlFile ? (XmlFile)file : null;
                                    });
  }
}
