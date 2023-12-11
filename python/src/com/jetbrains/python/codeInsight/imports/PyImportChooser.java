// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.jetbrains.python.PyPsiBundle;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class PyImportChooser implements ImportChooser {

  @Override
  public Promise<ImportCandidateHolder> selectImport(List<? extends ImportCandidateHolder> sources, boolean useQualifiedImport) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return Promises.resolvedPromise(sources.get(0));
    }

    AsyncPromise<ImportCandidateHolder> result = new AsyncPromise<>();

    // GUI part
    DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext -> JBPopupFactory.getInstance()
      .createPopupChooserBuilder(sources)
      .setRenderer(new CellRenderer())
      .setTitle(useQualifiedImport ? PyPsiBundle.message("ACT.qualify.with.module") : PyPsiBundle.message("ACT.from.some.module.import"))
      .setItemChosenCallback(item -> {
        result.setResult(item);
      })
      .setNamerForFiltering(o -> o.getPresentableText())
      .createPopup()
      .showInBestPositionFor(dataContext));

    return result;
  }

  // Stolen from FQNameCellRenderer
  private static class CellRenderer extends SimpleColoredComponent implements ListCellRenderer<ImportCandidateHolder> {
    private final Font FONT;

    CellRenderer() {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      FONT = scheme.getFont(EditorFontType.PLAIN);
      setOpaque(true);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ImportCandidateHolder> list,
                                                  ImportCandidateHolder value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      clear();

      PsiElement importable = value.getImportable();
      if (importable != null) {
        setIcon(importable.getIcon(0));
      }
      String item_name = value.getPresentableText();
      append(item_name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

      setFont(FONT);
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      }
      else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }
      return this;
    }
  }
}
