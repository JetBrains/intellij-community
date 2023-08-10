// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework.ui;

import com.google.common.io.Closeables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout;
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator;
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle;
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription;
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.jetbrains.kotlin.idea.configuration.ConfigureKotlinInProjectUtilsKt.*;

public class ConfigureDialogWithModulesAndVersion extends DialogWrapper implements ModulesChangedObserver {
    private static final String VERSIONS_LIST_URL =
            "https://search.maven.org/solrsearch/select?q=g:%22org.jetbrains.kotlin%22+AND+a:%22kotlin-stdlib%22&core=gav&rows=20&wt=json";

    /* We define the version here because we cannot properly use such a constant from org.jetbrains.kotlin.tools.projectWizard Versions
    because the current package is not a plugin and there are dependency issues if trying to make it a plugin */
    private static final String DEFAULT_KOTLIN_VERSION = "1.8.21";

    @NotNull private final String minimumVersion;

    private final Map<String, Map<String, Module>> kotlinVersionsAndModules;
    @Nullable private final String rootModuleVersion;
    @Nullable private final Module rootModule;

    private final Map<String, List<String>> jvmModulesTargetingUnsupportedJvm;
    private final Map<String, String> modulesAndJvmTargets;

    private final ChooseModulePanel chooseModulePanel;

    private JPanel contentPane;
    private JPanel chooseModulesPanelPlace;
    private JComboBox<String> kotlinVersionComboBox;
    private JPanel infoPanel;
    private JTextPane listOfKotlinVersionsAndModules;
    private JTextPane deprecatedJvmTargetsUsedWarning;

    private final AsyncProcessIcon processIcon = new AsyncProcessIcon("loader");

    public ConfigureDialogWithModulesAndVersion(
            @NotNull Project project,
            @NotNull KotlinProjectConfigurator configurator,
            @NotNull Collection<Module> excludeModules,
            @NotNull String minimumVersion
    ) {
        super(project);

        KotlinJ2KOnboardingFUSCollector.logShowConfigureKtWindow(project);

        var kotlinVersions = getKotlinVersionsAndModules(project, configurator);
        kotlinVersionsAndModules = kotlinVersions.getFirst();
        rootModuleVersion = kotlinVersions.getSecond();

        setTitle(KotlinProjectConfigurationBundle.message("configure.kotlin.title", configurator.getPresentableText()));

        this.minimumVersion = minimumVersion;
        init();

        ProgressManager.getInstance().run(new Task.Backgroundable(
                project, KotlinProjectConfigurationBundle.message("configure.kotlin.find.maven.versions"), false
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                loadKotlinVersions();
            }
        });

        kotlinVersionComboBox.addActionListener(e -> updateComponents());

        kotlinVersionComboBox.addItem(KotlinProjectConfigurationBundle.message("configure.kotlin.loading"));
        kotlinVersionComboBox.setEnabled(false);

        processIcon.resume();
        infoPanel.add(processIcon, BorderLayout.CENTER);

        chooseModulePanel = new ChooseModulePanel(project, configurator, excludeModules);
        chooseModulesPanelPlace.add(chooseModulePanel.getContentPane(), BorderLayout.CENTER);

        var compatibility = checkModuleJvmTargetCompatibility(chooseModulePanel.getModules(), IdeKotlinVersion.get(DEFAULT_KOTLIN_VERSION));
        jvmModulesTargetingUnsupportedJvm = compatibility.getModulesByIncompatibleJvmTarget();
        modulesAndJvmTargets = compatibility.getModuleJvmTargets();

        rootModule = getRootModule(project);

        chooseModulePanel.setActionListeners(this);

