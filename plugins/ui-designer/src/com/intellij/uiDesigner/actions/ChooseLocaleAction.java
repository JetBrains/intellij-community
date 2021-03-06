// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * @author yole
 */
public class ChooseLocaleAction extends ComboBoxAction {
  private GuiEditor myLastEditor;
  private Presentation myPresentation;

  public ChooseLocaleAction() {
    getTemplatePresentation().setText("");
    getTemplatePresentation().setDescription(UIDesignerBundle.messagePointer("choose.locale.description"));
    getTemplatePresentation().setIcon(AllIcons.Nodes.PpWeb);
  }

  @NotNull
  @Override public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    myPresentation = presentation;
    return super.createCustomComponent(presentation, place);
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();
    GuiEditor editor = myLastEditor;
    if (editor != null) {
      Locale[] locales = FormEditingUtil.collectUsedLocales(editor.getModule(), editor.getRootContainer());
      if (locales.length > 1 || (locales.length == 1 && locales [0].getDisplayName().length() > 0)) {
        Arrays.sort(locales, Comparator.comparing(Locale::getDisplayName));
        for(Locale locale: locales) {
          group.add(new SetLocaleAction(editor, locale, true));
        }
      }
      else {
        group.add(new SetLocaleAction(editor, new Locale(""), false));
      }
    }
    return group;
  }

  @Nullable private GuiEditor getEditor(final AnActionEvent e) {
    myLastEditor = FormEditingUtil.getActiveEditor(e.getDataContext());
    return myLastEditor;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(getEditor(e) != null);
  }

  private class SetLocaleAction extends AnAction {
    private final GuiEditor myEditor;
    private final Locale myLocale;
    private final boolean myUpdateText;

    SetLocaleAction(final GuiEditor editor, final Locale locale, final boolean updateText) {
      super(getLocaleText(locale));
      myUpdateText = updateText;
      myEditor = editor;
      myLocale = locale;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myEditor.setStringDescriptorLocale(myLocale);
      if (myUpdateText) {
        myPresentation.setText(getTemplatePresentation().getText());
      }
    }
  }

  private static @NlsSafe String getLocaleText(Locale locale) {
    return locale.getDisplayName().length() == 0 ? UIDesignerBundle.message("choose.locale.default") : locale.getDisplayName();
  }
}
