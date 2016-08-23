/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface BundleNameEvaluator {
  BundleNameEvaluator DEFAULT = new BundleNameEvaluator() {
    @Nullable
    public String evaluateBundleName(final PsiFile psiFile) {
      PsiDirectory directory = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
        @Override
        public PsiDirectory compute() {
          return psiFile.getParent();
        }
      });
      if (directory == null) return null;

      String packageQualifiedName = PropertiesUtil.getPackageQualifiedName(directory);
      if (packageQualifiedName == null) return null;

      StringBuilder qName = new StringBuilder(packageQualifiedName);
      if (qName.length() > 0) qName.append(".");
      qName.append(ResourceBundleManager.getInstance(psiFile.getProject()).getBaseName(psiFile));
      return qName.toString();
    }
  };

  BundleNameEvaluator BASE_NAME = new BundleNameEvaluator() {
    @Nullable
    public String evaluateBundleName(final PsiFile psiFile) {
      return ResourceBundleManager.getInstance(psiFile.getProject()).getBaseName(psiFile);
    }
  };

  @Nullable
  String evaluateBundleName(PsiFile psiFile);
}
