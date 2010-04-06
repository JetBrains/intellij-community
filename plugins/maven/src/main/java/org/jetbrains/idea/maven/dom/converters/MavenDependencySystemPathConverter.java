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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class MavenDependencySystemPathConverter extends ResolvingConverter<PsiFile> implements CustomReferenceConverter {
  @Override
  public PsiFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(s);
    if (f == null) return null;
    return PsiManager.getInstance(context.getXmlElement().getProject()).findFile(f);
  }

  public String toString(@Nullable PsiFile file, ConvertContext context) {
    if (file == null) return null;
    return file.getVirtualFile().getPath();
  }

  @NotNull
  public Collection<PsiFile> getVariants(ConvertContext context) {
    return Collections.emptyList();
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    XmlElement xmlElement = genericDomValue.getXmlElement();

    if (xmlElement != null && xmlElement.getText().contains("${")) return PsiReference.EMPTY_ARRAY;

    return createReferences(element, true);
  }

  @NotNull
  public static PsiReference[] createReferences(@NotNull final PsiElement psiElement, final boolean soft) {
    FileReferenceSet set = new MyFileReferenceSet(psiElement, soft);

    return set.getAllReferences();
  }

  private static class MyFileReferenceSet extends FileReferenceSet {
    private final boolean mySoft;

    public MyFileReferenceSet(PsiElement psiElement, boolean soft) {
      super(psiElement);
      mySoft = soft;
    }

    @Override
    public boolean isAbsolutePathReference() {
      return true;
    }

    @Override
    protected boolean isSoft() {
      return mySoft;
    }

    @NotNull
    @Override
    public Collection<PsiFileSystemItem> getDefaultContexts() {
      Collection<PsiFileSystemItem> systemItemCollection = super.getDefaultContexts();
      if (isAbsolutePathReference()) {
        VirtualFile vFile;
        if (SystemInfo.isWindows) {
          vFile = ManagingFS.getInstance().findRoot("", LocalFileSystem.getInstance());
        }
        else {
          vFile = LocalFileSystem.getInstance().findFileByPath("/");
        }

        if (vFile != null) {
          final PsiDirectory directory = getElement().getManager().findDirectory(vFile);
          if (directory != null) {
            systemItemCollection.add(directory);
          }
        }
      }
      return systemItemCollection;
    }
  }
}
