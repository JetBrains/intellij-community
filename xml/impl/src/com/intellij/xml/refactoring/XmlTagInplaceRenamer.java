// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xml.refactoring;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class XmlTagInplaceRenamer {
  @NonNls private static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OtherVariable";

  private final Editor myEditor;

  private final static Stack<XmlTagInplaceRenamer> ourRenamersStack = new Stack<>();
  private ArrayList<RangeHighlighter> myHighlighters;

  private XmlTagInplaceRenamer(@NotNull final Editor editor) {
    myEditor = editor;
  }

  public static void rename(final Editor editor, @NotNull final XmlTag tag) {
    if (!ourRenamersStack.isEmpty()) {
      ourRenamersStack.peek().finish();
    }

    final XmlTagInplaceRenamer renamer = new XmlTagInplaceRenamer(editor);
    ourRenamersStack.push(renamer);
    renamer.rename(tag);
  }

  private void rename(@NotNull final XmlTag tag) {
    final Pair<ASTNode, ASTNode> pair = getNamePair(tag);
    if (pair == null) return;

    final Project project = myEditor.getProject();
    if (project != null) {

      final List<TextRange> highlightRanges = new ArrayList<>();
      highlightRanges.add(pair.first.getTextRange());
      if (pair.second != null) {
        highlightRanges.add(pair.second.getTextRange());
      }

      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, tag)) {
        return;
      }

      myHighlighters = new ArrayList<>();

      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        final int offset = myEditor.getCaretModel().getOffset();
        myEditor.getCaretModel().moveToOffset(tag.getTextOffset());

        final Template t = buildTemplate(tag, pair);
        TemplateManager.getInstance(project).startTemplate(myEditor, t, new TemplateEditingAdapter() {
          @Override
          public void templateFinished(@NotNull final Template template, boolean brokenOff) {
            finish();
          }

          @Override
          public void templateCancelled(final Template template) {
            finish();
          }
        }, (variableName, value) -> value.length() == 0 || value.charAt(value.length() - 1) != ' ');

        // restore old offset
        myEditor.getCaretModel().moveToOffset(offset);

        addHighlights(highlightRanges, myEditor, myHighlighters);
      }), RefactoringBundle.message("rename.title"), null);
    }
  }

  private void finish() {
    ourRenamersStack.pop();

    if (myHighlighters != null) {
      Project project = myEditor.getProject();
      if (project != null && !project.isDisposed()) {
        final HighlightManager highlightManager = HighlightManager.getInstance(project);
        for (final RangeHighlighter highlighter : myHighlighters) {
          highlightManager.removeSegmentHighlighter(myEditor, highlighter);
        }
      }
    }
  }

  private Pair<ASTNode, ASTNode> getNamePair(@NotNull final XmlTag tag) {
    final int offset = myEditor.getCaretModel().getOffset();

    final ASTNode node = tag.getNode();
    assert node != null;

    final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
    if (startTagName == null) return null;

    final ASTNode endTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(node);

    final ASTNode selected = (endTagName == null ||
                              startTagName.getTextRange().contains(offset) ||
                              startTagName.getTextRange().contains(offset - 1))
                             ? startTagName
                             : endTagName;
    final ASTNode other = (selected == startTagName) ? endTagName : startTagName;

    return Pair.create(selected, other);
  }

  private static Template buildTemplate(@NotNull final XmlTag tag, @NotNull final Pair<? extends ASTNode, ? extends ASTNode> pair) {
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(tag);

    final ASTNode selected = pair.first;
    final ASTNode other = pair.second;

    builder.replaceElement(selected.getPsi(), PRIMARY_VARIABLE_NAME, new ConstantNode(selected.getText()), true);

    if (other != null) {
      builder.replaceElement(other.getPsi(), OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }

    return builder.buildInlineTemplate();
  }

  private static void addHighlights(List<? extends TextRange> ranges, Editor editor, ArrayList<RangeHighlighter> highlighters) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    final TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    final HighlightManager highlightManager = HighlightManager.getInstance(editor.getProject());
    for (final TextRange range : ranges) {
      highlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, 0, highlighters, null);
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

}
