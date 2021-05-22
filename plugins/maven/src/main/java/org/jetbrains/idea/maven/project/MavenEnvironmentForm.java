// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentType;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRCSettingsWatcher;
import org.jetbrains.idea.maven.execution.MavenSettingsObservable;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.MavenWslUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MavenEnvironmentForm implements PanelWithAnchor, MavenSettingsObservable {
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
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private String myTargetName;
  private Project myProject;

  public MavenEnvironmentForm() {
    DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        UIUtil.invokeLaterIfNeeded(() -> {
          if (isUpdating) return;
          if (!panel.isShowing()) return;

          myUpdateAlarm.cancelAllRequests();
          myUpdateAlarm.addRequest(() -> {
            isUpdating = true;
            userSettingsFileOverrider.updateDefault();
            localRepositoryOverrider.updateDefault();
            isUpdating = false;
          }, 100);
        });
      }
    };

    userSettingsFileOverrider =
      new PathOverrider(settingsFileComponent, settingsOverrideCheckBox, listener, new PathProvider() {
        @Override
        @Nullable
        protected File getFile() {
          return doResolveDefaultUserSettingsFile();
        }
      });

    localRepositoryOverrider =
      new PathOverrider(localRepositoryComponent, localRepositoryOverrideCheckBox, listener, new PathProvider() {
        @Override
        @Nullable
        protected File getFile() {
          return doResolveDefaultLocalRepository();
        }
      });

    mavenHomeField.addDocumentListener(listener);

  }

  @NotNull
  private File doResolveDefaultLocalRepository() {
    return MavenWslUtil.resolveWslAware(myProject,
                                        () -> MavenUtil.resolveLocalRepository("",
                                                                               FileUtil.toSystemIndependentName(
                                                                                 mavenHomeField.getText().trim()),
                                                                               settingsFileComponent.getComponent().getText()),
                                        wsl -> MavenWslUtil.resolveLocalRepository(wsl, "",
                                                                                   FileUtil.toSystemIndependentName(
                                                                                     mavenHomeField.getText().trim()),
                                                                                   settingsFileComponent.getComponent().getText()));
  }

  @NotNull
  private File doResolveDefaultUserSettingsFile() {
    return MavenWslUtil.resolveWslAware(myProject,
                                        () -> MavenUtil.resolveUserSettingsFile(""),
                                        wsl -> MavenWslUtil.resolveUserSettingsFile(wsl, ""));
  }

  private void createUIComponents() {
    mavenHomeField = new TextFieldWithHistory();
    mavenHomeField.setHistorySize(-1);
    final ArrayList<String> foundMavenHomes = new ArrayList<>();
    foundMavenHomes.add(MavenServerManager.BUNDLED_MAVEN_3);
    foundMavenHomes.add(MavenServerManager.WRAPPED_MAVEN);
    final File mavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(null);
    final File bundledMavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(MavenServerManager.BUNDLED_MAVEN_3);
    if (mavenHomeDirectory != null && ! FileUtil.filesEqual(mavenHomeDirectory, bundledMavenHomeDirectory)) {
      foundMavenHomes.add(FileUtil.toSystemIndependentName(mavenHomeDirectory.getPath()));
    }
    mavenHomeField.setHistory(foundMavenHomes);
    mavenHomeComponent = LabeledComponent.create(
      new ComponentWithBrowseButton<>(mavenHomeField, null), MavenConfigurableBundle.message("maven.settings.environment.home.directory"));
    mavenHomeOnTargetHelpLabel = ContextHelpLabel.create(MavenConfigurableBundle.message("maven.settings.on.targets.environment.home.directory.context.help"));
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
    data.setMavenHome(FileUtil.toSystemIndependentName(mavenHomeField.getText().trim()));
    data.setUserSettingsFile(userSettingsFileOverrider.getResult());
    data.setLocalRepository(localRepositoryOverrider.getResult());
  }

  public void initializeFormData(MavenGeneralSettings data, Project project) {
    myProject = project;

    setAnchor(mavenHomeComponent.getLabel());

    String text = MavenWslUtil.resolveWslAware(project,
                                               () -> {
                                                 final String resolvedMavenHome = resolveMavenHome(data.getMavenHome());
                                                 final String mavenHome = ObjectUtils.chooseNotNull(resolvedMavenHome, data.getMavenHome());
                                                 if (MavenServerManager.BUNDLED_MAVEN_3.equals(mavenHome)) {
                                                   return MavenProjectBundle.message("maven.bundled.version.title");
                                                 }
                                                 if (MavenServerManager.WRAPPED_MAVEN.equals(mavenHome)) {
                                                   return MavenProjectBundle.message("maven.wrapper.version.title");
                                                 }
                                                 return FileUtil.toSystemIndependentName(mavenHome);
                                               },
                                               wsl -> {
                                                 File home = MavenWslUtil.resolveMavenHomeDirectory(wsl, data.getMavenHome());
                                                 return home == null ? null : home.getAbsolutePath();
                                               });

    mavenHomeField.setText(text);
    mavenHomeField.addCurrentTextToHistory();
    updateMavenVersionLabel();
    userSettingsFileOverrider.reset(data.getUserSettingsFile());
    localRepositoryOverrider.reset(data.getLocalRepository());
  }

  @Override
  public void registerSettingsWatcher(@NotNull MavenRCSettingsWatcher watcher) {
    watcher.registerComponent("mavenHome", mavenHomeField);
    watcher.registerComponent("settingsFileOverride.checkbox", userSettingsFileOverrider.checkBox);
    watcher.registerComponent("settingsFileOverride.text", userSettingsFileOverrider.component);
    watcher.registerComponent("localRepoOverride.checkbox", localRepositoryOverrider.checkBox);
    watcher.registerComponent("localRepoOverride.text", localRepositoryOverrider.component);
  }

  @Nullable
  private static String resolveMavenHome(@Nullable String mavenHome) {
    if (StringUtil.equals(MavenServerManager.BUNDLED_MAVEN_3, mavenHome) || StringUtil.equals(MavenServerManager.WRAPPED_MAVEN, mavenHome)) {
      return mavenHome;
    }
    final File mavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(mavenHome);
    return mavenHomeDirectory != null ? mavenHomeDirectory.getPath() : null;
  }

  private void updateMavenVersionLabel() {
    boolean localTarget = myTargetName == null;
    String version = MavenUtil.getMavenVersion(MavenServerManager.getMavenHomeFile(getMavenHome()));
    String versionText = null;
    if (version != null) {
      versionText = MavenProjectBundle.message("label.invalid.maven.home.version", version);
    } else if (StringUtil.equals(MavenProjectBundle.message("maven.wrapper.version.title"), mavenHomeField.getText())) {
      versionText = MavenProjectBundle.message("maven.wrapper.version.label", version);
    }
    else if(localTarget) {
      versionText = MavenProjectBundle.message("label.invalid.maven.home.directory");
    }
    mavenVersionLabelComponent.getComponent().setText(StringUtil.notNullize(versionText));
  }

  @Nullable
  public String getMavenHome() {
    String mavenHome = FileUtil.toSystemIndependentName(mavenHomeField.getText().trim());
    final File mavenHomeFile = MavenServerManager.getMavenHomeFile(mavenHome);
    return mavenHomeFile != null ? mavenHomeFile.getPath() : null;
  }

  public JComponent createComponent() {
    // all listeners will be removed when dialog is closed
    mavenHomeComponent.getComponent().addBrowseFolderListener(MavenProjectBundle.message("maven.select.maven.home.directory"),
                                                              "",
                                                              null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR,
                                                              TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
    mavenHomeField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateMavenVersionLabel();
      }
    });

    settingsFileComponent.getComponent().addBrowseFolderListener(MavenProjectBundle.message("maven.select.maven.settings.file"), "", null,
                                                                 FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    localRepositoryComponent.getComponent().addBrowseFolderListener(MavenProjectBundle.message("maven.select.local.repository"), "", null,
                                                                    FileChooserDescriptorFactory.createSingleFolderDescriptor());
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
          targetEnvironmentType = TargetEnvironmentType.EXTENSION_NAME.findFirstSafe(type -> type.getId() == typeId);
        }
        if (targetEnvironmentType != null) {
          // wrap the text to avoid label ellipsis
          mavenHomeInputLabel = MessageFormat.format("<html><body><nobr>{0}</nobr></body></html>",
                                                     MavenConfigurableBundle.message("maven.settings.on.targets.environment.home.directory",
                                                                                     targetEnvironmentType.getDisplayName()));
        } else {
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
    List<String> mavenHomes = new ArrayList<>();
    boolean localTarget = targetName == null;
    if (localTarget) {
      mavenHomes.add(MavenServerManager.BUNDLED_MAVEN_3);
      final File mavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(null);
      if (mavenHomeDirectory != null) {
        mavenHomes.add(FileUtil.toSystemIndependentName(mavenHomeDirectory.getPath()));
      }
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
    }
    return mavenHomes;
  }

  private static abstract class PathProvider {
    @NlsSafe
    public String getPath() {
      final File file = getFile();
      return file == null ? "" : file.getPath();
    }

    @Nullable
    abstract protected File getFile();
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
