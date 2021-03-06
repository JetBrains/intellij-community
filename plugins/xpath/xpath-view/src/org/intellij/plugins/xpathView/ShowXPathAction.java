/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.TooltipEvent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.HintHint;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.PlatformIcons;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.util.HighlighterUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class ShowXPathAction extends XPathAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        super.update(event);

        final Presentation presentation = event.getPresentation();
        String presentationText = presentation.getText();
        if (presentationText != null && ActionPlaces.isMainMenuOrActionSearch(event.getPlace()) && presentationText.startsWith("Show ")) {
            final String text = presentation.getText().substring("Show ".length());
            presentation.setText(Character.toUpperCase(text.charAt(0)) + text.substring(1));
        }
    }

    @Override
    protected boolean isEnabledAt(XmlFile xmlFile, int offset) {
        final PsiElement element = xmlFile.findElementAt(offset);
        if (!(element instanceof XmlElement || element instanceof PsiWhiteSpace)) {
            return false;
        }

        final PsiElement node = XPathExpressionGenerator.transformToValidShowPathNode(element);
        return node != null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }
        final Project project = editor.getProject();
        if (project == null) {
            return;
        }
        final PsiDocumentManager docmgr = PsiDocumentManager.getInstance(project);
        final Document document = editor.getDocument();
        docmgr.commitDocument(document);

        final PsiFile psiFile = docmgr.getPsiFile(document);
        if (!(psiFile instanceof XmlFile)) {
            return;
        }

        final PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        if (!(element instanceof XmlElement || element instanceof PsiWhiteSpace)) {
            XPathAppComponent.showEditorHint(XPathBundle.message("hint.text.no.suitable.context.for.xpath.expression.selected"), editor);
            return;
        }

        final PsiElement node = XPathExpressionGenerator.transformToValidShowPathNode(element);
        if (node == null) {
            XPathAppComponent.showEditorHint(XPathBundle.message("hint.text.no.suitable.context.for.xpath.expression.selected"), editor);
            return;
        }

        final Config cfg = XPathAppComponent.getInstance().getConfig();
        final RangeHighlighter h = HighlighterUtil.highlightNode(editor, node, cfg.getContextAttributes(), cfg);

        final String path = XPathSupport.getInstance().getUniquePath((XmlElement)node, null); //NON-NLS

        final JTextField label = new JTextField(path);
        //noinspection HardCodedStringLiteral
        label.setPreferredSize(new Dimension(label.getPreferredSize().width + new JLabel("M").getPreferredSize().width, label.getPreferredSize().height));
        label.setOpaque(false);
        label.setEditable(false);
        label.setBorder(null);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        final JPanel p = new NonOpaquePanel(new BorderLayout());
        final JLabel l = new JLabel(XPathBundle.message("label.xpath"));
        p.add(l, BorderLayout.WEST);
        p.add(label, BorderLayout.CENTER);


        InplaceButton copy = new InplaceButton(ActionsBundle.message("action.EditorCopy.text"), PlatformIcons.COPY_ICON, new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            CopyPasteManager.getInstance().setContents(new StringSelection(path));
              Balloon balloon = JBPopupFactory.getInstance().getParentBalloonFor(p);
              if (balloon != null) {
                  balloon.hide(true);
              }
          }
        });

        p.add(copy, BorderLayout.EAST);

      final LightweightHint hint = new LightweightHint(p) {
            @Override
            public void hide() {
                super.hide();
                HighlighterUtil.removeHighlighter(editor, h);
            }


          @Override
          protected boolean canAutoHideOn(TooltipEvent event) {
              InputEvent inputEvent = event.getInputEvent();
              return ((inputEvent instanceof MouseEvent)) && ((MouseEvent)inputEvent).getButton() != 0;
          }
        };

        final Point point = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
        point.y += editor.getLineHeight() / 2;
      HintHint hintHint = new HintHint(editor, point).setAwtTooltip(true).setContentActive(true).setExplicitClose(true).setShowImmediately(true);
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point, HintManager.HIDE_BY_ANY_KEY, 0, false, hintHint);
    }
}