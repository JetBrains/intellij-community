// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.breadcrumbs.BreadcrumbsUtilEx;
import com.intellij.xml.breadcrumbs.PsiFileBreadcrumbsCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class XmlTagTreeHighlightingPass extends TextEditorHighlightingPass {
  private static final Key<List<RangeHighlighter>> TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY = Key.create("TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY");

  public static final TextAttributesKey TAG_TREE_HIGHLIGHTING_KEY = TextAttributesKey.createTextAttributesKey("TAG_TREE_HIGHLIGHTING_KEY");
  private static final HighlightInfoType TYPE = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION,
                                                                                            TAG_TREE_HIGHLIGHTING_KEY);

  private final PsiFile myFile;
  private final EditorEx myEditor;
  private final BreadcrumbsProvider myInfoProvider;

  private final List<Pair<TextRange, TextRange>> myPairsToHighlight = new ArrayList<>();

  XmlTagTreeHighlightingPass(@NotNull PsiFile file, @NotNull EditorEx editor) {
    super(file.getProject(), editor.getDocument(), true);
    myFile = file;
    myEditor = editor;
    FileViewProvider viewProvider = file.getManager().findViewProvider(file.getVirtualFile());
    myInfoProvider = BreadcrumbsUtilEx.findProvider(false, viewProvider);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    if (!WebEditorOptions.getInstance().isTagTreeHighlightingEnabled()) {
      return;
    }

    int offset = myEditor.getCaretModel().getOffset();
    PsiElement[] elements =
      PsiFileBreadcrumbsCollector.getLinePsiElements(myEditor.getDocument(), offset, myFile.getVirtualFile(), myProject, myInfoProvider);

    if (elements == null || elements.length == 0 || !XmlTagTreeHighlightingUtil.containsTagsWithSameName(elements)) {
      elements = PsiElement.EMPTY_ARRAY;
      FileViewProvider provider = myFile.getViewProvider();
      for (Language language : provider.getLanguages()) {
        PsiElement element = provider.findElementAt(offset, language);
        if (!isTagStartOrEnd(element)) {
          element = null;
        }
        if (element == null && offset > 0) {
          element = provider.findElementAt(offset - 1, language);
          if (!isTagStartOrEnd(element)) element = null;
        }

        XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
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
    IElementType type = element.getNode().getElementType();
    if (type == XmlTokenType.XML_NAME || type == XmlTokenType.XML_TAG_NAME) return isTagStartOrEnd(element.getNextSibling()) || isTagStartOrEnd(element.getPrevSibling());
    return type == XmlTokenType.XML_START_TAG_START || type == XmlTokenType.XML_END_TAG_START || type == XmlTokenType.XML_TAG_END;
  }

  @NotNull
  private static Pair<TextRange, TextRange> getTagRanges(XmlTag tag) {
    ASTNode tagNode = tag.getNode();
    return Pair.create(getStartTagRange(tagNode), getEndTagRange(tagNode));
  }

  @Nullable
  private static TextRange getStartTagRange(ASTNode tagNode) {
    ASTNode startTagStart = XmlChildRole.START_TAG_START_FINDER.findChild(tagNode);
    if (startTagStart == null) {
      return null;
    }

    ASTNode tagName = TemplateLanguageUtil.getSameLanguageTreeNext(startTagStart);
    if (tagName == null || (tagName.getElementType() != XmlTokenType.XML_NAME && tagName.getElementType() != XmlTokenType.XML_TAG_NAME)) {
      return null;
    }

    ASTNode next = TemplateLanguageUtil.getSameLanguageTreeNext(tagName);
    if (next != null && next.getElementType() == XmlTokenType.XML_TAG_END) {
      tagName = next;
    }

    return new TextRange(startTagStart.getStartOffset(), tagName.getTextRange().getEndOffset());
  }

  @Nullable
  private static TextRange getEndTagRange(ASTNode tagNode) {
    ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tagNode);
    if (endTagStart == null) {
      return null;
    }

    ASTNode endTagEnd = endTagStart;
    while (endTagEnd != null && endTagEnd.getElementType() != XmlTokenType.XML_TAG_END) {
      endTagEnd = TemplateLanguageUtil.getSameLanguageTreeNext(endTagEnd);
    }

    if (endTagEnd == null) {
      return null;
    }

    return new TextRange(endTagStart.getStartOffset(), endTagEnd.getTextRange().getEndOffset());
  }

  @Override
  public void doApplyInformationToEditor() {
    List<HighlightInfo> infos = getHighlights();
    UpdateHighlightersUtil.setHighlightersToSingleEditor(myProject, myEditor, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());
  }

  public List<HighlightInfo> getHighlights() {
    clearLineMarkers(myEditor);

    int count = myPairsToHighlight.size();
    List<HighlightInfo> highlightInfos = new ArrayList<>(count * 2);
    MarkupModel markupModel = myEditor.getMarkupModel();

    Color[] baseColors = XmlTagTreeHighlightingUtil.getBaseColors();
    Color[] colorsForEditor = count > 1 ? toColorsForEditor(baseColors) :
                                    new Color[] {myEditor.getColorsScheme().getAttributes(XmlHighlighterColors.MATCHED_TAG_NAME).getBackgroundColor()};
    Color[] colorsForLineMarkers = toColorsForLineMarkers(baseColors);

    List<RangeHighlighter> newHighlighters = new ArrayList<>();

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

      int start = pair.first != null ? pair.first.getStartOffset() : pair.second.getStartOffset();
      int end = pair.second != null ? pair.second.getEndOffset() : pair.first.getEndOffset();

      Color lineMarkerColor = colorsForLineMarkers[i];
      if (count > 1 && lineMarkerColor != null && start != end) {
        RangeHighlighter highlighter = createHighlighter(markupModel, new TextRange(start, end), lineMarkerColor);
        newHighlighters.add(highlighter);
      }
    }

    myEditor.putUserData(TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY, newHighlighters);

    return highlightInfos;
  }

  private static void clearLineMarkers(Editor editor) {
    List<RangeHighlighter> oldHighlighters = editor.getUserData(TAG_TREE_HIGHLIGHTERS_IN_EDITOR_KEY);

    if (oldHighlighters != null) {
      MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();

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
    return HighlightInfo.newHighlightInfo(TYPE).range(range).textAttributes(attributes)
      .severity(HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY).createUnconditionally();
  }

  @NotNull
  private static RangeHighlighter createHighlighter(MarkupModel mm, @NotNull TextRange range, Color color) {
    RangeHighlighter highlighter =
      mm.addRangeHighlighter(null, range.getStartOffset(), range.getEndOffset(), 0, HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setLineMarkerRenderer((__, g, r) -> {
      g.setColor(color);
      g.fillRect(r.x - 1, r.y, 2, r.height);
    });
    return highlighter;
  }

  static Color toLineMarkerColor(int gray, Color color) {
    //noinspection UseJBColor
    return color == null ? null : new Color(
      toLineMarkerColor(gray, color.getRed()),
      toLineMarkerColor(gray, color.getGreen()),
      toLineMarkerColor(gray, color.getBlue()));
  }

  private static int toLineMarkerColor(int gray, int color) {
    int value = (int)(gray * 0.6 + 0.32 * color);
    return value < 0 ? 0 : Math.min(value, 255);
  }

  private static Color[] toColorsForLineMarkers(Color[] baseColors) {
    Color[] colors = new Color[baseColors.length];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = toLineMarkerColor(239, baseColors[i]);
    }
    return colors;
  }

  private Color[] toColorsForEditor(Color[] baseColors) {
    Color tagBackground = myEditor.getBackgroundColor();

    Color[] resultColors = new Color[baseColors.length];
    // todo: make configurable
    double transparency = WebEditorOptions.getInstance().getTagTreeHighlightingOpacity() * 0.01;

    for (int i = 0; i < resultColors.length; i++) {
      Color color = baseColors[i];

      Color color1 = color != null
                           ? UIUtil.makeTransparent(color, tagBackground, transparency)
                           : null;
      resultColors[i] = color1;
    }

    return resultColors;
  }

  public static void clearHighlightingAndLineMarkers(Editor editor, @NotNull Project project) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(editor.getDocument(), project, true);

    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
      if (info == null) continue;
      if (info.type == TYPE) {
        highlighter.dispose();
      }
    }

    clearLineMarkers(editor);
  }
}
