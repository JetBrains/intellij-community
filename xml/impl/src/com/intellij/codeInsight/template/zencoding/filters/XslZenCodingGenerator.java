/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding.filters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class XslZenCodingGenerator extends XmlZenCodingGenerator {
  private final XmlZenCodingGenerator myDelegate = new XmlZenCodingGeneratorImpl();
  @NonNls private static final String SELECT_ATTR_NAME = "select";

  @Override
  public String toString(@NotNull final XmlTag tag,
                         @NotNull List<Pair<String, String>> attribute2Value,
                         final boolean hasChildren,
                         @NotNull PsiElement context) {
    for (Pair<String, String> pair : attribute2Value) {
      if (SELECT_ATTR_NAME.equals(pair.first)) {
        return myDelegate.toString(tag, attribute2Value, hasChildren, context);
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (isOurTag(tag, hasChildren)) {
          XmlAttribute attribute = tag.getAttribute(SELECT_ATTR_NAME);
          if (attribute != null) {
            attribute.delete();
          }
        }
      }
    });
    return myDelegate.toString(tag, attribute2Value, hasChildren, context);
  }

  private static boolean isOurTag(XmlTag tag, boolean hasChildren) {
    if (hasChildren) {
      String name = tag.getLocalName();
      return name.equals("with-param") || name.equals("variable");
    }
    return false;
  }

  @NotNull
  @Override
  public String buildAttributesString(@NotNull List<Pair<String, String>> attribute2value, boolean hasChildren, int numberInIteration) {
    return myDelegate.buildAttributesString(attribute2value, hasChildren, numberInIteration);
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return myDelegate.isMyContext(context);
  }

  @Override
  public String getSuffix() {
    return "xsl";
  }

  @Override
  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    VirtualFile vFile = context.getContainingFile().getVirtualFile();
    return vFile != null && "xsl".equals(vFile.getExtension());
  }
}
