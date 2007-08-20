/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Aug 8, 2007
 * Time: 2:20:33 PM
 */
package com.intellij.xml.refactoring;

import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
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
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XmlTagInplaceRenamer {
  @NonNls private static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OtherVariable";

  private Editor myEditor;
  private HighlightManager myHighlightManager;
  private Project myProject;

  private XmlTagInplaceRenamer(@NotNull final Editor editor) {
    myEditor = editor;
    myProject = editor.getProject();
    myHighlightManager = HighlightManager.getInstance(editor.getProject());
  }
  
  public static void rename(final Editor editor, @NotNull final XmlTag tag) {
    new XmlTagInplaceRenamer(editor).rename(tag);
  }

  private void rename(@NotNull final XmlTag tag) {
    final Pair<ASTNode, ASTNode> pair = getNamePair(tag);

    final List<TextRange> highlightRanges = new ArrayList<TextRange>();
    highlightRanges.add(pair.first.getTextRange());
    if (pair.second != null) {
      highlightRanges.add(pair.second.getTextRange());
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, tag)) {
      return;
    }

    final ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final int offset = myEditor.getCaretModel().getOffset();
            myEditor.getCaretModel().moveToOffset(tag.getTextOffset());

            final Template t = buildTemplate(tag, pair);
            TemplateManager.getInstance(myProject).startTemplate(myEditor, t, new TemplateEditingListener() {
              public void templateFinished(final Template template) {
                removeHighlighters(myEditor, highlighters);
              }

              public void templateCancelled(final Template template) {
                removeHighlighters(myEditor, highlighters);
              }
            });

            // restore old offset
            myEditor.getCaretModel().moveToOffset(offset);

            addHighlights(highlightRanges, myEditor, highlighters);
          }
        });
      }
    }, RefactoringBundle.message("rename.title"), null);
  }

  private void removeHighlighters(@NotNull final Editor editor, @NotNull final List<RangeHighlighter> highlighters) {
    for (final RangeHighlighter highlighter : highlighters) {
      myHighlightManager.removeSegmentHighlighter(editor, highlighter);
    }
  }

  private Pair<ASTNode, ASTNode> getNamePair(@NotNull final XmlTag tag) {
    final int offset = myEditor.getCaretModel().getOffset();

    final ASTNode node = tag.getNode();
    assert node != null;

    final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
    assert startTagName != null;

    final ASTNode endTagName = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(node);

    final ASTNode selected = (endTagName == null || startTagName.getTextRange().contains(offset) ||
                              startTagName.getTextRange().contains(offset - 1))
                             ? startTagName
                             : endTagName;
    final ASTNode other = (selected == startTagName) ? endTagName : startTagName;

    return new Pair<ASTNode, ASTNode>(selected, other);
  }

  private static Template buildTemplate(@NotNull final XmlTag tag, @NotNull final Pair<ASTNode, ASTNode> pair) {
    final TemplateBuilder builder = new TemplateBuilder(tag);

    final ASTNode selected = pair.first;
    final ASTNode other = pair.second;

    builder.replaceElement(selected.getPsi(), PRIMARY_VARIABLE_NAME, new EmptyExpression() {
      public Result calculateQuickResult(final ExpressionContext context) {
        return new TextResult(selected.getText());
      }

      public Result calculateResult(final ExpressionContext context) {
        return new TextResult(selected.getText());
      }
    }, true);

    if (other != null) {
      builder.replaceElement(other.getPsi(), OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }

    return builder.buildInlineTemplate();
  }

  private void addHighlights(List<TextRange> ranges, Editor editor, ArrayList<RangeHighlighter> highlighters) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    final TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    for (final TextRange range : ranges) {
      myHighlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, 0, highlighters, null);
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

}