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

class ExternalResourceReference implements PsiReference, LocalQuickFixProvider {
  private final XmlAttribute myAttribute;
  private final ExternalResourceManager myResourceManager = ExternalResourceManager.getInstance();

  public ExternalResourceReference(XmlAttribute attribute) {
    myAttribute = attribute;
  }

  @Nullable
  @Override
  public LocalQuickFix[] getQuickFixes() {
    return new LocalQuickFix[] { new DownloadResourceFix(myAttribute.getValue()) };
  }


  @NotNull
  public PsiElement getElement() {
    return myAttribute.getValueElement();
  }

  @NotNull
  public TextRange getRangeInElement() {
    final XmlAttributeValue value = myAttribute.getValueElement();
    return value != null ? TextRange.from(1, value.getTextLength() - 2) : TextRange.from(0, 0);
  }

  @Nullable
  public PsiElement resolve() {
    final String value = myAttribute.getValue();
    final String resourceLocation = myResourceManager.getResourceLocation(value);

    //noinspection StringEquality
    if (resourceLocation != value) {
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
        return myAttribute.getManager().findFile(file);
      }
    }
    return null;
  }

  @NotNull
  public String getCanonicalText() {
    return myAttribute.getValue();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    myAttribute.setValue(newElementName);
    final XmlAttributeValue value = myAttribute.getValueElement();
    assert value != null;
    return value;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public boolean isReferenceTo(PsiElement element) {
    return element == resolve();
  }

  @NotNull
  public Object[] getVariants() {
    return myResourceManager.getResourceUrls(null, false);
  }

  public boolean isSoft() {
    return false;
  }
}
