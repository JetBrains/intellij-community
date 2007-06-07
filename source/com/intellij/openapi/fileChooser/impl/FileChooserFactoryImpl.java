package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.ComponentDisposable;

import javax.swing.*;
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

  public FileTextField createFileTextField(final FileChooserDescriptor descriptor, final boolean showHidden, Disposable parent) {
    FileTextFieldImpl.Vfs field = new FileTextFieldImpl.Vfs(descriptor, showHidden, new JTextField());
    Disposer.register(parent, field);
    return field;
  }

  public FileTextField createFileTextField(final FileChooserDescriptor descriptor, Disposable parent) {
    return createFileTextField(descriptor, true, parent);
  }

  public void installFileCompletion(final JTextField field, final FileChooserDescriptor descriptor, final boolean showHidden,
                                    final Disposable parent) {
    FileTextFieldImpl.Vfs vfsField = new FileTextFieldImpl.Vfs(descriptor, showHidden, field);
    Disposer.register(parent, vfsField);
  }
}