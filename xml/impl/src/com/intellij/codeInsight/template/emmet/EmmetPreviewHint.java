/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.Alarm;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Producer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;

public class EmmetPreviewHint extends LightweightHint implements Disposable {
  private static final Key<EmmetPreviewHint> KEY = new Key<>("emmet.preview");
  @NotNull private final Editor myParentEditor;
  @NotNull private final Editor myEditor;
  @NotNull private final Alarm myAlarm = new Alarm(this);
  private boolean isDisposed = false;

  private EmmetPreviewHint(@NotNull JBPanel panel, @NotNull Editor editor, @NotNull Editor parentEditor) {
    super(panel);
    myParentEditor = parentEditor;
    myEditor = editor;

    final Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myParentEditor);
    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == myParentEditor || event.getEditor() == myEditor || event.getEditor() == topLevelEditor) {
          hide(true);
        }
      }
    }, this);

    myEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent event) {
        if (!isDisposed && event.isWholeTextReplaced()) {
          Pair<Point, Short> position = guessPosition();
          HintManagerImpl.adjustEditorHintPosition(EmmetPreviewHint.this, myParentEditor, position.first, position.second);
          myEditor.getScrollingModel().scrollVertically(0);
        }
      }
    }, this);
  }

  public void showHint() {
    myParentEditor.putUserData(KEY, this);

    Pair<Point, Short> position = guessPosition();
    JRootPane pane = myParentEditor.getComponent().getRootPane();
    JComponent layeredPane = pane != null ? pane.getLayeredPane() : myParentEditor.getComponent();
    HintHint hintHint = new HintHint(layeredPane, position.first)
      .setAwtTooltip(true)
      .setContentActive(true)
      .setExplicitClose(true)
      .setShowImmediately(true)
      .setPreferredPosition(position.second == HintManager.ABOVE ? Balloon.Position.above : Balloon.Position.below)
      .setTextBg(myParentEditor.getColorsScheme().getDefaultBackground())
      .setBorderInsets(JBUI.insets(1));

    int hintFlags = HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING;
    HintManagerImpl.getInstanceImpl().showEditorHint(this, myParentEditor, position.first, hintFlags, 0, false, hintHint);
  }

  public void updateText(@NotNull final Producer<String> contentProducer) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      if (!isDisposed) {
        final String newText = contentProducer.produce();
        if (StringUtil.isEmpty(newText)) {
          hide();
        }
        else if (!myEditor.getDocument().getText().equals(newText)) {
          DocumentUtil.writeInRunUndoTransparentAction(() -> myEditor.getDocument().setText(newText));
        }
      }
    }, 100);
  }

  @TestOnly
  @NotNull
  public String getContent() {
    return myEditor.getDocument().getText();
  }

  @Nullable
  public static EmmetPreviewHint getExistingHint(@NotNull Editor parentEditor) {
    EmmetPreviewHint emmetPreviewHint = KEY.get(parentEditor);
    if (emmetPreviewHint != null) {
      if (!emmetPreviewHint.isDisposed) {
        return emmetPreviewHint;
      }
      emmetPreviewHint.hide();
    }
    return null;
  }

  @NotNull
  public static EmmetPreviewHint createHint(@NotNull final EditorEx parentEditor,
                                            @NotNull String templateText,
                                            @NotNull FileType fileType) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument(templateText);
    final EditorEx previewEditor = (EditorEx)editorFactory.createEditor(document, parentEditor.getProject(), fileType, true);
    MarkupModelEx model = previewEditor.getMarkupModel();
    if (model instanceof EditorMarkupModel) {
      ((EditorMarkupModel)model).setErrorStripeVisible(true);
    }
    final EditorSettings settings = previewEditor.getSettings();
    settings.setLineNumbersShown(false);
    settings.setAdditionalLinesCount(1);
    settings.setAdditionalColumnsCount(1);
    settings.setRightMarginShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setVirtualSpace(false);
    settings.setWheelFontChangeEnabled(false);
    settings.setAdditionalPageAtBottom(false);
    settings.setCaretRowShown(false);
    previewEditor.setCaretEnabled(false);
    previewEditor.setBorder(JBUI.Borders.empty());

    JBPanel panel = new JBPanel(new BorderLayout()) {
      @NotNull
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        Dimension parentEditorSize = parentEditor.getScrollPane().getSize();
        int maxWidth = (int)parentEditorSize.getWidth() / 3;
        int maxHeight = (int)parentEditorSize.getHeight() / 2;
        final int width = settings.isUseSoftWraps() ? maxWidth : Math.min((int)size.getWidth(), maxWidth);
        final int height = Math.min((int)size.getHeight(), maxHeight);
        return new Dimension(width, height);
      }

      @NotNull
      @Override
      public Insets getInsets() {
        return JBUI.insets(1, 2, 0, 0);
      }
    };
    panel.setBackground(previewEditor.getBackgroundColor());
    panel.add(previewEditor.getComponent(), BorderLayout.CENTER);
    return new EmmetPreviewHint(panel, previewEditor, parentEditor);
  }

  @Override
  public boolean vetoesHiding() {
    return true;
  }

  @Override
  public void hide(boolean ok) {
    super.hide(ok);
    ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(this));
  }

  @Override
  public void dispose() {
    isDisposed = true;
    myAlarm.cancelAllRequests();
    EmmetPreviewHint existingBalloon = myParentEditor.getUserData(KEY);
    if (existingBalloon == this) {
      myParentEditor.putUserData(KEY, null);
    }
    if (!myEditor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }
  }

  @NotNull
  private Pair<Point, Short> guessPosition() {
    JRootPane rootPane = myParentEditor.getContentComponent().getRootPane();
    JComponent layeredPane = rootPane != null ? rootPane.getLayeredPane() : myParentEditor.getComponent();
    LogicalPosition logicalPosition = myParentEditor.getCaretModel().getLogicalPosition();

    LogicalPosition pos = new LogicalPosition(logicalPosition.line, logicalPosition.column);
    Point p1 = HintManagerImpl.getHintPosition(this, myParentEditor, pos, HintManager.UNDER);
    Point p2 = HintManagerImpl.getHintPosition(this, myParentEditor, pos, HintManager.ABOVE);

    boolean p1Ok = p1.y + getComponent().getPreferredSize().height < layeredPane.getHeight();
    boolean p2Ok = p2.y >= 0;

    if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
    if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);

    int underSpace = layeredPane.getHeight() - p1.y;
    int aboveSpace = p2.y;
    return aboveSpace > underSpace
           ? new Pair<>(new Point(p2.x, 0), HintManager.UNDER)
           : new Pair<>(p1, HintManager.ABOVE);
  }
}
