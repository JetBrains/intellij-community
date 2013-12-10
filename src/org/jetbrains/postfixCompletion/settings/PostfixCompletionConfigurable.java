package org.jetbrains.postfixCompletion.settings;

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

public class PostfixCompletionConfigurable implements SearchableConfigurable, EditorOptionsProvider, Configurable.NoScroll {
  @Nullable
  private PostfixTemplatesListPanel myTemplatesListPanel;
  @NotNull
  private final PostfixCompletionSettings myTemplatesSettings;

  private JComponent myPanel;
  private JBCheckBox myCompletionEnabledCheckbox;
  private JBCheckBox myPluginEnabledCheckbox;
  private JPanel myTemplatesListPanelContainer;

  public PostfixCompletionConfigurable() {
    PostfixCompletionSettings settings = PostfixCompletionSettings.getInstance();
    if (settings == null) {
      throw new RuntimeException("Can't retrieve postfix template settings");
    }

    myTemplatesSettings = settings;
    myTemplatesListPanel = new PostfixTemplatesListPanel(Arrays.asList(PostfixTemplate.EP_NAME.getExtensions()));
    myTemplatesListPanelContainer.setLayout(new BorderLayout());
    myTemplatesListPanelContainer.add(myTemplatesListPanel.getComponent(), BorderLayout.CENTER);
    myPluginEnabledCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateComponents();
      }
    });
  }

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
    return myTemplatesListPanel;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myTemplatesListPanel != null) {
      Map<String, Boolean> newTemplatesState = ContainerUtil.newHashMap();
      for (Map.Entry<String, Boolean> entry : myTemplatesListPanel.getState().entrySet()) {
        Boolean value = entry.getValue();
        if (value != null && !value) {
          newTemplatesState.put(entry.getKey(), entry.getValue());
        }
      }
      myTemplatesSettings.setTemplatesState(newTemplatesState);
      myTemplatesSettings.setPostfixPluginEnabled(myPluginEnabledCheckbox.isSelected());
      myTemplatesSettings.setTemplatesCompletionEnabled(myCompletionEnabledCheckbox.isSelected());
    }
  }

  @Override
  public void reset() {
    if (myTemplatesListPanel != null) {
      myTemplatesListPanel.setState(myTemplatesSettings.getTemplatesState());
      myPluginEnabledCheckbox.setSelected(myTemplatesSettings.isPostfixPluginEnabled());
      myCompletionEnabledCheckbox.setSelected(myTemplatesSettings.isTemplatesCompletionEnabled());
      
      updateComponents();
    }
  }

  @Override
  public boolean isModified() {
    if (myTemplatesListPanel == null) {
      return false;
    }
    return myPluginEnabledCheckbox.isSelected() != myTemplatesSettings.isPostfixPluginEnabled() ||
           myCompletionEnabledCheckbox.isSelected() != myTemplatesSettings.isTemplatesCompletionEnabled() ||
           !myTemplatesListPanel.getState().equals(myTemplatesSettings.getTemplatesState());
  }

  @Override
  public void disposeUIResources() {
    myTemplatesListPanel = null;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String s) {
    return null;
  }

  private void updateComponents() {
    myCompletionEnabledCheckbox.setEnabled(myPluginEnabledCheckbox.isSelected());
    if (myTemplatesListPanel != null) {
      myTemplatesListPanel.setEnabled(myPluginEnabledCheckbox.isSelected());
    }
  }
}