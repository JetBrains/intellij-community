package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class ConfigureFileDefaultEncodingAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);

    final ConfigureFileEncodingConfigurable configurable = ConfigureFileEncodingConfigurable.getInstance(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable(){
      public void run() {
        if (virtualFile != null) {
          configurable.selectFile(virtualFile);
        }
      }
    });
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }
}
