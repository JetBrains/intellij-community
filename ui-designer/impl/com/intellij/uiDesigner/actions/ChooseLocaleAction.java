package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

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
    getTemplatePresentation().setText(UIDesignerBundle.message("choose.locale.default"));
    getTemplatePresentation().setDescription(UIDesignerBundle.message("choose.locale.description"));
    getTemplatePresentation().setIcon(IconLoader.getIcon("/com/intellij/uiDesigner/icons/chooseLocale.png"));
  }

  @Override public JComponent createCustomComponent(Presentation presentation) {
    myPresentation = presentation;
    return super.createCustomComponent(presentation);
  }

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();
    GuiEditor editor = myLastEditor;
    if (editor != null) {
      Locale[] locales = FormEditingUtil.collectUsedLocales(editor.getModule(), editor.getRootContainer());
      if (locales.length > 0) {
        Arrays.sort(locales, new Comparator<Locale>() {
          public int compare(final Locale o1, final Locale o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
          }
        });
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
    myLastEditor = FormEditingUtil.getEditorFromContext(e.getDataContext());
    return myLastEditor;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getEditor(e) != null);
  }

  private class SetLocaleAction extends AnAction {
    private GuiEditor myEditor;
    private Locale myLocale;
    private boolean myUpdateText;

    public SetLocaleAction(final GuiEditor editor, final Locale locale, final boolean updateText) {
      super(locale.getDisplayName().length() == 0
            ? UIDesignerBundle.message("choose.locale.default")
            : locale.getDisplayName());
      myUpdateText = updateText;
      myEditor = editor;
      myLocale = locale;
    }

    public void actionPerformed(AnActionEvent e) {
      myEditor.setStringDescriptorLocale(myLocale);
      if (myUpdateText) {
        myPresentation.setText(getTemplatePresentation().getText());
      }
    }
  }
}
