package org.jetbrains.plugins.ipnb.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbRunCellAction;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.IOException;

/**
 * @author traff
 */
public class IpnbFileEditor extends UserDataHolderBase implements FileEditor, TextEditor {
  private final Project myProject;
  private final VirtualFile myFile;

  private final String myName;

  private final JComponent myEditorPanel;

  private final TextEditor myEditor;
  private final IpnbFilePanel myIpnbEditorPanel;


  public IpnbFileEditor(Project project, VirtualFile vFile) {
    myProject = project;

    myFile = vFile;

    myName = vFile.getName();

    myEditor = createEditor(project, vFile);

    myEditorPanel = new JPanel(new BorderLayout());
    myEditorPanel.setBackground(IpnbEditorUtil.getBackground());

    myIpnbEditorPanel = createIpnbEditorPanel(myProject, vFile, this);

    final JPanel controlPanel = new JPanel();
    controlPanel.setBackground(IpnbEditorUtil.getBackground());
    final JButton button = new JButton();
    button.setPreferredSize(new Dimension(30, 30));
    button.setIcon(AllIcons.General.Run);
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final IpnbRunCellAction action = (IpnbRunCellAction)ActionManager.getInstance().getAction("IpnbRunCellAction");
        action.runCell(myIpnbEditorPanel);
      }
    });
    controlPanel.add(button);
    final MatteBorder border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.GRAY);
    controlPanel.setBorder(border);
    myEditorPanel.add(controlPanel, BorderLayout.NORTH);
    myEditorPanel.add(new MyScrollPane(myIpnbEditorPanel), BorderLayout.CENTER);
  }

  @NotNull
  private IpnbFilePanel createIpnbEditorPanel(Project project, VirtualFile vFile, Disposable parent) {
    try {
      return new IpnbFilePanel(project, parent, IpnbParser.parseIpnbFile(new String(vFile.contentsToByteArray(), CharsetToolkit.UTF8)));
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), "Can't open " + vFile.getPath());
      throw new IllegalStateException(e);
    }
  }

  public IpnbFilePanel getIpnbEditorPanel() {
    return myIpnbEditorPanel;
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
      if (!(provider instanceof IpnbEditorProvider)) {
        return provider;
      }
    }
    return null;
  }

  private class MyScrollPane extends JBScrollPane {
    private MyScrollPane(Component view) {
      super(view);
    }

    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(this);
    }
  }


  private class MyScrollBar extends JBScrollBar {
    private MyScrollPane myScrollPane;

    public MyScrollBar(MyScrollPane scrollPane) {
      myScrollPane = scrollPane;
    }

    @Override
    public int getUnitIncrement(int direction) {
      return myEditor.getEditor().getLineHeight();
    }

    @Override
    public int getBlockIncrement(int direction) {
      return myEditor.getEditor().getLineHeight();
    }
  }
}
