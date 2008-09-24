package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileTypes.FileOptionsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.util.Map;

public class FileEncodingConfigurable implements FileOptionsProvider, NonDefaultProjectConfigurable {
  private final Project myProject;
  private FileTreeTable myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;
  private JCheckBox myAutodetectUTFEncodedFilesCheckBox;
  private JCheckBox myTransparentNativeToAsciiCheckBox;
  private JPanel myPropertiesFilesEncodingCombo;
  private Charset mySelectedCharsetForPropertiesFiles;
  private ChooseFileEncodingAction myAction;

  public static FileEncodingConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, FileEncodingConfigurable.class);
  }

  public FileEncodingConfigurable(Project project) {
    myProject = project;
  }

  @Nls
  public String getDisplayName() {
    return IdeBundle.message("file.encodings.configurable");
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/general/configureEncoding.png");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    myAction = new ChooseFileEncodingAction(null) {
      public void update(final AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(mySelectedCharsetForPropertiesFiles == null ?
                                          IdeBundle.message("encoding.name.system.default") :
                                          mySelectedCharsetForPropertiesFiles.displayName());
      }

      protected void chosen(final VirtualFile virtualFile, final Charset charset) {
        mySelectedCharsetForPropertiesFiles = charset == NO_ENCODING ? null : charset;
        update(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", myAction.getTemplatePresentation(),
                                          ActionManager.getInstance(), 0));
      }
    };
    Presentation templatePresentation = myAction.getTemplatePresentation();
    myPropertiesFilesEncodingCombo.add(myAction.createCustomComponent(templatePresentation));
    myTreeView = new FileTreeTable(myProject);
    myTreePanel.setViewportView(myTreeView);
    return myPanel;
  }

  public boolean isModified() {
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);

    Map<VirtualFile, Charset> editing = myTreeView.getValues();
    Map<VirtualFile, Charset> mapping = EncodingProjectManager.getInstance(myProject).getAllMappings();
    boolean same = editing.equals(mapping)
       && Comparing.equal(encodingManager.getDefaultCharsetForPropertiesFiles(null), mySelectedCharsetForPropertiesFiles)
       && encodingManager.isUseUTFGuessing(null) == myAutodetectUTFEncodedFilesCheckBox.isSelected()
       && encodingManager.isNative2AsciiForPropertiesFiles(null) == myTransparentNativeToAsciiCheckBox.isSelected()
      ;
    return !same;
  }

  public void apply() throws ConfigurationException {
    Map<VirtualFile,Charset> result = myTreeView.getValues();
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    encodingManager.setMapping(result);
    encodingManager.setDefaultCharsetForPropertiesFiles(null, mySelectedCharsetForPropertiesFiles);
    encodingManager.setNative2AsciiForPropertiesFiles(null, myTransparentNativeToAsciiCheckBox.isSelected());
    encodingManager.setUseUTFGuessing(null, myAutodetectUTFEncodedFilesCheckBox.isSelected());
  }

  public void reset() {
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    myTreeView.reset(encodingManager.getAllMappings());
    myAutodetectUTFEncodedFilesCheckBox.setSelected(encodingManager.isUseUTFGuessing(null));
    myTransparentNativeToAsciiCheckBox.setSelected(encodingManager.isNative2AsciiForPropertiesFiles(null));
    mySelectedCharsetForPropertiesFiles = encodingManager.getDefaultCharsetForPropertiesFiles(null);
    myAction.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", myAction.getTemplatePresentation(),
                                      ActionManager.getInstance(), 0));
 }

  public void disposeUIResources() {
    myAction = null;
  }

  public void selectFile(@NotNull VirtualFile virtualFile) {
    myTreeView.select(virtualFile);
  }

  private void createUIComponents() {
    myTreePanel = ScrollPaneFactory.createScrollPane(new JTable());
  }
}
