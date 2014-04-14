package org.jetbrains.plugins.ipnb;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

/**
 * @author traff
 */
public class IpnbFileEditor extends UserDataHolderBase implements FileEditor, TextEditor {
  private final Project myProject;
  private final VirtualFile myFile;

  private final String myName;

  private final JPanel myEditorPanel;

  private final TextEditor myEditor;


  public IpnbFileEditor(Project project, VirtualFile vFile) {
    myProject = project;

    myFile = vFile;

    myName = vFile.getName();

    myEditor = createEditor(project, vFile);

    myEditorPanel = createIpnbEditorPanel(myName);
  }

  @NotNull
  private JPanel createIpnbEditorPanel(String name) {
    JPanel panel = new JPanel();
    panel.setLayout(new FlowLayout());
    panel.add(new JLabel("Hello IPython " + name));
    return panel;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myEditorPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorPanel;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return new MyEditorState(-1, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myEditor);
  }

  @NotNull
  @Override
  public Editor getEditor() {
    return myEditor.getEditor();
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return true;
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
  }

  @Nullable
  private static TextEditor createEditor(@NotNull Project project, @NotNull VirtualFile vFile) {
    FileEditorProvider provider = getProvider(project, vFile);

    if (provider != null) {
      FileEditor editor = provider.createEditor(project, vFile);
      if (editor instanceof TextEditor) {
        return (TextEditor)editor;
      }
    }
    return null;
  }

  @Nullable
  private static FileEditorProvider getProvider(Project project, VirtualFile vFile) {
    FileEditorProvider[] providers = FileEditorProviderManagerImpl.getInstance().getProviders(project, vFile);
    for (FileEditorProvider provider : providers) {
      if (!(provider instanceof IpnbFileEditor)) {
        return provider;
      }
    }
    return null;
  }
}
