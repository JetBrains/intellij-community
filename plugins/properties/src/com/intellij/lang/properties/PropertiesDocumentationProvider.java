/*
 * @author max
 */
package com.intellij.lang.properties;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class PropertiesDocumentationProvider extends QuickDocumentationProvider {
  @Nullable
  public String getQuickNavigateInfo(PsiElement element) {
    if (element instanceof Property) {
      @NonNls String info = "\n\"" + ((Property)element).getValue() + "\"";
      PsiFile file = element.getContainingFile();
      if (file != null) {
        info += " [" + file.getName() + "]";
      }
      return info;
    }
    return null;
  }

  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    if (element instanceof Property) {
      Property property = (Property)element;
      String text = property.getDocCommentText();

      @NonNls String info = "";
      if (text != null) {
        TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_COMMENT).clone();
        Color background = attributes.getBackgroundColor();
        if (background != null) {
          info +="<div bgcolor=#"+ GuiUtils.colorToHex(background)+">";
        }
        String doc = StringUtil.join(StringUtil.split(text, "\n"), "<br>");
        info += "<font color=#" + GuiUtils.colorToHex(attributes.getForegroundColor()) + ">" + doc + "</font>\n<br>";
        if (background != null) {
          info += "</div>";
        }
      }
      info += "\n<b>" + property.getName() + "</b>=\"" + ((Property)element).getValue() + "\"";
      PsiFile file = element.getContainingFile();
      if (file != null) {
        info += " [" + file.getName() + "]";
      }
      return info;
    }
    return null;
  }
}