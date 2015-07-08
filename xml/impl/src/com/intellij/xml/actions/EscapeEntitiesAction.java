/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xml.actions;

import com.intellij.codeInsight.actions.SimpleCodeInsightAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ParameterizedCachedValueImpl;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.util.XmlUtil;
import io.netty.util.collection.IntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class EscapeEntitiesAction extends SimpleCodeInsightAction {
  private static ParameterizedCachedValueImpl<IntObjectHashMap<String>, PsiFile> ESCAPES = new ParameterizedCachedValueImpl<IntObjectHashMap<String>, PsiFile>(
    new ParameterizedCachedValueProvider<IntObjectHashMap<String>, PsiFile>() {
      @Nullable
      @Override
      public CachedValueProvider.Result<IntObjectHashMap<String>> compute(PsiFile param) {
        final XmlFile file = XmlUtil.findXmlFile(param, Html5SchemaProvider.getCharsDtdLocation());
        assert file != null;
        final IntObjectHashMap<String> result = new IntObjectHashMap<String>();
        XmlUtil.processXmlElements(file, new PsiElementProcessor() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            if (element instanceof XmlEntityDecl) {
              final String value = ((XmlEntityDecl)element).getValueElement().getValue();
              final Integer key = Integer.valueOf(value.substring(2, value.length() - 1));
              if (!result.containsKey(key)) {
                result.put(key, ((XmlEntityDecl)element).getName());
              }
            }
            return true;
          }
        }, true);
        return new CachedValueProvider.Result<IntObjectHashMap<String>>(result, ModificationTracker.NEVER_CHANGED);
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
      if (element != null && element.getNode().getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
        if (c == '<' || c == '>' || c == '&' || c == '"' || c == '\'' || c > 0xff) {
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
  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return ApplicationManager.getApplication().isInternal() && file instanceof XmlFile;
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
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.replaceString(start, end, newText);
          }
        });
      }
    }
  }
}
