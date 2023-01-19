// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.FormHighlightingPass;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

public final class UIFormEditor extends UserDataHolderBase implements FileEditor, PossiblyDumbAware {
  private final VirtualFile myFile;
  private final GuiEditor myEditor;
  private UIFormEditor.MyBackgroundEditorHighlighter myBackgroundEditorHighlighter;

  public UIFormEditor(@NotNull final Project project, @NotNull final VirtualFile file){
    final VirtualFile vf = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getOriginalFile() : file;
    final Module module = ModuleUtilCore.findModuleForFile(vf, project);
    if (module == null) {
      throw new IllegalArgumentException("No module for file " + file + " in project " + project);
    }
    myFile = file;
    myEditor = new GuiEditor(this, project, module, file);
  }

  @Override
  @NotNull
  public JComponent getComponent(){
    return myEditor;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myEditor);
  }

  @Override
  public JComponent getPreferredFocusedComponent(){
    return myEditor.getPreferredFocusedComponent();
  }

  @Override
  @NotNull
  public String getName(){
    return UIDesignerBundle.message("title.gui.designer");
  }

  @NotNull
  public GuiEditor getEditor() {
    return myEditor;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public boolean isModified(){
    return false;
  }

  @Override
  public boolean isValid(){
    //TODO[anton,vova] fire when changed
    return
      FileDocumentManager.getInstance().getDocument(myFile) != null &&
      FileTypeRegistry.getInstance().isFileOfType(myFile, GuiFormFileType.INSTANCE);
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener){
    //TODO[anton,vova]
  }

  @Override
  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener){
    //TODO[anton,vova]
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    if (myBackgroundEditorHighlighter == null) {
      myBackgroundEditorHighlighter = new MyBackgroundEditorHighlighter(myEditor);
    }
    return myBackgroundEditorHighlighter;
  }

  @Override
  @NotNull
  public FileEditorState getState(@NotNull final FileEditorStateLevel ignored) {
    final Document document = FileDocumentManager.getInstance().getCachedDocument(myFile);
    long modificationStamp = document != null ? document.getModificationStamp() : myFile.getModificationStamp();
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
    final String[] ids = new String[selection.size()];
    for (int i = ids.length - 1; i >= 0; i--) {
      ids[i] = selection.get(i).getId();
    }
    return new MyEditorState(modificationStamp, ids);
  }

  @Override
  public void setState(@NotNull final FileEditorState state){
    FormEditingUtil.clearSelection(myEditor.getRootContainer());
    final String[] ids = ((MyEditorState)state).getSelectedComponentIds();
    for (final String id : ids) {
      final RadComponent component = (RadComponent)FormEditingUtil.findComponent(myEditor.getRootContainer(), id);
      if (component != null) {
        component.setSelected(true);
      }
    }
  }

  public void selectComponent(@NotNull final String binding) {
    final RadComponent component = (RadComponent) FormEditingUtil.findComponentWithBinding(myEditor.getRootContainer(), binding);
    if (component != null) {
      FormEditingUtil.selectSingleComponent(getEditor(), component);
    }
  }

  public void selectComponentById(@NotNull final String id) {
    final RadComponent component = (RadComponent)FormEditingUtil.findComponent(myEditor.getRootContainer(), id);
    if (component != null) {
      FormEditingUtil.selectSingleComponent(getEditor(), component);
    }
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  private static class MyBackgroundEditorHighlighter implements BackgroundEditorHighlighter {
    private final HighlightingPass[] myPasses;

    MyBackgroundEditorHighlighter(final GuiEditor editor) {
      myPasses = new HighlightingPass[] { new FormHighlightingPass(editor) };
    }

    @Override
    public HighlightingPass @NotNull [] createPassesForEditor() {
      return myPasses;
    }
  }
}
