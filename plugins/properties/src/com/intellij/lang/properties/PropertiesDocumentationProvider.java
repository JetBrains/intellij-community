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

/*
 * @author max
 */
package com.intellij.lang.properties;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class PropertiesDocumentationProvider extends AbstractDocumentationProvider {
  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof IProperty) {
      return "\"" + renderPropertyValue((IProperty)element) + "\"" + getLocationString(element);
    }
    return null;
  }

  private static String getLocationString(PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null ? " [" + file.getName() + "]" : "";
  }

  @NotNull
  private static String renderPropertyValue(IProperty prop) {
    String raw = prop.getValue();
    if (raw == null) {
      return "<i>empty</i>";
    }
    return StringUtil.escapeXml(raw);
  }

  public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    if (element instanceof IProperty) {
      IProperty property = (IProperty)element;
      String text = property.getDocCommentText();

      @NonNls String info = "";
      if (text != null) {
        TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_COMMENT).clone();
        Color background = attributes.getBackgroundColor();
        if (background != null) {
          info +="<div bgcolor=#"+ GuiUtils.colorToHex(background)+">";
        }
        String doc = StringUtil.join(ContainerUtil.map(StringUtil.split(text, "\n"), new Function<String, String>() {
          @Override
          public String fun(String s) {
            return StringUtil.trimStart(StringUtil.trimStart(s, "#"), "!").trim();
          }
        }), "<br>");
        info += "<font color=#" + GuiUtils.colorToHex(attributes.getForegroundColor()) + ">" + doc + "</font>\n<br>";
        if (background != null) {
          info += "</div>";
        }
      }
      info += "\n<b>" + property.getName() + "</b>=\"" + renderPropertyValue(((IProperty)element)) + "\"";
      info += getLocationString(element);
      return info;
    }
    return null;
  }
}