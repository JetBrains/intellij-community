package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.ide.DataManager;

import java.awt.*;

public class FileChooserFactoryImpl extends FileChooserFactory implements ApplicationComponent {
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

  public String getComponentName() {
    return "FileChooserFactory";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}