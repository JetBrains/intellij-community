package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.awt.*;

public class FileChooserFactoryImpl extends FileChooserFactory {
  public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Project project) {
    return new FileChooserDialogImpl(descriptor, project);
  }

  public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Component parent) {
    final DataContext dataContext = DataManager.getInstance().getDataContext(parent);
    if (descriptor.getContextModule() == null) { // if not set
      descriptor.setContextModule((Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT));
    }
    return new FileChooserDialogImpl(descriptor, parent);
  }

}