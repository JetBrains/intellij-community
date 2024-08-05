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

  public abstract @Nullable XmlFile getSchema(@NotNull @NonNls String url, @Nullable Module module, final @NotNull PsiFile baseFile);

  public boolean isAvailable(final @NotNull XmlFile file) {
    return false;
  }

  public static @NotNull List<XmlSchemaProvider> getAvailableProviders(@NotNull XmlFile file) {
    return ContainerUtil.findAll(EP_NAME.getExtensionList(), xmlSchemaProvider -> xmlSchemaProvider.isAvailable(file));
  }

  /**
   * Provides specific namespaces for given XML file.
   *
   * @param file    XML or JSP file.
   * @param tagName optional
   * @return available namespace uris, or {@code null} if the provider did not recognize the file.
   */
  public @NotNull Set<String> getAvailableNamespaces(final @NotNull XmlFile file, final @Nullable String tagName) {
    return Collections.emptySet();
  }

  public @Nullable String getDefaultPrefix(@NotNull @NonNls String namespace, final @NotNull XmlFile context) {
    return null;
  }

  public @Nullable Set<String> getLocations(@NotNull @NonNls String namespace, final @NotNull XmlFile context) {
    return null;
  }

  public static @Nullable XmlFile findSchema(@NotNull @NonNls String namespace, @Nullable Module module, @NotNull PsiFile file) {
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

  public static @Nullable XmlFile findSchema(@NotNull @NonNls String namespace, @NotNull PsiFile baseFile) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(baseFile);
    return findSchema(namespace, module, baseFile);
  }
}
