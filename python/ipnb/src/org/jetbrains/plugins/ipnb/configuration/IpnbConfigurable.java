package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class IpnbConfigurable implements SearchableConfigurable {
  private JPanel myMainPanel;
  private JBTextField myFieldUrl;
  @NotNull private final Project myProject;

  public IpnbConfigurable(@NotNull Project project) {
    myProject = project;
    myFieldUrl.setText(IpnbSettings.getInstance(myProject).getURL());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Jupyter Notebook";
  }

  @Override
  public String getHelpTopic() {
    return "reference-ipnb";
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    final String oldUrl = IpnbSettings.getInstance(myProject).getURL();
    final String url = StringUtil.trimEnd(myFieldUrl.getText(), "/");
    return !url.equals(oldUrl);
  }

  @Override
  public void apply() throws ConfigurationException {
    String url = myFieldUrl.getText();
    url = StringUtil.trimEnd(url, "/");
    IpnbSettings.getInstance(myProject).setURL(url);
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  @Override
  public String getId() {
    return "IpnbConfigurable";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }
}

