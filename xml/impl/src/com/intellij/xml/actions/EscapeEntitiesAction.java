// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.util.XmlUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

final class EscapeEntitiesAction extends BaseCodeInsightAction implements CodeInsightActionHandler {
  private static String escape(XmlFile file, Int2ObjectMap<String> map, String text, int start) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      final PsiElement element = file.findElementAt(start + i);
      if (element != null && isCharacterElement(element)) {
        if (c == '<' || c == '>' || c == '&' || c == '"' || c == '\'' || c > 0x7f) {
          final String escape = map.get(c);
          if (escape != null) {
            result.append("&").append(escape).append(";");
            continue;
          }
        }
      }
      result.append(c);
    }
    return result.toString();
  }

  private static @NotNull Int2ObjectMap<String> computeMap(XmlFile xmlFile) {
    final XmlFile file = XmlUtil.findXmlFile(xmlFile, Html5SchemaProvider.getCharsDtdLocation());
    assert file != null;

    Int2ObjectMap<String> result = new Int2ObjectOpenHashMap<>();
    XmlUtil.processXmlElements(file, element -> {
      if (element instanceof XmlEntityDecl) {
        final String value = ((XmlEntityDecl)element).getValueElement().getValue();
        final int key = Integer.parseInt(value.substring(2, value.length() - 1));
        if (!result.containsKey(key)) {
          result.put(key, ((XmlEntityDecl)element).getName());
        }
      }
      return true;
    }, true);
    return result;
  }

  private static boolean isCharacterElement(PsiElement element) {
    final IElementType type = element.getNode().getElementType();
    if (type == XmlTokenType.XML_DATA_CHARACTERS) return true;
    if (type == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      if (element.getParent().getParent() instanceof XmlAttribute) return true;
    }
    if (type == XmlTokenType.XML_BAD_CHARACTER) return true;
    if (type == XmlTokenType.XML_START_TAG_START) {
      if (element.getNextSibling() instanceof PsiErrorElement) return true;
      if (element.getParent() instanceof PsiErrorElement) return true;
    }
    return false;
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return psiFile instanceof XmlFile;
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return this;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    int[] starts = editor.getSelectionModel().getBlockSelectionStarts();
    int[] ends = editor.getSelectionModel().getBlockSelectionEnds();
    final Document document = editor.getDocument();
    XmlFile xmlFile = (XmlFile)psiFile;
    Int2ObjectMap<String> map = computeMap(xmlFile);
    for (int i = starts.length - 1; i >= 0; i--) {
      final int start = starts[i];
      final int end = ends[i];
      String oldText = document.getText(new TextRange(start, end));
      final String newText = escape(xmlFile, map, oldText, start);
      if (!oldText.equals(newText)) {
        document.replaceString(start, end, newText);
      }
    }
  }
}
