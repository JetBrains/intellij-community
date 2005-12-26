package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * @author yole
 */
public class ChooseLocaleAction extends ComboBoxAction {
  private GuiEditor myEditor;
  private Presentation myPresentation;

  public ChooseLocaleAction(GuiEditor editor) {
    myEditor = editor;
    Locale locale = editor.getStringDescriptorLocale();
    getTemplatePresentation().setText(locale == null || locale.getDisplayName().length() == 0
                                      ? UIDesignerBundle.message("choose.locale.default")
                                      : locale.getDisplayName());
    getTemplatePresentation().setDescription(UIDesignerBundle.message("choose.locale.description"));
    getTemplatePresentation().setIcon(IconLoader.getIcon("/com/intellij/uiDesigner/icons/chooseLocale.png"));
  }

  @Override public JComponent createCustomComponent(Presentation presentation) {
    myPresentation = presentation;
    return super.createCustomComponent(presentation);
  }

  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();
    Locale[] locales = FormEditingUtil.collectUsedLocales(myEditor.getModule(), myEditor.getRootContainer());
    Arrays.sort(locales, new Comparator<Locale>() {
      public int compare(final Locale o1, final Locale o2) {
        return o1.getDisplayName().compareTo(o2.getDisplayName());
      }
    });
    for(Locale locale: locales) {
      group.add(new SetLocaleAction(myEditor, locale));
    }
    return group;
  }

  @Nullable private GuiEditor getEditor(final AnActionEvent e) {
    if (myEditor != null) return myEditor;
    return GuiEditorUtil.getEditorFromContext(e.getDataContext());
  }

  public void update(AnActionEvent e) {
    if (getEditor(e) == null) {
      e.getPresentation().setEnabled(false);
    }
  }

  private class SetLocaleAction extends AnAction {
    private GuiEditor myEditor;
    private Locale myLocale;

    public SetLocaleAction(final GuiEditor editor, final Locale locale) {
      super(locale.getDisplayName().length() == 0
            ? UIDesignerBundle.message("choose.locale.default")
            : locale.getDisplayName());
      myEditor = editor;
      myLocale = locale;
    }

    public void actionPerformed(AnActionEvent e) {
      myEditor.setStringDescriptorLocale(myLocale);
      myPresentation.setText(getTemplatePresentation().getText());
    }
  }
}
