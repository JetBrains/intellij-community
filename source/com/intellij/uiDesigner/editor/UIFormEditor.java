package com.intellij.uiDesigner.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.RadComponent;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class UIFormEditor extends UserDataHolderBase implements FileEditor{
  private final VirtualFile myFile;
  private final GuiEditor myEditor;

  public UIFormEditor(final Project project, final VirtualFile file){
    final Module module = ModuleUtil.getModuleForFile(project, file);
    if (module == null) {
      throw new IllegalArgumentException("no module for file " + file + " in project " + project);
    }
    myFile = file;
    myEditor = new GuiEditor(module, file);
  }

  public JComponent getComponent(){
    return myEditor;
  }
  
  void dispose() {
    myEditor.dispose();
  }

  public JComponent getPreferredFocusedComponent(){
    return myEditor.getPreferredFocusedComponent();
  }

  public String getName(){
    return "GUI Designer";
  }

  public GuiEditor getEditor() {
    return myEditor;
  }

  public boolean isModified(){
    //TODO[anton,vova]
    return false;
  }

  public boolean isValid(){
    //TODO[anton,vova] fire when changed
    return 
      FileDocumentManager.getInstance().getDocument(myFile) != null &&
      FileTypeManager.getInstance().getFileTypeByFile(myFile) == StdFileTypes.GUI_DESIGNER_FORM;
  }

  public void selectNotify(){
  }

  public void deselectNotify(){
  }

  public void addPropertyChangeListener(final PropertyChangeListener listener){
    //TODO[anton,vova]
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener){
    //TODO[anton,vova]
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    // TODO[jeka]: seems like it actually should be implemented.
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  public FileEditorState getState(final FileEditorStateLevel ignored) {
    final Document document = FileDocumentManager.getInstance().getDocument(myFile);
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
    final String[] ids = new String[selection.size()];
    for (int i = ids.length - 1; i >= 0; i--) {
      ids[i] = selection.get(i).getId();
    }
    return new MyEditorState(document.getModificationStamp(), ids);
  }

  public void setState(final FileEditorState state){
    if (state == null){
      throw new IllegalArgumentException("state cannot be null");
    }

    FormEditingUtil.clearSelection(myEditor.getRootContainer());
    final String[] ids = ((MyEditorState)state).getSelectedComponentIds();
    for (int i = 0; i < ids.length; i++) {
      final String id = ids[i];
      final RadComponent component = FormEditingUtil.findComponent(myEditor.getRootContainer(), id);
      if (component != null) {
        component.setSelected(true);
      }
    }
  }

  public void selectComponent(final String binding){
    if (binding == null){
      throw new IllegalArgumentException("binding cannot be null");
    }

    FormEditingUtil.clearSelection(myEditor.getRootContainer());

    FormEditingUtil.iterate(myEditor.getRootContainer(), new FormEditingUtil.ComponentVisitor<RadComponent>() {
      public boolean visit(final RadComponent component) {
        if (binding.equals(component.getBinding())) {
          component.setSelected(true);
          return false;
        }
        return true;
      }
    });
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }
}
