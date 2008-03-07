/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlSchemaProvider {

  public final static ExtensionPointName<XmlSchemaProvider> EP_NAME = new ExtensionPointName<XmlSchemaProvider>("com.intellij.xml.schemaProvider");

  @Nullable
  public static XmlFile findSchema(@NotNull @NonNls String url, @Nullable Module module, @NotNull PsiFile file) {
    for (XmlSchemaProvider provider: Extensions.getExtensions(EP_NAME)) {
      if (file instanceof XmlFile && !provider.isAvailable((XmlFile)file)) {
        continue;
      }
      final XmlFile schema = provider.getSchema(url, module, file);
      if (schema != null) {
        return schema;
      }
    }
    return null;
  }

  @Nullable
  public static XmlFile findSchema(@NotNull @NonNls String url, @NotNull PsiFile baseFile) {
    final PsiDirectory directory = baseFile.getParent();
    final Module module = ModuleUtil.findModuleForPsiElement(directory == null ? baseFile : directory);
    return findSchema(url, module, baseFile);
  }

  @Nullable
  public static XmlSchemaProvider getAvailableProvider(final @NotNull XmlFile file) {
    for (XmlSchemaProvider provider: Extensions.getExtensions(EP_NAME)) {
      if (provider.isAvailable(file)) {
        return provider;
      }
    }
    return null;    
  }

  @Nullable
  public abstract XmlFile getSchema(@NotNull @NonNls String url, @Nullable Module module, @NotNull final PsiFile baseFile);


  public boolean isAvailable(final @NotNull XmlFile file) {
    return false;
  }

  /**
   * Provides specific namespaces for given xml file.
   * @param file an xml or jsp file.
   * @return available namespace uris, or <code>null</code> if the provider did not recognize the file.
   */
  @NotNull
  public Set<String> getAvailableNamespaces(final @NotNull XmlFile file) {
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
