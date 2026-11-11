// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentType;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
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

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jetbrains.idea.maven.project.MavenHomeKt.getAllKnownHomes;
import static org.jetbrains.idea.maven.project.MavenHomeKt.resolveMavenHomeType;
import static org.jetbrains.idea.maven.project.MavenHomeKt.staticOrBundled;

public class MavenEnvironmentForm {
  private final MavenEnvironmentFormUi ui = new MavenEnvironmentFormUi();

  private final PathOverrider userSettingsFileOverrider;
  private final PathOverrider localRepositoryOverrider;

  private boolean isUpdating = false;
  private final SingleEdtTaskScheduler updateAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
  private String myTargetName;
  private Project myProject;

  public MavenEnvironmentForm() {
    ui.mavenHomeField.setHistorySize(-1);
    final ArrayList<String> foundMavenHomes = new ArrayList<>();
    getAllKnownHomes().forEach(it -> foundMavenHomes.add(it.getTitle()));
    ui.mavenHomeField.setHistory(foundMavenHomes);

    DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        UIUtil.invokeLaterIfNeeded(() -> {
          if (isUpdating) return;
          if (!ui.panel.isShowing()) return;

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
      new PathOverrider(ui.settingsFileField, ui.settingsOverrideCheckBox, listener, new PathProvider() {
        @Override
        protected @Nullable Path getFile() {
          return doResolveDefaultUserSettingsFile();
        }
      });

    localRepositoryOverrider =
      new PathOverrider(ui.localRepositoryField, ui.localRepositoryOverrideCheckBox, listener, new PathProvider() {
        @Override
        protected @Nullable Path getFile() {
          return doResolveDefaultLocalRepository();
        }
      });

    ui.mavenHomeField.addDocumentListener(listener);
  }

  private @NotNull Path doResolveDefaultLocalRepository() {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    MavenConfig config = projectsManager != null ? projectsManager.getGeneralSettings().getMavenConfig() : null;
    return MavenEelUtil.getLocalRepoUnderModalProgress(myProject, "",
                                                       staticOrBundled(resolveMavenHomeType(ui.mavenHomeField.getText().trim())),
                                                       ui.settingsFileField.getText(), config);
  }

  private @NotNull Path doResolveDefaultUserSettingsFile() {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    MavenConfig config = projectsManager != null ? projectsManager.getGeneralSettings().getMavenConfig() : null;
    return MavenEelUtil.getUserSettingsUnderModalProgress(myProject, "", config);
  }

  public boolean isModified(MavenGeneralSettings data) {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(data);
  }

  public void setData(MavenGeneralSettings data) {
    data.setMavenHomeType(resolveMavenHomeType(ui.mavenHomeField.getText().trim()));
    data.setUserSettingsFile(userSettingsFileOverrider.getResult());
    data.setLocalRepository(localRepositoryOverrider.getResult());
  }

  public void initializeFormData(MavenGeneralSettings data, Project project) {
    if (myProject == null  && project != null) {
      setupFileChooser(project);
    }
    myProject = project;

    String text = data.getMavenHomeType().getTitle();
    ui.mavenHomeField.setText(text);
    updateMavenVersionLabel();
    userSettingsFileOverrider.reset(data.getUserSettingsFile());
    localRepositoryOverrider.reset(data.getLocalRepository());
  }


  private void updateMavenVersionLabel() {
    boolean localTarget = myTargetName == null;
    MavenHomeType type = resolveMavenHomeType(ui.mavenHomeField.getText().trim());
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
    ui.mavenVersionLabel.setText(StringUtil.notNullize(versionText));
  }

  private static @NlsContexts.Label String getUnsupportedMavenMessage(String version) {
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
    ui.mavenHomeField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateMavenVersionLabel();
      }
    });
    return ui.panel;
  }

  private void setupFileChooser(@NotNull Project project) {
    var descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(MavenProjectBundle.message("maven.select.maven.home.directory"));
    ui.mavenHomeComponent.addBrowseFolderListener(project, descriptor, TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
    ui.settingsFileField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle(MavenProjectBundle.message("maven.select.maven.settings.file"))
      .withEnvironmentRestricted(true));
    ui.localRepositoryField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(MavenProjectBundle.message("maven.select.local.repository"))
      .withEnvironmentRestricted(true));
  }

  @ApiStatus.Internal
  void apply(@NotNull Project project, @Nullable String targetName) {
    boolean localTarget = targetName == null;
    boolean targetChanged = !Objects.equals(myTargetName, targetName);
    if (targetChanged) {
      myTargetName = targetName;
      ui.mavenHomeComponent.setButtonVisible(localTarget);
      ui.mavenHomeOnTargetHelpLabel.visible(!localTarget);
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
          mavenHomeInputLabel = MavenConfigurableBundle.message("maven.settings.on.targets.environment.home.directory",
                                                                targetEnvironmentType.getDisplayName());
        }
        else {
          mavenHomeInputLabel = MavenConfigurableBundle.message("maven.settings.environment.home.directory");
        }
      }
      ui.mavenHomeLabel.setText(mavenHomeInputLabel);
      reloadMavenHomeComponents(project, targetName);
    }
    else if (!localTarget) {
      reloadMavenHomeComponents(project, targetName);
    }
  }

  private void reloadMavenHomeComponents(@NotNull Project project, @Nullable String targetName) {
    List<String> targetMavenHomes = findTargetMavenHomes(project, targetName);
    if (!ui.mavenHomeField.getHistory().equals(targetMavenHomes)) {
      EdtInvocationManager.getInstance().invokeLater(() -> ui.mavenHomeField.setHistory(targetMavenHomes));
    }
    String mavenHomeFieldText = ui.mavenHomeField.getText();
    if (targetMavenHomes.isEmpty()) {
      if (!mavenHomeFieldText.isEmpty()) {
        EdtInvocationManager.getInstance().invokeLater(() -> ui.mavenHomeField.setSelectedItem(""));
      }
    }
    else if (!targetMavenHomes.contains(mavenHomeFieldText)) {
      EdtInvocationManager.getInstance().invokeLater(() -> {
        if (!targetMavenHomes.contains(ui.mavenHomeField.getText())) {
          ui.mavenHomeField.setSelectedItem(targetMavenHomes.get(0));
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

  private abstract static class PathProvider {
    public @NlsSafe String getPath() {
      final Path file = getFile();
      return file == null ? "" : file.toString();
    }

    protected abstract @Nullable Path getFile();
  }

  private static class PathOverrider {
    private final TextFieldWithBrowseButton component;
    private final JCheckBox checkBox;
    private final PathProvider pathProvider;

    private Boolean isOverridden;
    private @NlsSafe String overrideText;

    PathOverrider(final TextFieldWithBrowseButton component,
                  final JCheckBox checkBox,
                  DocumentListener docListener,
                  PathProvider pathProvider) {
      this.component = component;
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
