
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;

public class CopyAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CopyProvider provider = (CopyProvider)dataContext.getData(DataConstantsEx.COPY_PROVIDER);
    if (provider == null) return;
    provider.performCopy(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CopyProvider provider = (CopyProvider)dataContext.getData(DataConstantsEx.COPY_PROVIDER);
    presentation.setEnabled(provider != null && provider.isCopyEnabled(dataContext));
  }
}
