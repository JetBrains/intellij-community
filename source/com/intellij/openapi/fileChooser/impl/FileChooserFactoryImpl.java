package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.Project;

import java.awt.*;

public class FileChooserFactoryImpl extends FileChooserFactory implements ApplicationComponent {
  public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Project project) {
    return new FileChooserDialogImpl(descriptor, project);
  }

  public FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Component parent) {
    return new FileChooserDialogImpl(descriptor, parent);
  }

  public String getComponentName() {
    return "FileChooserFactory";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}