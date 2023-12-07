// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;


public class ChooseLocaleAction extends ComboBoxAction {

  private static final String PRESENTATION = "ChooseLocaleAction.presentation";

  public ChooseLocaleAction() {
    getTemplatePresentation().setText("");
    getTemplatePresentation().setDescription(UIDesignerBundle.messagePointer("choose.locale.description"));
    getTemplatePresentation().setIcon(AllIcons.Nodes.PpWeb);
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JComponent component = super.createCustomComponent(presentation, place);
    component.putClientProperty(PRESENTATION, presentation);
    return component;
  }

  @Override
  protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext dataContext) {
    Presentation presentation = (Presentation)button.getClientProperty(PRESENTATION);
    DefaultActionGroup group = new DefaultActionGroup();
    GuiEditor editor = FormEditingUtil.getActiveEditor(dataContext);
    if (editor != null) {
      Locale[] locales = FormEditingUtil.collectUsedLocales(editor.getModule(), editor.getRootContainer());
      if (locales.length > 1 || (locales.length == 1 && locales[0].getDisplayName().length() > 0)) {
        Arrays.sort(locales, Comparator.comparing(Locale::getDisplayName));
        for (Locale locale : locales) {
          group.add(new SetLocaleAction(editor, locale) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              super.actionPerformed(e);
              presentation.setText(getTemplatePresentation().getText());
            }
          });
        }
      }
      else {
        group.add(new SetLocaleAction(editor, new Locale("")));
      }
    }
    return group;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(FormEditingUtil.getActiveEditor(e.getDataContext()) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static class SetLocaleAction extends AnAction {
    final GuiEditor myEditor;
    final Locale myLocale;

    SetLocaleAction(GuiEditor editor, Locale locale) {
      getTemplatePresentation().setText(getLocaleText(locale), false);
      myEditor = editor;
      myLocale = locale;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myEditor.setStringDescriptorLocale(myLocale);
    }
  }

  private static @NlsSafe String getLocaleText(Locale locale) {
    return locale.getDisplayName().length() == 0 ? UIDesignerBundle.message("choose.locale.default") : locale.getDisplayName();
  }
}
