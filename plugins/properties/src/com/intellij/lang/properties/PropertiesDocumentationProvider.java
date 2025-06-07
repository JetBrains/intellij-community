// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class PropertiesDocumentationProvider extends AbstractDocumentationProvider {
  @Override
  public @Nullable @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof IProperty) {
      return "\"" + renderPropertyValue((IProperty)element) + "\"" + getLocationString(element);
    }
    return null;
  }

  private static @NlsSafe String getLocationString(PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null ? " [" + file.getName() + "]" : "";
  }

  private static @NotNull HtmlChunk renderPropertyValue(IProperty prop) {
    final String raw = prop.getValue();
    if (raw != null) return HtmlChunk.text(raw);

    return new HtmlBuilder()
      .append(PropertiesBundle.message("i18n.message.empty"))
      .wrapWith("i");
  }

  @Override
  public @Nls String generateDoc(final PsiElement element, final @Nullable PsiElement originalElement) {
    if (element instanceof IProperty property) {
      String text = property.getDocCommentText();

      @NonNls String info = "";
      if (text != null) {
        TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PropertiesComponent.PROPERTY_COMMENT.getTextAttributesKey()).clone();
        Color background = attributes.getBackgroundColor();
        if (background != null) {
          info +="<div bgcolor=#"+ GuiUtils.colorToHex(background)+">";
        }
        String doc = StringUtil.join(ContainerUtil.map(StringUtil.split(text, "\n"), s -> {
          final String trimHash = StringUtil.trimStart(s, PropertiesCommenter.HASH_COMMENT_PREFIX);
          final String trimExclamation = StringUtil.trimStart(trimHash, PropertiesCommenter.EXCLAMATION_COMMENT_PREFIX);
          return trimExclamation.trim();
        }), "<br>");
        final Color foreground = attributes.getForegroundColor();
        info += foreground != null ? "<font color=#" + GuiUtils.colorToHex(foreground) + ">" + doc + "</font>" : doc;
        info += "\n<br>";
        if (background != null) {
          info += "</div>";
        }
      }
      info += "\n<b>" + property.getName() + "</b>=\"" + renderPropertyValue(property) + "\"";
      info += getLocationString(element);
      return info;
    }
    return null;
  }
}