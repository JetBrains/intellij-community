package com.intellij.compiler.impl;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.HelpID;
import com.intellij.compiler.options.CompilerConfigurable;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorTreeNodeDescriptor;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

public class CompilerErrorTreeView extends NewErrorTreeViewPanel {
  public CompilerErrorTreeView(Project project) {
    super(project, HelpID.COMPILER);
  }

  protected void fillRightToolbarGroup(DefaultActionGroup group) {
    super.fillRightToolbarGroup(group);
    group.add(new CompilerPropertiesAction());
  }

  protected void addExtraPopupMenuActions(DefaultActionGroup group) {
    group.add(new ExcludeFromCompileAction());
  }

  protected boolean shouldShowFirstErrorInEditor() {
    return CompilerWorkspaceConfiguration.getInstance(myProject).AUTO_SHOW_ERRORS_IN_EDITOR;
  }

  private static class CompilerPropertiesAction extends AnAction {
    public CompilerPropertiesAction() {
      super("Compiler Properties", null, IconLoader.getIcon("/general/projectProperties.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
      ShowSettingsUtil.getInstance().editConfigurable(project, CompilerConfigurable.getInstance(project));
    }
  }

  private class ExcludeFromCompileAction extends AnAction {
    public ExcludeFromCompileAction() {
      super("Exclude From Compile");
    }

    public void actionPerformed(AnActionEvent e) {
      VirtualFile file = getSelectedFile();

      if (file != null && file.isValid()) {
        ExcludeEntryDescription description = new ExcludeEntryDescription(file, false, true);
        CompilerConfiguration.getInstance(myProject).addExcludeEntryDescription(description);
        FileStatusManager.getInstance(myProject).fileStatusesChanged();
      }
    }

    private VirtualFile getSelectedFile() {
      final ErrorTreeElement selectedElement = getSelectedErrorTreeElement();
      if (selectedElement == null) return null;

      String filePresentableText = getSelectedFilePresentableText(selectedElement);

      if (filePresentableText == null) return null;

      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePresentableText.replace('\\', '/'));
      return file;
    }

    private String getSelectedFilePresentableText(final ErrorTreeElement selectedElement) {
      String filePresentableText = null;

      if (selectedElement instanceof GroupingElement) {
        GroupingElement groupingElement = (GroupingElement)selectedElement;
        filePresentableText = groupingElement.getName();
      }
      else {
        NodeDescriptor parentDescriptor = getSelectedNodeDescriptor().getParentDescriptor();
        if (parentDescriptor instanceof ErrorTreeNodeDescriptor) {
          ErrorTreeNodeDescriptor treeNodeDescriptor = (ErrorTreeNodeDescriptor)parentDescriptor;
          ErrorTreeElement element = treeNodeDescriptor.getElement();
          if (element instanceof GroupingElement) {
            GroupingElement groupingElement = (GroupingElement)element;
            filePresentableText = groupingElement.getName();
          }
        }

      }
      return filePresentableText;
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final boolean isApplicable = getSelectedFile() != null;
      presentation.setEnabled(isApplicable);
      presentation.setVisible(isApplicable);
    }
  }

}
