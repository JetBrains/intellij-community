package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.util.Map;

public class ConfigureFileEncodingConfigurable implements Configurable {
  private final Project myProject;
  private FileTreeTable myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;

  public static ConfigureFileEncodingConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ConfigureFileEncodingConfigurable.class);
  }

  public ConfigureFileEncodingConfigurable(Project project) {
    myProject = project;
  }

  @Nls
  public String getDisplayName() {
    return "File Encodings";
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
    myTreeView = new FileTreeTable(myProject);
    myTreePanel.setViewportView(myTreeView);
    return myPanel;
  }

  public boolean isModified() {
    Map<VirtualFile, Charset> editing = myTreeView.getValues();
    Map<VirtualFile, Charset> mapping = EncodingProjectManager.getInstance(myProject).getAllMappings();
    return !editing.equals(mapping);
  }

  public void apply() throws ConfigurationException {
    Map<VirtualFile,Charset> result = myTreeView.getValues();
    EncodingProjectManager.getInstance(myProject).setMapping(result);
  }

  public void reset() {
    myTreeView.reset(EncodingProjectManager.getInstance(myProject).getAllMappings());
  }

  public void disposeUIResources() {
  }
}
