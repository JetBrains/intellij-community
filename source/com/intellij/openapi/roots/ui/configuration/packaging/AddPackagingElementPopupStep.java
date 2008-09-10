package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
*/
class AddPackagingElementPopupStep extends BaseListPopupStep<AddPackagingElementAction> {
  private PackagingEditor myEditor;

  public AddPackagingElementPopupStep(final PackagingEditor editor, final List<AddPackagingElementAction> addActions) {
    super(null, addActions);
    myEditor = editor;
  }

  @Override
    public PopupStep onChosen(final AddPackagingElementAction selectedValue, final boolean finalChoice) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        selectedValue.perform(myEditor);
      }
    }, ModalityState.stateForComponent(myEditor.getMainPanel()));
    return FINAL_CHOICE;
  }

  @Override
    public boolean isSelectable(final AddPackagingElementAction value) {
    return value.isEnabled(myEditor);
  }

  @Override
    public Icon getIconFor(final AddPackagingElementAction aValue) {
    return aValue.getIcon();
  }

  @NotNull
    @Override
    public String getTextFor(final AddPackagingElementAction value) {
    return value.getText();
  }
}
