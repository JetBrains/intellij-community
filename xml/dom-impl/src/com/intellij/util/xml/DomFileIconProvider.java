// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.IconManager;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.NullableFunction;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.PsiFileGist;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.xml.impl.DomApplicationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;

final class DomFileIconProvider extends IconProvider {

  private final int gistVersion = DomApplicationComponent.getInstance().getCumulativeVersion(false);

  private final PsiFileGist<DomTag> DOM_FILE_DESCRIPTION =
    GistManager.getInstance().newPsiFileGist("DOM_FILE_DESCRIPTION", gistVersion, new DomFileDescriptionTagExternalizer(), new NullableFunction<>() {
      @Override
      public @Nullable DomFileIconProvider.DomTag fun(PsiFile file) {
        if (file instanceof XmlFile) {
          return findDomFileDescription((XmlFile)file);
        }
        return null;
      }

      private static @Nullable DomFileIconProvider.DomTag findDomFileDescription(@NotNull XmlFile file) {
        PsiFile originalFile = file.getOriginalFile(); // DOM often needs proper module and origin
        if (!(originalFile instanceof XmlFile)) return null;

        return AstLoadingFilter.forceAllowTreeLoading(originalFile, () -> {
          DomFileDescription<?> description =
            DomManager.getDomManager(originalFile.getProject()).getDomFileDescription((XmlFile)originalFile);

          if (description != null) {
            return new DomTag(
              description.getRootTagName(),
              description.getRootElementClass().getName()
            );
          }
          return null;
        });
      }
    });

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof XmlFile xmlFile) {
      DomTag tag = DOM_FILE_DESCRIPTION.getFileData(xmlFile);
      if (tag == null) return null;

      DomFileDescription<?> restored = restoreDomFileDescription(tag);
      if (restored == null) {
        return null;
      }

      Icon fileIcon = restored.getFileIcon(xmlFile, flags);
      if (fileIcon != null) {
        return IconManager.getInstance().createLayeredIcon(element, fileIcon, ElementBase.transformFlags(element, flags));
      }
    }
    return null;
  }

  private static @Nullable DomFileDescription<?> restoreDomFileDescription(@NotNull DomFileIconProvider.DomTag tag) {
    Set<DomFileDescription<?>> descriptionsForTag = DomApplicationComponent.getInstance().getFileDescriptions(tag.rootTagName());

    for (DomFileDescription<?> description : descriptionsForTag) {
      if (tag.rootElementClass().equals(description.getRootElementClass().getName())) {
        return description;
      }
    }
    return null;
  }

  private static class DomFileDescriptionTagExternalizer implements DataExternalizer<DomTag> {
    @Override
    public void save(@NotNull DataOutput out, DomTag value) throws IOException {
      out.writeUTF(value.rootTagName());
      out.writeUTF(value.rootElementClass());
    }

    @Override
    public DomTag read(@NotNull DataInput in) throws IOException {
      var rootTagName = in.readUTF();
      var rootElementClass = in.readUTF();
      return new DomTag(rootTagName, rootElementClass);
    }
  }

  private record DomTag(@NotNull String rootTagName,
                        @NotNull String rootElementClass) {
  }
}