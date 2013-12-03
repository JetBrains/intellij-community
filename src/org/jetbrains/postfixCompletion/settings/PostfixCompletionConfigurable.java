package org.jetbrains.postfixCompletion.settings;

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;

public class PostfixCompletionConfigurable implements SearchableConfigurable, EditorOptionsProvider, Configurable.NoScroll {
  private static final Logger LOG = Logger.getInstance(PostfixCompletionConfigurable.class);
  @Nullable private PostfixTemplatesListPanel myPanel;

  @NotNull
  @Override
  public String getId() {
    return "reference.settingsdialog.IDE.editor.postfix.completion";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return getId();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Postfix Completion";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      final PostfixTemplate[] templates = PostfixTemplate.EP_NAME.getExtensions();

      PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
      if (templatesSettings == null) {
        LOG.error("Can't retrieve postfix template settings");
        return null;
      }

      myPanel = new PostfixTemplatesListPanel(Arrays.asList(templates));
    }

    return myPanel.getComponent();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
      if (templatesSettings != null) {
        Map<String, Boolean> newTemplatesState = ContainerUtil.newHashMap();
        for (Map.Entry<String, Boolean> entry : myPanel.getState().entrySet()) {
          Boolean value = entry.getValue();
          if (value != null && !value) {
            newTemplatesState.put(entry.getKey(), entry.getValue());
          }
        }

        templatesSettings.setTemplatesState(newTemplatesState);
      }
    }
  }

  @Override
  public void reset() {
    if (myPanel != null) {
      PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
      if (templatesSettings != null) {
        myPanel.setState(templatesSettings.getTemplatesState());
      }
    }
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @Override
  public boolean isModified() {
    PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
    if (templatesSettings == null) return false;

    return myPanel != null && !myPanel.getState().equals(templatesSettings.getTemplatesState());
  }

  @Nullable
  @Override
  public Runnable enableSearch(String s) {
    return null;
  }
}