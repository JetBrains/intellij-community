/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface BundleNameEvaluator {

  BundleNameEvaluator DEFAULT = new BundleNameEvaluator() {

    @Nullable
    public String evaluateBundleName(final PsiFile psiFile) {
      final VirtualFile virtualFile = psiFile == null ? null : psiFile.getOriginalFile().getVirtualFile();
      if (virtualFile == null) {
        return null;
      }

      final PsiDirectory directory = psiFile.getParent();
      if (directory == null) {
        return null;
      }

      final String packageQualifiedName = PropertiesUtil.getPackageQualifiedName(directory);

      if (packageQualifiedName != null) {
        final StringBuilder qName = new StringBuilder(packageQualifiedName);
        if (qName.length() > 0) {
          qName.append(".");
        }
        qName.append(PropertiesUtil.getBaseName(virtualFile));
        return qName.toString();
      }
      return null;
    }
  };

  @Nullable
  String evaluateBundleName(PsiFile psiFile);
}
