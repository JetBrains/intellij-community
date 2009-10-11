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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.references.MavenModulePsiReference;

public class MavenModuleConverter extends MavenReferenceConverter<PsiFile> {
  @Override
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString(@Nullable PsiFile psiFile, ConvertContext context) {
    VirtualFile file = context.getFile().getOriginalFile().getVirtualFile();
    return MavenModulePsiReference.calcRelativeModulePath(file, psiFile.getVirtualFile());
  }

  protected PsiReference createReference(PsiElement element, String text, TextRange range) {
    return new MavenModulePsiReference(element, text, range);
  }
}
