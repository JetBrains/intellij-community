package com.intellij.ide.actions;

import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;

public class PasteAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    PasteProvider provider = (PasteProvider)dataContext.getData(DataConstantsEx.PASTE_PROVIDER);
    if (provider == null || !provider.isPasteEnabled(dataContext)) return;
    provider.performPaste(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    PasteProvider provider = (PasteProvider)dataContext.getData(DataConstantsEx.PASTE_PROVIDER);
    presentation.setEnabled(provider != null && provider.isPastePossible(dataContext));
  }
}