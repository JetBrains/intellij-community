package com.intellij.uiDesigner.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormHighlightingPass;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

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
  private UIFormEditor.MyBackgroundEditorHighlighter myBackgroundEditorHighlighter;

  public UIFormEditor(final Project project, final VirtualFile file){
    final Module module = VfsUtil.getModuleForFile(project, file);
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

  @NotNull
  public String getName(){
    return UIDesignerBundle.message("title.gui.designer");
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
    if (myBackgroundEditorHighlighter == null) {
      myBackgroundEditorHighlighter = new MyBackgroundEditorHighlighter(myEditor);
    }
    return myBackgroundEditorHighlighter;
  }

  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @NotNull
  public FileEditorState getState(final FileEditorStateLevel ignored) {
    final Document document = FileDocumentManager.getInstance().getCachedDocument(myFile);
    long modificationStamp = document != null ? document.getModificationStamp() : myFile.getModificationStamp();
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
    final String[] ids = new String[selection.size()];
    for (int i = ids.length - 1; i >= 0; i--) {
      ids[i] = selection.get(i).getId();
    }
    return new MyEditorState(modificationStamp, ids);
  }

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

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  private class MyBackgroundEditorHighlighter implements BackgroundEditorHighlighter {
    private HighlightingPass[] myPasses;

    public MyBackgroundEditorHighlighter(final GuiEditor editor) {
      myPasses = new HighlightingPass[] { new FormHighlightingPass(editor) };
    }

    public HighlightingPass[] createPassesForEditor() {
      PsiDocumentManager.getInstance(myEditor.getProject()).commitAllDocuments();
      return myPasses;
    }

    public HighlightingPass[] createPassesForVisibleArea() {
      PsiDocumentManager.getInstance(myEditor.getProject()).commitAllDocuments();
      return myPasses;
    }
  }
}
