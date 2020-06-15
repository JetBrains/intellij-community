// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Optional;

public final class MarkdownSettingsConfigurable implements SearchableConfigurable {
  static final String PLANT_UML_DIRECTORY = "plantUML";
  static final String PLANTUML_JAR_URL = Registry.stringValue("markdown.plantuml.download.link");
  static final String PLANTUML_JAR = "plantuml.jar";

  private static final String DOWNLOAD_CACHE_DIRECTORY = "download-cache";
  @TestOnly
  public static final Ref<VirtualFile> PLANTUML_JAR_TEST = Ref.create();
  @Nullable
  private MarkdownSettingsForm myForm = null;
  @NotNull
  private final MarkdownApplicationSettings myMarkdownApplicationSettings;

  public MarkdownSettingsConfigurable() {
    myMarkdownApplicationSettings = MarkdownApplicationSettings.getInstance();
  }

  @NotNull
  @Override
  public String getId() {
    return "Settings.Markdown";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return MarkdownBundle.message("markdown.settings.name");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    MarkdownSettingsForm form = getForm();
    if (form == null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(MarkdownBundle.message("markdown.settings.no.providers")), BorderLayout.NORTH);
      return panel;
    }
    return form.getComponent();
  }

  @Nullable
  public MarkdownSettingsForm getForm() {
    if (!MarkdownHtmlPanelProvider.hasAvailableProviders()) {
      return null;
    }

    if (myForm == null) {
      myForm = new MarkdownSettingsForm();
    }
    return myForm;
  }

  @Override
  public boolean isModified() {
    MarkdownSettingsForm form = getForm();
    if (form == null) {
      return false;
    }
    return !form.getMarkdownCssSettings().equals(myMarkdownApplicationSettings.getMarkdownCssSettings()) ||
           !form.getMarkdownPreviewSettings().equals(myMarkdownApplicationSettings.getMarkdownPreviewSettings()) ||
           form.isDisableInjections() != myMarkdownApplicationSettings.isDisableInjections() ||
           form.isHideErrors() != myMarkdownApplicationSettings.isHideErrors();
  }

  @Override
  public void apply() throws ConfigurationException {
    final MarkdownSettingsForm form = getForm();
    if (form == null) {
      return;
    }

    form.validate();

    myMarkdownApplicationSettings.setMarkdownCssSettings(form.getMarkdownCssSettings());
    myMarkdownApplicationSettings.setMarkdownPreviewSettings(form.getMarkdownPreviewSettings());
    myMarkdownApplicationSettings.setDisableInjections(form.isDisableInjections());
    myMarkdownApplicationSettings.setHideErrors(form.isHideErrors());

    ApplicationManager.getApplication().getMessageBus().syncPublisher(MarkdownApplicationSettings.SettingsChangedListener.TOPIC)
      .settingsChanged(myMarkdownApplicationSettings);
  }

  @Override
  public void reset() {
    MarkdownSettingsForm form = getForm();
    if (form == null) {
      return;
    }
    form.setMarkdownCssSettings(myMarkdownApplicationSettings.getMarkdownCssSettings());
    form.setMarkdownPreviewSettings(myMarkdownApplicationSettings.getMarkdownPreviewSettings());
    form.setDisableInjections(myMarkdownApplicationSettings.isDisableInjections());
    form.setHideErrors(myMarkdownApplicationSettings.isHideErrors());
  }

  @Override
  public void disposeUIResources() {
    if (myForm != null) {
      Disposer.dispose(myForm);
    }
    myForm = null;
  }

  /**
   * Returns true if PlantUML jar has been already downloaded
   */
  public static boolean isPlantUMLAvailable() {
    File jarPath = getDownloadedJarPath();
    return jarPath != null && jarPath.exists();
  }

  /**
   * Gets 'download-cache' directory PlantUML jar to be download to
   */
  @NotNull
  public static File getDirectoryToDownload() {
    return new File(PathManager.getSystemPath(), DOWNLOAD_CACHE_DIRECTORY + "/" + PLANT_UML_DIRECTORY);
  }

  /**
   * Gets expected by Markdown plugin path to PlantUML JAR
   */
  @NotNull
  public static File getExpectedJarPath() {
    return new File(getDirectoryToDownload(), PLANTUML_JAR);
  }

  /**
   * Returns {@link File} presentation of downloaded PlantUML jar
   */
  @Nullable
  public static File getDownloadedJarPath() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection TestOnlyProblems
      return Optional.ofNullable(PLANTUML_JAR_TEST.get()).map(VfsUtilCore::virtualToIoFile).orElse(null);
    }
    else {
      return getExpectedJarPath();
    }
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "Settings.Markdown";
  }
}
