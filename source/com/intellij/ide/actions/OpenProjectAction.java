package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import com.intellij.util.ArrayUtil;

public class OpenProjectAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

    final FileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(true);
    descriptor.setTitle(IdeBundle.message("title.open.project"));
    String [] extensions = new String[]{ProjectFileType.DOT_DEFAULT_EXTENSION};
    final ProjectOpenProcessor[] openProcessors = Extensions.getExtensions(ProjectOpenProcessorBase.EXTENSION_POINT_NAME);
    for (ProjectOpenProcessor openProcessor : openProcessors) {
      final String[] supportedExtensions = ((ProjectOpenProcessorBase)openProcessor).getSupportedExtensions();
      if (supportedExtensions != null) {
        extensions = ArrayUtil.mergeArrays(extensions, supportedExtensions, String.class);
      }
    }
    descriptor.setDescription(IdeBundle.message("filter.project.files", StringUtil.join(extensions, ", ")));
    final VirtualFile[] files = FileChooser.chooseFiles(project, descriptor);

    if (files.length == 0 || files[0] == null) return;

    ProjectUtil.openOrImport(files[0].getPath(), project, false);
  }
}