// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provide default mappings for namespace prefixes to namespaces for given XML file.
 */
public interface XmlFileNSInfoProvider {

  ExtensionPointName<XmlFileNSInfoProvider> EP_NAME = new ExtensionPointName<>("com.intellij.xml.fileNSInfoProvider");

  /**
   * Provides information (if any) for default mappings of namespace prefix to namespace identifiers.
   *
   * @param file for which ns mapping information is requested.
   * @return Array of namespace prefix to namespace mappings for given file in the format {@code [nsPrefix, namespaceId]} or
   * {@code null} if it does not know about such mappings.
   * Empty nsPrefix is {@code ""}, {@code nsPrefix}, {@code namespaceId} should not be {@code null}, invalid mapping table is skipped.
   */
  @NonNls
  String[] @Nullable [] getDefaultNamespaces(@NotNull XmlFile file);

  boolean overrideNamespaceFromDocType(@NotNull XmlFile file);
}
