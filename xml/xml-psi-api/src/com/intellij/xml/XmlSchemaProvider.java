// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlSchemaProvider implements PossiblyDumbAware {
  public static final ExtensionPointName<XmlSchemaProvider> EP_NAME = new ExtensionPointName<>("com.intellij.xml.schemaProvider");

  @Nullable
  public static XmlFile findSchema(@NotNull @NonNls String namespace, @Nullable Module module, @NotNull PsiFile file) {
    if (file.getProject().isDefault()) return null;
    for (XmlSchemaProvider provider : EP_NAME.getExtensionList()) {
      if (!DumbService.getInstance(file.getProject()).isUsableInCurrentContext(provider)) {
        continue;
      }

      if (file instanceof XmlFile && !provider.isAvailable((XmlFile)file)) {
        continue;
      }
      final XmlFile schema = provider.getSchema(namespace, module, file);
      if (schema != null) {
        return schema;
      }
    }
    return null;
  }

  @Nullable
  public static XmlFile findSchema(@NotNull @NonNls String namespace, @NotNull PsiFile baseFile) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(baseFile);
    return findSchema(namespace, module, baseFile);
  }

  public static @NotNull List<XmlSchemaProvider> getAvailableProviders(@NotNull XmlFile file) {
    return ContainerUtil.findAll(EP_NAME.getExtensionList(), xmlSchemaProvider -> xmlSchemaProvider.isAvailable(file));
  }

  @Nullable
  public abstract XmlFile getSchema(@NotNull @NonNls String url, @Nullable Module module, @NotNull final PsiFile baseFile);


  public boolean isAvailable(@NotNull final XmlFile file) {
    return false;
  }

  /**
   * Provides specific namespaces for given XML file.
   *
   * @param file    XML or JSP file.
   * @param tagName optional
   * @return available namespace uris, or {@code null} if the provider did not recognize the file.
   */
  @NotNull
  public Set<String> getAvailableNamespaces(@NotNull final XmlFile file, @Nullable final String tagName) {
    return Collections.emptySet();
  }

  @Nullable
  public String getDefaultPrefix(@NotNull @NonNls String namespace, @NotNull final XmlFile context) {
    return null;
  }

  @Nullable
  public Set<String> getLocations(@NotNull @NonNls String namespace, @NotNull final XmlFile context) {
    return null;
  }
}
