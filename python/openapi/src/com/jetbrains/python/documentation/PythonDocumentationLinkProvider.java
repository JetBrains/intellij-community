/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PythonDocumentationLinkProvider {
  ExtensionPointName<PythonDocumentationLinkProvider> EP_NAME = ExtensionPointName.create("Pythonid.documentationLinkProvider");

  @Nullable
  String getExternalDocumentationUrl(PsiElement element, PsiElement originalElement);

  /**
   * This method was used to provide the fallback URL in case the one returned by {@link #getExternalDocumentationUrl(PsiElement, PsiElement)}
   * doesn't exist. This check is not performed any longer to avoid UI sluggishness.
   *
   * @deprecated Do your best to provide a valid URL in {@link #getExternalDocumentationUrl(PsiElement, PsiElement)}. To be removed in 2019.2.
   */
  @Deprecated
  default String getExternalDocumentationRoot(Sdk sdk) {
    return "";
  }
}
