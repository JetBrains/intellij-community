package com.intellij.ide.actions;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class ExportToTextFileToolbarAction extends ExportToTextFileAction {
  private ExporterToTextFile myExporterToTextFile;

  public ExportToTextFileToolbarAction(ExporterToTextFile exporterToTextFile) {
    myExporterToTextFile = exporterToTextFile;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPORT_TO_TEXT_FILE));
  }

  protected ExporterToTextFile getExporter(DataContext dataContext) {
    return myExporterToTextFile;
  }
}
