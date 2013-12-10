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

  @Nullable 
  private PostfixTemplatesListPanel myTemplatesListPanel;

  @NotNull
  @Override
  public String getId() {
    return "reference.settingsdialog.IDE.editor.postfix.completion";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Postfix Completion";
  }

  @Nullable
  public PostfixTemplatesListPanel getTemplatesListPanel() {
    if (myTemplatesListPanel == null) {
      createComponent();
    }
    return myTemplatesListPanel;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (myTemplatesListPanel == null) {
      final PostfixTemplate[] templates = PostfixTemplate.EP_NAME.getExtensions();

      PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
      if (templatesSettings == null) {
        LOG.error("Can't retrieve postfix template settings");
        return null;
      }

      myTemplatesListPanel = new PostfixTemplatesListPanel(Arrays.asList(templates));
    }

    return myTemplatesListPanel.getComponent();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myTemplatesListPanel != null) {
      PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
      if (templatesSettings != null) {
        Map<String, Boolean> newTemplatesState = ContainerUtil.newHashMap();
        for (Map.Entry<String, Boolean> entry : myTemplatesListPanel.getState().entrySet()) {
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
    if (myTemplatesListPanel != null) {
      PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
      if (templatesSettings != null) {
        myTemplatesListPanel.setState(templatesSettings.getTemplatesState());
      }
    }
  }

  @Override
  public void disposeUIResources() {
    myTemplatesListPanel = null;
  }

  @Override
  public boolean isModified() {
    PostfixCompletionSettings templatesSettings = PostfixCompletionSettings.getInstance();
    if (templatesSettings == null) return false;

    return myTemplatesListPanel != null && !myTemplatesListPanel.getState().equals(templatesSettings.getTemplatesState());
  }

  @Nullable
  @Override
  public Runnable enableSearch(String s) {
    return null;
  }
}