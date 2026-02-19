/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.quickfix.DownloadResourceFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

final class ExternalResourceReference implements PsiReference, LocalQuickFixProvider {
  private final XmlAttribute attribute;
  private final ExternalResourceManager resourceManager = ExternalResourceManager.getInstance();

  ExternalResourceReference(XmlAttribute attribute) {
    this.attribute = attribute;
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
    return new LocalQuickFix[] { new DownloadResourceFix(attribute.getValue()) };
  }


  @Override
  public @NotNull PsiElement getElement() {
    return attribute.getValueElement();
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    final XmlAttributeValue value = attribute.getValueElement();
    return value != null ? TextRange.from(1, value.getTextLength() - 2) : TextRange.from(0, 0);
  }

  @Override
  public @Nullable PsiElement resolve() {
    String value = attribute.getValue();
    String resourceLocation = value == null ? null : resourceManager.getResourceLocation(value, attribute.getProject());
    if (Objects.equals(resourceLocation, value)) {
      return null;
    }

    VirtualFile file;
    try {
      file = VfsUtil.findFileByURL(new URL(resourceLocation));
    }
    catch (MalformedURLException e) {
      try {
        file = VfsUtil.findFileByURL(new File(resourceLocation).toURI().toURL());
      }
      catch (MalformedURLException e1) {
        file = null;
      }
    }
    if (file != null) {
      return attribute.getManager().findFile(file);
    }
    return null;
  }

  @Override
  public @NotNull String getCanonicalText() {
    return attribute.getValue();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    attribute.setValue(newElementName);
    final XmlAttributeValue value = attribute.getValueElement();
    assert value != null;
    return value;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return element == resolve();
  }

  @Override
  public Object @NotNull [] getVariants() {
    return resourceManager.getResourceUrls(null, false);
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}