        kotlinVersionComboBox.addItemListener(e -> showWarningIfThereAreDifferentKotlinVersions());
        showUnsupportedJvmTargetWarning();
        updateComponents();
    }

    @Override
    public void onModulesChangedNotified() {
        showWarningIfThereAreDifferentKotlinVersions();
    }

    private static final int MODULES_TO_DISPLAY_SIZE = 2;
    private static final String DELIMITER = ", ";

    private void showWarningIfThereAreDifferentKotlinVersions() {
        String currentSelectedKotlinVersion = getKotlinVersion();
        List<Module> modulesToConfigure = chooseModulePanel.getModulesToConfigure();
        if (!kotlinVersionsAndModules.isEmpty() && currentSelectedKotlinVersion != null && !modulesToConfigure.isEmpty()) {
            listOfKotlinVersionsAndModules.setText("");

            if (modulesToConfigure.contains(rootModule) &&
                !(kotlinVersionsAndModules.size() == 1 && kotlinVersionsAndModules.containsKey(currentSelectedKotlinVersion))) {
                createMessageAboutDifferentKotlinVersions(currentSelectedKotlinVersion);
            } else if (rootModuleVersion != null &&
                       !IdeKotlinVersion.get(rootModuleVersion).equals(IdeKotlinVersion.get(currentSelectedKotlinVersion))) {
                createMessageThatTopLevelAndModulesShouldHaveSameVersion();
            } else {
                listOfKotlinVersionsAndModules.setVisible(false);
            }
        } else {
            listOfKotlinVersionsAndModules.setVisible(false);
        }
    }

    private void createMessageThatTopLevelAndModulesShouldHaveSameVersion() {
        String message = KotlinProjectConfigurationBundle.message("configure.kotlin.root.contains.another.kotlin", rootModuleVersion) +
                         KotlinProjectConfigurationBundle.message("configure.kotlin.root.should.contain.same.version") +
                         KotlinProjectConfigurationBundle.message("configure.kotlin.choose.the.same.kotlin.version", rootModuleVersion);
        Messages.installHyperlinkSupport(listOfKotlinVersionsAndModules);
        listOfKotlinVersionsAndModules.setText(message);
        listOfKotlinVersionsAndModules.setVisible(true);
    }

    private void createMessageAboutDifferentKotlinVersions(String currentSelectedKotlinVersion) {
        final StringBuilder message = new StringBuilder();
        message.append(KotlinProjectConfigurationBundle.message("configure.kotlin.root.should.contain.same.version"));
        message.append(KotlinProjectConfigurationBundle.message("configure.kotlin.currently.there.are.versions"));

        kotlinVersionsAndModules.keySet().stream().filter(it -> !it.equals(currentSelectedKotlinVersion)).sorted()
                .forEach(it -> {
                             Map<String, Module> modules = kotlinVersionsAndModules.get(it);
                             Set<String> modulesNames = modules.keySet();
                             StringBuilder modulesEnumeration = new StringBuilder();
                             if (modulesNames.size() > MODULES_TO_DISPLAY_SIZE) {
                                 modulesEnumeration.append(
                                         modulesNames.stream().limit(MODULES_TO_DISPLAY_SIZE).sorted()
                                                 .collect(Collectors.joining(DELIMITER)));
                                 modulesEnumeration.append(
                                         KotlinProjectConfigurationBundle.message("configure.kotlin.version.and.modules.and.more",
                                                                                  modulesNames.size() -
                                                                                  MODULES_TO_DISPLAY_SIZE));
                             } else {
                                 modulesEnumeration.append(modulesNames.stream().sorted().collect(Collectors.joining(DELIMITER)));
                             }
                             message.append(KotlinProjectConfigurationBundle
                                                    .message("configure.kotlin.version.and.modules", it,
                                                             modulesEnumeration.toString()));
                         }
                );

        message.append(KotlinProjectConfigurationBundle.message("configure.kotlin.choose.another.kotlin.version"));
        Messages.installHyperlinkSupport(listOfKotlinVersionsAndModules);
        // It's not hardcoded, we take strings from resources
        //noinspection HardCodedStringLiteral
        listOfKotlinVersionsAndModules.setText(message.toString());
        listOfKotlinVersionsAndModules.setVisible(true);
    }

    private void showUnsupportedJvmTargetWarning() {
        if (!jvmModulesTargetingUnsupportedJvm.isEmpty()) {
            final StringBuilder message = new StringBuilder();
            message.append(KotlinProjectConfigurationBundle.message("configurator.kotlin.jvm.targets.unsupported", "1.8"));
            jvmModulesTargetingUnsupportedJvm
                    .keySet()
                    .stream()
                    .sorted()
                    .forEach(jvmTargetVersion ->
                             {
                                 List<String> modulesWithThisTargetVersion = jvmModulesTargetingUnsupportedJvm.get(jvmTargetVersion);
                                 StringBuilder modulesEnumeration = new StringBuilder();
                                 if (modulesWithThisTargetVersion.size() > MODULES_TO_DISPLAY_SIZE) {
                                     modulesEnumeration.append(
                                             modulesWithThisTargetVersion.stream().limit(MODULES_TO_DISPLAY_SIZE).sorted()
                                                     .collect(Collectors.joining(DELIMITER)));
                                     modulesEnumeration.append(
                                             KotlinProjectConfigurationBundle.message("configure.kotlin.jvm.target.in.modules.and.more",
                                                                                      modulesWithThisTargetVersion.size() -
                                                                                      MODULES_TO_DISPLAY_SIZE));
                                 } else {
                                     modulesEnumeration.append(
                                             modulesWithThisTargetVersion.stream().sorted().collect(Collectors.joining(DELIMITER)));
                                 }
                                 message.append(KotlinProjectConfigurationBundle.message("configurator.kotlin.jvm.target.in.modules",
                                                                                         jvmTargetVersion, modulesEnumeration.toString()));
                             }
                    );
            message.append(KotlinProjectConfigurationBundle.message("configurator.kotlin.jvm.target.bump.manually.learn.more"));
            Messages.installHyperlinkSupport(deprecatedJvmTargetsUsedWarning);
            // It's not hardcoded, we take strings from resources
            //noinspection HardCodedStringLiteral
            deprecatedJvmTargetsUsedWarning.setText(message.toString());

            deprecatedJvmTargetsUsedWarning.setVisible(true);
        } else {
            deprecatedJvmTargetsUsedWarning.setVisible(false);
        }
    }

    public List<Module> getModulesToConfigure() {
        return chooseModulePanel.getModulesToConfigure();
    }

    public String getKotlinVersion() {
        return (String) kotlinVersionComboBox.getSelectedItem();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

    private void loadKotlinVersions() {
        Collection<String> items;
        try {
            items = loadVersions(minimumVersion);
            hideLoader();
        } catch (Throwable t) {
            items = Collections.singletonList("1.0.0");
            showWarning();
        }
        updateVersions(items);
    }

    private void hideLoader() {
        ApplicationManager.getApplication().invokeLater(() -> {
            infoPanel.setVisible(false);
            infoPanel.updateUI();
        }, ModalityState.stateForComponent(infoPanel));
    }

    private void showWarning() {
        ApplicationManager.getApplication().invokeLater(() -> {
            infoPanel.remove(processIcon);
            infoPanel.add(new JLabel(UIUtil.getBalloonWarningIcon()), BorderLayout.CENTER);
            infoPanel.setToolTipText(KotlinProjectConfigurationBundle.message("configure.kotlin.cant.load.versions"));
            infoPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            infoPanel.updateUI();
        }, ModalityState.stateForComponent(infoPanel));
    }

    private void updateVersions(@NotNull final Collection<String> newItems) {
        ApplicationManager.getApplication().invokeLater(() -> {
            kotlinVersionComboBox.removeAllItems();
            kotlinVersionComboBox.setEnabled(true);
            for (@NlsSafe String newItem : newItems) {
                kotlinVersionComboBox.addItem(newItem);
            }
            kotlinVersionComboBox.setSelectedIndex(0);
        }, ModalityState.stateForComponent(kotlinVersionComboBox));
    }

    @NotNull
    public static Collection<String> loadVersions(String minimumVersion) throws IOException {
        List<String> versions = new ArrayList<>();

        IdeKotlinVersion kotlinCompilerVersion = KotlinPluginLayout.getStandaloneCompilerVersion();
        String kotlinArtifactVersion = kotlinCompilerVersion.getArtifactVersion();

        RepositoryDescription repositoryDescription = getRepositoryForVersion(kotlinCompilerVersion);
        if (repositoryDescription != null && repositoryDescription.getBintrayUrl() != null) {
            HttpURLConnection eapConnection =
                    HttpConfigurable.getInstance().openHttpConnection(repositoryDescription.getBintrayUrl() + kotlinArtifactVersion);
            try {
                int timeout = (int) TimeUnit.SECONDS.toMillis(30);
                eapConnection.setConnectTimeout(timeout);
                eapConnection.setReadTimeout(timeout);

                if (eapConnection.getResponseCode() == 200) {
                    versions.add(kotlinArtifactVersion);
                }
            } finally {
                eapConnection.disconnect();
            }
        }

        HttpURLConnection urlConnection = HttpConfigurable.getInstance().openHttpConnection(VERSIONS_LIST_URL);
        try {
            int timeout = (int) TimeUnit.SECONDS.toMillis(30);
            urlConnection.setConnectTimeout(timeout);
            urlConnection.setReadTimeout(timeout);

            urlConnection.connect();

            InputStreamReader streamReader = new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8);
            try {
                JsonElement rootElement = JsonParser.parseReader(streamReader);
                JsonArray docsElements = rootElement.getAsJsonObject().get("response").getAsJsonObject().get("docs").getAsJsonArray();

                for (JsonElement element : docsElements) {
                    String versionNumber = element.getAsJsonObject().get("v").getAsString();
                    if (VersionComparatorUtil.compare(minimumVersion, versionNumber) <= 0) {
                        versions.add(versionNumber);
                    }
                }
            } finally {
                Closeables.closeQuietly(streamReader);
            }
        } finally {
            urlConnection.disconnect();
        }
        Collections.sort(versions, VersionComparatorUtil.COMPARATOR.reversed());

        // Handle the case when the new version has just been released and the Maven search index hasn't been updated yet
        if (kotlinCompilerVersion.isRelease() && !versions.contains(kotlinArtifactVersion)) {
            versions.add(0, kotlinArtifactVersion);
        }

        return versions;
    }

    private void updateComponents() {
        setOKActionEnabled(kotlinVersionComboBox.isEnabled());
    }

    public Map<String, Map<String, Module>> getVersionsAndModules() {
        return kotlinVersionsAndModules;
    }

    public Map<String, String> getModulesAndJvmTargets() {
        return modulesAndJvmTargets;
    }
}
