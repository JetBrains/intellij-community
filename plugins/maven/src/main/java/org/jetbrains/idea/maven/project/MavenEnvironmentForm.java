// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentType;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.MavenVersionSupportUtil;
import org.jetbrains.idea.maven.config.MavenConfig;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration;
import org.jetbrains.idea.maven.utils.MavenEelUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jetbrains.idea.maven.project.MavenHomeKt.*;

public class MavenEnvironmentForm implements PanelWithAnchor {
  private JPanel panel;
  private LabeledComponent<ComponentWithBrowseButton<TextFieldWithHistory>> mavenHomeComponent;
  private ContextHelpLabel mavenHomeOnTargetHelpLabel;
  private TextFieldWithHistory mavenHomeField;
  private LabeledComponent<JBLabel> mavenVersionLabelComponent;
  private LabeledComponent<TextFieldWithBrowseButton> settingsFileComponent;
  private LabeledComponent<TextFieldWithBrowseButton> localRepositoryComponent;
  private JCheckBox settingsOverrideCheckBox;
  private JCheckBox localRepositoryOverrideCheckBox;
  private JComponent anchor;

  private final PathOverrider userSettingsFileOverrider;
  private final PathOverrider localRepositoryOverrider;

  private boolean isUpdating = false;
  private final SingleEdtTaskScheduler updateAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
  private String myTargetName;
  private Project myProject;

