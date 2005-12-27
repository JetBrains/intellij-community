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
  private GuiEditor myLastEditor;
  private Presentation myPresentation;

  public ChooseLocaleAction() {
    this(null);
  }

  public ChooseLocaleAction(GuiEditor editor) {
    myEditor = editor;
    Locale locale = editor != null ? editor.getStringDescriptorLocale() : null;
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
    GuiEditor editor = myLastEditor == null ? myEditor : myLastEditor;
    if (editor != null) {
      Locale[] locales = FormEditingUtil.collectUsedLocales(editor.getModule(), editor.getRootContainer());
      Arrays.sort(locales, new Comparator<Locale>() {
        public int compare(final Locale o1, final Locale o2) {
          return o1.getDisplayName().compareTo(o2.getDisplayName());
        }
      });
      for(Locale locale: locales) {
        group.add(new SetLocaleAction(editor, locale));
      }
    }
    return group;
  }

  @Nullable private GuiEditor getEditor(final AnActionEvent e) {
    if (myEditor != null) return myEditor;
    myLastEditor = GuiEditorUtil.getEditorFromContext(e.getDataContext());
    return myLastEditor;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getEditor(e) != null);
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
