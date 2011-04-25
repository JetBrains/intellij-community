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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import com.intellij.xml.breadcrumbs.BreadcrumbsXmlWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlTagTreeHighlightingPass extends TextEditorHighlightingPass {
  private static final Key<List<RangeHighlighter>> TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY = Key.create("TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY");

  private static final HighlightInfoType TYPE = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, TextAttributesKey
    .createTextAttributesKey("TAG_TREE_HIGHLIGHTING_KEY"));

  private final XmlFile myFile;
  private final Editor myEditor;
  private final BreadcrumbsInfoProvider myInfoProvider;

  private final List<Pair<TextRange, TextRange>> myPairsToHighlight = new ArrayList<Pair<TextRange, TextRange>>();

  public XmlTagTreeHighlightingPass(@NotNull XmlFile file, @NotNull Editor editor) {
    super(file.getProject(), editor.getDocument(), true);
    myFile = file;
    myEditor = editor;
    final FileViewProvider viewProvider = PsiManager.getInstance(file.getProject()).findViewProvider(file.getVirtualFile());
    myInfoProvider = BreadcrumbsXmlWrapper.findInfoProvider(viewProvider);
  }

  @Override
  public void doCollectInformation(ProgressIndicator progress) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    if (!WebEditorOptions.getInstance().isTagTreeHighlightingEnabled()) {
      return;
    }

    final PsiElement[] elements =
      BreadcrumbsXmlWrapper.getLinePsiElements(myEditor.getCaretModel().getOffset(), myFile.getVirtualFile(), myProject, myInfoProvider);

    if (elements == null || elements.length == 0) {
      return;
    }

    if (!XmlTagTreeHighlightingUtil.containsTagsWithSameName(elements)) {
      return;
    }

    for (int i = elements.length - 1; i >= 0; i--) {
      if (elements[i] instanceof XmlTag) {
        myPairsToHighlight.add(getTagRanges((XmlTag)elements[i]));
      }
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
    clearLineMarkers(myEditor);

    final int count = myPairsToHighlight.size();
    final List<HighlightInfo> highlightInfos = new ArrayList<HighlightInfo>(count * 2);
    final MarkupModel markupModel = myEditor.getMarkupModel();

    final Color[] baseColors = XmlTagTreeHighlightingUtil.getBaseColors();
    final Color[] colorsForEditor = toColorsForEditor(baseColors);
    final Color[] colorsForLineMarkers = toColorsForLineMarkers(baseColors);

    final List<RangeHighlighter> newHighlighters = new ArrayList<RangeHighlighter>();

    assert colorsForEditor.length > 0;

    for (int i = 0; i < count && i < baseColors.length; i++) {
      Pair<TextRange, TextRange> pair = myPairsToHighlight.get(i);

      if (pair == null || (pair.first == null && pair.second == null)) {
        continue;
      }

      Color color = colorsForEditor[i];

      if (pair.first != null) {
        highlightInfos.add(createHighlightInfo(color, pair.first));
      }

      if (pair.second != null) {
        highlightInfos.add(createHighlightInfo(color, pair.second));
      }

      final int start = pair.first != null ? pair.first.getStartOffset() : pair.second.getStartOffset();
      final int end = pair.second != null ? pair.second.getEndOffset() : pair.first.getEndOffset();

      final RangeHighlighter highlighter = createHighlighter(markupModel, new TextRange(start, end), colorsForLineMarkers[i]);
      newHighlighters.add(highlighter);
    }

    myEditor.putUserData(TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY, newHighlighters);

    return highlightInfos;
  }

  private static void clearLineMarkers(Editor editor) {
    final List<RangeHighlighter> oldHighlighters = editor.getUserData(TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY);

    if (oldHighlighters != null) {
      final MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();

      for (RangeHighlighter highlighter : oldHighlighters) {
        if (markupModel.containsHighlighter(highlighter)) {
          highlighter.dispose();
        }
      }
      editor.putUserData(TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY, null);
    }
  }

  @NotNull
  private static HighlightInfo createHighlightInfo(Color color, @NotNull TextRange range) {
    return new HighlightInfo(new TextAttributes(null, color, null, null, Font.PLAIN), TYPE, range.getStartOffset(),
                             range.getEndOffset(), null, null, HighlightSeverity.INFORMATION, false, null, false);
  }

  @NotNull
  private static RangeHighlighter createHighlighter(final MarkupModel mm, final @NotNull TextRange range, final Color color) {
    final RangeHighlighter highlighter =
      mm.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), 0, null, HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setLineMarkerRenderer(new LineMarkerRenderer() {
      public void paint(Editor editor, Graphics g, Rectangle r) {
        int height = r.height + editor.getLineHeight();
        g.setColor(color);
        g.fillRect(r.x, r.y, 2, height);
      }
    });
    return highlighter;
  }


  private static Color[] toColorsForLineMarkers(Color[] baseColors) {
    final Color[] colors = new Color[baseColors.length];
    final Color tagBackground = new Color(239, 239, 239);
    final double transparency = 0.4;
    final double factor = 0.8;

    for (int i = 0; i < colors.length; i++) {
      final Color color = baseColors[i];

      int r = (int)(color.getRed() * factor);
      int g = (int)(color.getGreen() * factor);
      int b = (int)(color.getBlue() * factor);

      r = (int)(tagBackground.getRed() * (1 - transparency) + r * transparency);
      g = (int)(tagBackground.getGreen() * (1 - transparency) + g * transparency);
      b = (int)(tagBackground.getBlue() * (1 - transparency) + b * transparency);

      colors[i] = new Color(r, g, b);
    }

    return colors;
  }

  private Color[] toColorsForEditor(Color[] baseColors) {
    final Color tagBackground = myEditor instanceof EditorEx ? ((EditorEx)myEditor).getBackgroundColor() : null;

    if (tagBackground == null) {
      return baseColors;
    }

    final Color[] resultColors = new Color[baseColors.length];
    // todo: make configurable
    final double transparency = WebEditorOptions.getInstance().getTagTreeHighlightingOpacity() * 0.01;

    for (int i = 0; i < resultColors.length; i++) {
      final Color color = baseColors[i];

      final Color color1 = XmlTagTreeHighlightingUtil.makeTransparent(color, tagBackground, transparency);
      resultColors[i] = color1;
    }

    return resultColors;
  }

  public static void clearHighlightingAndLineMarkers(final Editor editor, @NotNull Project project) {
    final MarkupModel markupModel = editor.getDocument().getMarkupModel(project);

    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      Object tooltip = highlighter.getErrorStripeTooltip();

      if (!(tooltip instanceof HighlightInfo)) {
        continue;
      }

      if (((HighlightInfo)tooltip).type == TYPE) {
        highlighter.dispose();
      }
    }

    clearLineMarkers(editor);
  }
}
