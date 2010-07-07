/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Aug 8, 2007
 * Time: 2:20:33 PM
 */
package com.intellij.xml.refactoring;

import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XmlTagInplaceRenamer extends InplaceRenamer {

  final XmlTag myTag;

  public XmlTagInplaceRenamer(@NotNull final Editor editor, XmlTag tag) {
    super(editor);
    this.myTag = tag;
  }

  @Override
  protected void rename() {

    final Pair<ASTNode, ASTNode> pair = getNamePair(myTag);
    if (pair == null) return;

    final Project project = myEditor.getProject();
    if (project == null) {
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myTag)) {
      return;
    }

    final List<TextRange> highlightRanges = new ArrayList<TextRange>();
    highlightRanges.add(pair.first.getTextRange());
    if (pair.second != null) {
      highlightRanges.add(pair.second.getTextRange());
    }

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final int offset = myEditor.getCaretModel().getOffset();
            myEditor.getCaretModel().moveToOffset(myTag.getTextOffset());

            final Template t = buildTemplate(myTag, pair);
            TemplateManager.getInstance(project).startTemplate(myEditor, t, new TemplateEditingAdapter() {
              public void templateFinished(final Template template, boolean brokenOff) {
                finish();
              }

              public void templateCancelled(final Template template) {
                finish();
              }
            }, new PairProcessor<String, String>() {
              public boolean process(final String variableName, final String value) {
                return value.length() == 0 || value.charAt(value.length() - 1) != ' ';
              }
            });

            // restore old offset
            myEditor.getCaretModel().moveToOffset(offset);

            addHighlights(highlightRanges);
          }
        });
      }
    }, RefactoringBundle.message("rename.title"), null);
  }

  @Nullable
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

    return new Pair<ASTNode, ASTNode>(selected, other);
  }

  private static Template buildTemplate(@NotNull final XmlTag tag, @NotNull final Pair<ASTNode, ASTNode> pair) {
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(tag);

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

}
