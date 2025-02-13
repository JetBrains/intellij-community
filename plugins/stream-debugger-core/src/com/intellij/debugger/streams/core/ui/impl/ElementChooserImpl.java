// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.ui.impl;

import com.intellij.debugger.streams.core.StreamDebuggerBundle;
import com.intellij.debugger.streams.core.ui.ChooserOption;
import com.intellij.debugger.streams.core.ui.ElementChooser;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vitaliy.Bibaev
 */
public class ElementChooserImpl<T extends ChooserOption> implements ElementChooser<T> {
  private static final int HIGHLIGHT_LAYER = HighlighterLayer.SELECTION + 1;
  private final Set<RangeHighlighter> myRangeHighlighters = new HashSet<>();
  private final Editor myEditor;
  private final TextAttributes myAttributes;

  public ElementChooserImpl(@NotNull Editor editor) {
    myEditor = editor;
    final TextAttributes searchResultAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final Color foreground = editor.getColorsScheme().getDefaultForeground();
    myAttributes = new TextAttributes(foreground, searchResultAttributes.getBackgroundColor(), null,
                                      searchResultAttributes.getEffectType(), searchResultAttributes.getFontType());
  }

  @Override
  public void show(@NotNull List<T> options, @NotNull CallBack<T> callBack) {
    final DefaultListModel<T> model = new DefaultListModel<>();
    int maxOffset = -1;
    for (final T option : options) {
      model.addElement(option);
      maxOffset = Math.max(maxOffset, option.rangeStream().mapToInt(TextRange::getEndOffset).max().orElse(-1));
    }

    final JBList<T> list = new JBList<>(model);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        //noinspection unchecked
        setText(((T)value).getText());
        return this;
      }
    });

    list.addListSelectionListener(e -> {
      final T selectedValue = list.getSelectedValue();
      if (selectedValue == null) return;
      dropHighlighters();
      selectedValue.rangeStream().forEach(x -> {
        final RangeHighlighter highlighter = myEditor.getMarkupModel()
          .addRangeHighlighter(x.getStartOffset(), x.getEndOffset(), HIGHLIGHT_LAYER, myAttributes, HighlighterTargetArea.EXACT_RANGE);
        myRangeHighlighters.add(highlighter);
      });
    });

    final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setTitle(StreamDebuggerBundle.message("multiple.chains.popup.title"))
      .setMovable(true)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback(() -> callBack.chosen(list.getSelectedValue()))
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          dropHighlighters();
        }
      })
      .createPopup();

    if (maxOffset != -1) {
      myEditor.getCaretModel().moveToOffset(maxOffset);
    }

    popup.showInBestPositionFor(myEditor);
  }

  private void dropHighlighters() {
    myRangeHighlighters.forEach(RangeMarker::dispose);
    myRangeHighlighters.clear();
  }
}
