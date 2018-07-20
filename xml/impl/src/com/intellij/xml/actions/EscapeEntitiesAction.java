// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ParameterizedCachedValueImpl;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class EscapeEntitiesAction extends BaseCodeInsightAction implements CodeInsightActionHandler {
  private static final ParameterizedCachedValueImpl<TIntObjectHashMap<String>, PsiFile> ESCAPES = new ParameterizedCachedValueImpl<TIntObjectHashMap<String>, PsiFile>(
    new ParameterizedCachedValueProvider<TIntObjectHashMap<String>, PsiFile>() {
      @Nullable
      @Override
      public CachedValueProvider.Result<TIntObjectHashMap<String>> compute(PsiFile param) {
        final XmlFile file = XmlUtil.findXmlFile(param, Html5SchemaProvider.getCharsDtdLocation());
        assert file != null;
        final TIntObjectHashMap<String> result = new TIntObjectHashMap<>();
        XmlUtil.processXmlElements(file, new PsiElementProcessor() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            if (element instanceof XmlEntityDecl) {
              final String value = ((XmlEntityDecl)element).getValueElement().getValue();
              final int key = Integer.parseInt(value.substring(2, value.length() - 1));
              if (!result.containsKey(key)) {
                result.put(key, ((XmlEntityDecl)element).getName());
              }
            }
            return true;
          }
        }, true);
        return new CachedValueProvider.Result<>(result, ModificationTracker.NEVER_CHANGED);
      }
    }) {
    @Override
    public boolean isFromMyProject(Project project) {
      return true;
    }
  };

  private static String escape(XmlFile file, String text, int start) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      final PsiElement element = file.findElementAt(start + i);
      if (element != null && isCharacterElement(element)) {
        if (c == '<' || c == '>' || c == '&' || c == '"' || c == '\'' || c > 0x7f) {
          final String escape = ESCAPES.getValue(file).get(c);
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
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return file instanceof XmlFile;
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    int[] starts = editor.getSelectionModel().getBlockSelectionStarts();
    int[] ends = editor.getSelectionModel().getBlockSelectionEnds();
    final Document document = editor.getDocument();
    for (int i = starts.length - 1; i >= 0; i--) {
      final int start = starts[i];
      final int end = ends[i];
      String oldText = document.getText(new TextRange(start, end));
      final String newText = escape((XmlFile)file, oldText, start);
      if (!oldText.equals(newText)) {
        document.replaceString(start, end, newText);
      }
    }
  }
}