  public MavenEnvironmentForm() {
    DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        UIUtil.invokeLaterIfNeeded(() -> {
          if (isUpdating) return;
          if (!panel.isShowing()) return;

          updateAlarm.cancelAndRequest(100, () -> {
            isUpdating = true;
            userSettingsFileOverrider.updateDefault();
            localRepositoryOverrider.updateDefault();
            isUpdating = false;
          });
        });
      }
    };

    userSettingsFileOverrider =
      new PathOverrider(settingsFileComponent, settingsOverrideCheckBox, listener, new PathProvider() {
        @Override
        @Nullable
        protected Path getFile() {
          return doResolveDefaultUserSettingsFile();
        }
      });

    localRepositoryOverrider =
      new PathOverrider(localRepositoryComponent, localRepositoryOverrideCheckBox, listener, new PathProvider() {
        @Override
        @Nullable
        protected Path getFile() {
          return doResolveDefaultLocalRepository();
        }
      });

    mavenHomeField.addDocumentListener(listener);
  }

  @NotNull
  private Path doResolveDefaultLocalRepository() {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    MavenConfig config = projectsManager != null ? projectsManager.getGeneralSettings().getMavenConfig() : null;
    return MavenEelUtil.getLocalRepo(myProject, "",
                                     staticOrBundled(resolveMavenHomeType(mavenHomeField.getText().trim())),
                                     settingsFileComponent.getComponent().getText(), config);
  }

  @NotNull
  private Path doResolveDefaultUserSettingsFile() {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    MavenConfig config = projectsManager != null ? projectsManager.getGeneralSettings().getMavenConfig() : null;
    return MavenEelUtil.getUserSettings(myProject, "", config);
  }

  private void createUIComponents() {
    mavenHomeField = new TextFieldWithHistory();
    mavenHomeField.setHistorySize(-1);
    final ArrayList<String> foundMavenHomes = new ArrayList<>();
    getAllKnownHomes().forEach(it -> foundMavenHomes.add(it.getTitle()));
    mavenHomeField.setHistory(foundMavenHomes);
    mavenHomeComponent = LabeledComponent.create(
      new ComponentWithBrowseButton<>(mavenHomeField, null), MavenConfigurableBundle.message("maven.settings.environment.home.directory"));
    mavenHomeOnTargetHelpLabel =
      ContextHelpLabel.create(MavenConfigurableBundle.message("maven.settings.on.targets.environment.home.directory.context.help"));
    mavenHomeOnTargetHelpLabel.setVisible(false);
    mavenHomeOnTargetHelpLabel.setOpaque(true);
    mavenHomeComponent.add(mavenHomeOnTargetHelpLabel, BorderLayout.EAST);

    final JBLabel versionLabel = new JBLabel();
    versionLabel.setOpaque(true);
    versionLabel.setVerticalAlignment(SwingConstants.TOP);
    versionLabel.setVerticalTextPosition(SwingConstants.TOP);
    mavenVersionLabelComponent = LabeledComponent.create(versionLabel, "");
  }

  public boolean isModified(MavenGeneralSettings data) {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(data);
  }

  public void setData(MavenGeneralSettings data) {
    data.setMavenHomeType(resolveMavenHomeType(mavenHomeField.getText().trim()));
    data.setUserSettingsFile(userSettingsFileOverrider.getResult());
    data.setLocalRepository(localRepositoryOverrider.getResult());
  }

  public void initializeFormData(MavenGeneralSettings data, Project project) {
    myProject = project;

    setAnchor(mavenHomeComponent.getLabel());

    String text = data.getMavenHomeType().getTitle();
    mavenHomeField.setText(text);
    updateMavenVersionLabel();
    userSettingsFileOverrider.reset(data.getUserSettingsFile());
    localRepositoryOverrider.reset(data.getLocalRepository());
  }


  private void updateMavenVersionLabel() {
    boolean localTarget = myTargetName == null;
    MavenHomeType type = resolveMavenHomeType(mavenHomeField.getText().trim());
    String version = null;
    if (type instanceof StaticResolvedMavenHomeType sType) {
      version = MavenUtil.getMavenVersion(sType);
    }
    String versionText = null;
    if (version != null) {
      if (StringUtil.compareVersionNumbers(version, "3.1") < 0) {
        versionText = getUnsupportedMavenMessage(version);
      }
      else {
        versionText = MavenProjectBundle.message("label.invalid.maven.home.version", version);
      }
    }
    else if (localTarget) {
      versionText = type.getTitle();
    }
    mavenVersionLabelComponent.getComponent().setText(StringUtil.notNullize(versionText));
  }

  @NlsContexts.Label
  private static String getUnsupportedMavenMessage(String version) {
    if (StringUtil.compareVersionNumbers(version, "3.1") < 0 && StringUtil.compareVersionNumbers(version, "2") > 0) {
      return MavenProjectBundle.message("label.invalid.maven30");
    }
    if (!MavenVersionSupportUtil.isMaven2PluginInstalled()) {
      return MavenProjectBundle.message("label.invalid.install.maven2plugin");
    }

    if (MavenVersionSupportUtil.isMaven2PluginDisabled()) {
      return MavenProjectBundle.message("label.invalid.enable.maven2plugin");
    }

    if (version == null) return MavenProjectBundle.message("label.invalid.maven.home.directory");
    return MavenProjectBundle.message("label.invalid.maven.home.version", version);
  }


  public JComponent createComponent() {
    // all listeners will be removed when dialog is closed
    var descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(MavenProjectBundle.message("maven.select.maven.home.directory"));
    mavenHomeComponent.getComponent().addBrowseFolderListener(null, descriptor, TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
    mavenHomeField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateMavenVersionLabel();
      }
    });

    settingsFileComponent.getComponent().addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle(MavenProjectBundle.message("maven.select.maven.settings.file")));
    localRepositoryComponent.getComponent().addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(MavenProjectBundle.message("maven.select.local.repository")));
    return panel;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    mavenHomeComponent.setAnchor(anchor);
    mavenVersionLabelComponent.setAnchor(anchor);
    settingsFileComponent.setAnchor(anchor);
    localRepositoryComponent.setAnchor(anchor);
  }

  @ApiStatus.Internal
  void apply(@NotNull Project project, @Nullable String targetName) {
    boolean localTarget = targetName == null;
    boolean targetChanged = !Objects.equals(myTargetName, targetName);
    if (targetChanged) {
      myTargetName = targetName;
      mavenHomeComponent.getComponent().getButton().setVisible(localTarget);
      mavenHomeOnTargetHelpLabel.setVisible(!localTarget);
      String mavenHomeInputLabel;
      if (localTarget) {
        mavenHomeInputLabel = MavenConfigurableBundle.message("maven.settings.environment.home.directory");
      }
      else {
        TargetEnvironmentsManager targetManager = TargetEnvironmentsManager.getInstance(project);
        TargetEnvironmentConfiguration targetEnvironmentConfiguration = targetManager.getTargets().findByName(targetName);
        String typeId = targetEnvironmentConfiguration != null ? targetEnvironmentConfiguration.getTypeId() : null;
        TargetEnvironmentType<?> targetEnvironmentType = null;
        if (typeId != null) {
          targetEnvironmentType = TargetEnvironmentType.EXTENSION_NAME.findFirstSafe(type -> type.getId().equals(typeId));
        }
        if (targetEnvironmentType != null) {
          // wrap the text to avoid label ellipsis
          mavenHomeInputLabel = MessageFormat.format("<html><body><nobr>{0}</nobr></body></html>",
                                                     MavenConfigurableBundle.message("maven.settings.on.targets.environment.home.directory",
                                                                                     targetEnvironmentType.getDisplayName()));
        }
        else {
          mavenHomeInputLabel = MavenConfigurableBundle.message("maven.settings.environment.home.directory");
        }
      }
      mavenHomeComponent.setText(mavenHomeInputLabel);
      reloadMavenHomeComponents(project, targetName);
    }
    else if (!localTarget) {
      reloadMavenHomeComponents(project, targetName);
    }
  }

  private void reloadMavenHomeComponents(@NotNull Project project, @Nullable String targetName) {
    List<String> targetMavenHomes = findTargetMavenHomes(project, targetName);
    if (!mavenHomeField.getHistory().equals(targetMavenHomes)) {
      EdtInvocationManager.getInstance().invokeLater(() -> mavenHomeField.setHistory(targetMavenHomes));
    }
    String mavenHomeFieldText = mavenHomeField.getText();
    if (targetMavenHomes.isEmpty()) {
      if (!mavenHomeFieldText.isEmpty()) {
        EdtInvocationManager.getInstance().invokeLater(() -> mavenHomeField.setSelectedItem(""));
      }
    }
    else if (!targetMavenHomes.contains(mavenHomeFieldText)) {
      EdtInvocationManager.getInstance().invokeLater(() -> {
        if (!targetMavenHomes.contains(mavenHomeField.getText())) {
          mavenHomeField.setSelectedItem(targetMavenHomes.get(0));
        }
      });
    }
  }

  private static List<String> findTargetMavenHomes(@NotNull Project project, @Nullable String targetName) {
    List<String> mavenHomes;
    boolean localTarget = targetName == null;
    if (localTarget) {
      mavenHomes = new ArrayList<>();
      MavenUtil.getSystemMavenHomeVariants(project).forEach(it -> {
        mavenHomes.add(it.getTitle());
      });
    }
    else {
      TargetEnvironmentConfiguration targetEnvironmentConfiguration =
        TargetEnvironmentsManager.getInstance(project).getTargets().findByName(targetName);
      if (targetEnvironmentConfiguration != null) {
        mavenHomes = targetEnvironmentConfiguration.getRuntimes().resolvedConfigs().stream()
          .filter(runtimeConfiguration -> runtimeConfiguration instanceof MavenRuntimeTargetConfiguration)
          .map(runtimeConfiguration -> ((MavenRuntimeTargetConfiguration)runtimeConfiguration).getHomePath())
          .collect(Collectors.toList());
      }
      else {
        mavenHomes = new ArrayList<>();
      }
    }
    return mavenHomes;
  }

  private static abstract class PathProvider {
    @NlsSafe
    public String getPath() {
      final Path file = getFile();
      return file == null ? "" : file.toString();
    }

    @Nullable
    abstract protected Path getFile();
  }

  private static class PathOverrider {
    private final TextFieldWithBrowseButton component;
    private final JCheckBox checkBox;
    private final PathProvider pathProvider;

    private Boolean isOverridden;
    private @NlsSafe String overrideText;

    PathOverrider(final LabeledComponent<TextFieldWithBrowseButton> component,
                  final JCheckBox checkBox,
                  DocumentListener docListener,
                  PathProvider pathProvider) {
      this.component = component.getComponent();
      this.component.getTextField().getDocument().addDocumentListener(docListener);
      this.checkBox = checkBox;
      this.pathProvider = pathProvider;
      checkBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          update();
        }
      });
    }

    private void update() {
      final boolean override = checkBox.isSelected();
      if (Comparing.equal(isOverridden, override)) return;

      isOverridden = override;

      component.setEditable(override);
      component.setEnabled(override && checkBox.isEnabled());

      if (override) {
        if (overrideText != null) component.setText(overrideText);
      }
      else {
        if (!StringUtil.isEmptyOrSpaces(component.getText())) overrideText = component.getText();
        component.setText(pathProvider.getPath());
      }
    }

    private void updateDefault() {
      if (!checkBox.isSelected()) {
        component.setText(pathProvider.getPath());
      }
    }

    public void reset(@NlsSafe String text) {
      isOverridden = null;
      checkBox.setSelected(!StringUtil.isEmptyOrSpaces(text));
      overrideText = StringUtil.isEmptyOrSpaces(text) ? null : text;
      update();
    }

    public String getResult() {
      return checkBox.isSelected() ? component.getText().trim() : "";
    }
  }
}
