/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class HtmlTagTreeHighlightingPass extends TextEditorHighlightingPass {
  private static final HighlightInfoType TYPE = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, TextAttributesKey
    .createTextAttributesKey("TAG_TREE_HIGHLIGHTING_KEY"));

  private final XmlFile myFile;
  private final Editor myEditor;

  private final List<Pair<TextRange, TextRange>> myPairsToHighlight = new ArrayList<Pair<TextRange, TextRange>>();

  public HtmlTagTreeHighlightingPass(@NotNull XmlFile file, @NotNull Editor editor) {
    super(file.getProject(), editor.getDocument(), true);
    myFile = file;
    myEditor = editor;
  }

  @Override
  public void doCollectInformation(ProgressIndicator progress) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    if (!WebEditorOptions.getInstance().isTagTreeHighlightingEnabled()) {
      return;
    }

    final int offset = myEditor.getCaretModel().getOffset();

    PsiElement element = null;

    final FileViewProvider viewProvider = myFile.getViewProvider();
    for (Language language : viewProvider.getLanguages()) {
      if (language instanceof XMLLanguage) {
        element = viewProvider.findElementAt(offset, language);
        if (element != null) {
          break;
        }
      }
    }

    if (element == null) {
      return;
    }

    while (element != null) {
      if (element instanceof XmlTag) {
        myPairsToHighlight.add(getTagRanges((XmlTag)element));
      }
      element = element.getParent();
    }
  }

  @Nullable
  private static Pair<TextRange, TextRange> getTagRanges(XmlTag tag) {
    final ASTNode tagNode = tag.getNode();
    return new Pair<TextRange, TextRange>(getStartTagRange(tagNode), getEndTagRange(tagNode));
  }

  @Nullable
  private static TextRange getStartTagRange(ASTNode tagNode) {
    final ASTNode startTagStart = XmlChildRole.START_TAG_START_FINDER.findChild(tagNode);
    if (startTagStart == null) {
      return null;
    }

    ASTNode tagName = startTagStart.getTreeNext();
    if (tagName == null || tagName.getElementType() != XmlTokenType.XML_NAME) {
      return null;
    }

    ASTNode next = tagName.getTreeNext();
    if (next != null && next.getElementType() == XmlTokenType.XML_TAG_END) {
      tagName = next;
    }

    return new TextRange(startTagStart.getStartOffset(), tagName.getTextRange().getEndOffset());
  }

  @Nullable
  private static TextRange getEndTagRange(ASTNode tagNode) {
    final ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tagNode);
    if (endTagStart == null) {
      return null;
    }

    ASTNode endTagEnd = endTagStart;
    while (endTagEnd != null && endTagEnd.getElementType() != XmlTokenType.XML_TAG_END) {
      endTagEnd = endTagEnd.getTreeNext();
    }

    if (endTagEnd == null) {
      return null;
    }

    return new TextRange(endTagStart.getStartOffset(), endTagEnd.getTextRange().getEndOffset());
  }

  @Override
  public void doApplyInformationToEditor() {
    final List<HighlightInfo> infos = getHighlights();
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());
  }

  public List<HighlightInfo> getHighlights() {
    final int count = myPairsToHighlight.size();
    final List<HighlightInfo> highlightInfos = new ArrayList<HighlightInfo>(count * 2);

    final Color[] colors = getColors();

    assert colors.length > 0;

    int colorIndex = 0;

    for (Pair<TextRange, TextRange> pair : myPairsToHighlight) {
      if (pair == null || (pair.first == null && pair.second == null)) {
        continue;
      }

      if (colorIndex >= colors.length) {
        colorIndex = 0;
      }
      Color color = colors[colorIndex];

      if (pair.first != null) {
        highlightInfos.add(createHighlightInfo(color, pair.first));
      }

      if (pair.second != null) {
        highlightInfos.add(createHighlightInfo(color, pair.second));
      }

      colorIndex++;
    }

    return highlightInfos;
  }

  @NotNull
  private static HighlightInfo createHighlightInfo(Color color, @NotNull TextRange range) {
    return new HighlightInfo(new TextAttributes(null, color, null, null, Font.PLAIN), TYPE, range.getStartOffset(),
                             range.getEndOffset(), null, null, HighlightSeverity.INFORMATION, false, null, false);
  }

  private Color[] getColors() {
    final ColorKey[] colorKeys = HtmlTagTreeHighlightingColors.COLOR_KEYS;
    final Color[] colors = new Color[colorKeys.length];
    final Color tagBackground = myEditor instanceof EditorEx ? ((EditorEx)myEditor).getBackgroundColor() : null;

    // todo: make configurable
    final double transparency = 0.1;

    final EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();

    for (int i = 0; i < colors.length; i++) {
      Color color = colorsScheme.getColor(colorKeys[i]);

      if (tagBackground == null) {
        colors[i] = color;
      }
      else {
        int r = (int)(tagBackground.getRed() * (1 - transparency) + color.getRed() * transparency);
        int g = (int)(tagBackground.getGreen() * (1 - transparency) + color.getGreen() * transparency);
        int b = (int)(tagBackground.getBlue() * (1 - transparency) + color.getBlue() * transparency);

        colors[i] = new Color(r, g, b);
      }
    }

    return colors;
  }
}
