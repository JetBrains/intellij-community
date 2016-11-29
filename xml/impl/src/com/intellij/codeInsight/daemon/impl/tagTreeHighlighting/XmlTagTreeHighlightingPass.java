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
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.ui.Gray;
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

  private static final TextAttributesKey TAG_TREE_HIGHLIGHTING_KEY = TextAttributesKey.createTextAttributesKey("TAG_TREE_HIGHLIGHTING_KEY");
  private static final HighlightInfoType TYPE = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION,
                                                                                            TAG_TREE_HIGHLIGHTING_KEY);

  private final PsiFile myFile;
  private final EditorEx myEditor;
  private final BreadcrumbsInfoProvider myInfoProvider;

  private final List<Pair<TextRange, TextRange>> myPairsToHighlight = new ArrayList<>();

  public XmlTagTreeHighlightingPass(@NotNull PsiFile file, @NotNull EditorEx editor) {
    super(file.getProject(), editor.getDocument(), true);
    myFile = file;
    myEditor = editor;
    final FileViewProvider viewProvider = file.getManager().findViewProvider(file.getVirtualFile());
    myInfoProvider = BreadcrumbsXmlWrapper.findInfoProvider(viewProvider);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    if (!WebEditorOptions.getInstance().isTagTreeHighlightingEnabled()) {
      return;
    }

    final int offset = myEditor.getCaretModel().getOffset();
    PsiElement[] elements = BreadcrumbsXmlWrapper.getLinePsiElements(offset,
                                                                     myFile.getVirtualFile(),
                                                                     myProject, myInfoProvider);

    if (elements == null || elements.length == 0 || !XmlTagTreeHighlightingUtil.containsTagsWithSameName(elements)) {
      elements = PsiElement.EMPTY_ARRAY;
      final FileViewProvider provider = myFile.getViewProvider();
      for (Language language : provider.getLanguages()) {
        PsiElement element = provider.findElementAt(offset, language);
        if (!isTagStartOrEnd(element)) {
          element = null;
        }
        if (element == null && offset > 0) {
          element = provider.findElementAt(offset - 1, language);
          if (!isTagStartOrEnd(element)) element = null;
        }

        final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (tag != null) {
          elements = new PsiElement[] {tag};
        }
      }
    }

    for (int i = elements.length - 1; i >= 0; i--) {
      if (elements[i] instanceof XmlTag) {
        myPairsToHighlight.add(getTagRanges((XmlTag)elements[i]));
      }
    }
  }

  private static boolean isTagStartOrEnd(@Nullable PsiElement element) {
    if (element == null) return false;
    final IElementType type = element.getNode().getElementType();
    if (type == XmlTokenType.XML_NAME) return isTagStartOrEnd(element.getNextSibling()) || isTagStartOrEnd(element.getPrevSibling());
    return type == XmlTokenType.XML_START_TAG_START || type == XmlTokenType.XML_END_TAG_START || type == XmlTokenType.XML_TAG_END;
  }

  @NotNull
  private static Pair<TextRange, TextRange> getTagRanges(XmlTag tag) {
    final ASTNode tagNode = tag.getNode();
    return Pair.create(getStartTagRange(tagNode), getEndTagRange(tagNode));
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
    if (myDocument != null) {
      final List<HighlightInfo> infos = getHighlights();
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());
    }
  }

  public List<HighlightInfo> getHighlights() {
    clearLineMarkers(myEditor);

    final int count = myPairsToHighlight.size();
    final List<HighlightInfo> highlightInfos = new ArrayList<>(count * 2);
    final MarkupModel markupModel = myEditor.getMarkupModel();

    final Color[] baseColors = XmlTagTreeHighlightingUtil.getBaseColors();
    final Color[] colorsForEditor = count > 1 ? toColorsForEditor(baseColors) :
                                    new Color[] {myEditor.getColorsScheme().getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES).getBackgroundColor()};
    final Color[] colorsForLineMarkers = toColorsForLineMarkers(baseColors);

    final List<RangeHighlighter> newHighlighters = new ArrayList<>();

    assert colorsForEditor.length > 0;

    for (int i = 0; i < count && i < baseColors.length; i++) {
      Pair<TextRange, TextRange> pair = myPairsToHighlight.get(i);

      if (pair.first == null && pair.second == null) {
        continue;
      }

      Color color = colorsForEditor[i];

      if (color == null) {
        continue;
      }

      if (pair.first != null && !pair.first.isEmpty()) {
        highlightInfos.add(createHighlightInfo(color, pair.first));
      }

      if (pair.second != null && !pair.second.isEmpty()) {
        highlightInfos.add(createHighlightInfo(color, pair.second));
      }

      final int start = pair.first != null ? pair.first.getStartOffset() : pair.second.getStartOffset();
      final int end = pair.second != null ? pair.second.getEndOffset() : pair.first.getEndOffset();

      final Color lineMarkerColor = colorsForLineMarkers[i];
      if (count > 1 && lineMarkerColor != null && start != end) {
        final RangeHighlighter highlighter = createHighlighter(markupModel, new TextRange(start, end), lineMarkerColor);
        newHighlighters.add(highlighter);
      }
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
    TextAttributes attributes = new TextAttributes(null, color, null, null, Font.PLAIN);
    return HighlightInfo.newHighlightInfo(TYPE).range(range).textAttributes(attributes).severity(HighlightSeverity.INFORMATION).createUnconditionally();
  }

  @NotNull
  private static RangeHighlighter createHighlighter(final MarkupModel mm, @NotNull final TextRange range, final Color color) {
    final RangeHighlighter highlighter =
      mm.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), 0, null, HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setLineMarkerRenderer(new LineMarkerRenderer() {
      @Override
      public void paint(Editor editor, Graphics g, Rectangle r) {
        g.setColor(color);
        g.fillRect(r.x - 1, r.y, 2, r.height);
      }
    });
    return highlighter;
  }


  private static Color[] toColorsForLineMarkers(Color[] baseColors) {
    final Color[] colors = new Color[baseColors.length];
    final Color tagBackground = Gray._239;
    final double transparency = 0.4;
    final double factor = 0.8;

    for (int i = 0; i < colors.length; i++) {
      final Color color = baseColors[i];

      if (color == null) {
        colors[i] = null;
        continue;
      }

      int r = (int)(color.getRed() * factor);
      int g = (int)(color.getGreen() * factor);
      int b = (int)(color.getBlue() * factor);

      r = (int)(tagBackground.getRed() * (1 - transparency) + r * transparency);
      g = (int)(tagBackground.getGreen() * (1 - transparency) + g * transparency);
      b = (int)(tagBackground.getBlue() * (1 - transparency) + b * transparency);

      //noinspection UseJBColor
      colors[i] = new Color(r, g, b);
    }

    return colors;
  }

  private Color[] toColorsForEditor(Color[] baseColors) {
    final Color tagBackground = myEditor.getBackgroundColor();

    if (tagBackground == null) {
      return baseColors;
    }

    final Color[] resultColors = new Color[baseColors.length];
    // todo: make configurable
    final double transparency = WebEditorOptions.getInstance().getTagTreeHighlightingOpacity() * 0.01;

    for (int i = 0; i < resultColors.length; i++) {
      final Color color = baseColors[i];

      final Color color1 = color != null
                           ? XmlTagTreeHighlightingUtil.makeTransparent(color, tagBackground, transparency)
                           : null;
      resultColors[i] = color1;
    }

    return resultColors;
  }

  public static void clearHighlightingAndLineMarkers(final Editor editor, @NotNull Project project) {
    final MarkupModel markupModel = DocumentMarkupModel.forDocument(editor.getDocument(), project, true);

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
