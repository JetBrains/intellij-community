// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinJvmBundle;
import org.jetbrains.kotlin.idea.KotlinPluginUtil;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout;
import org.jetbrains.kotlin.idea.configuration.ConfigureKotlinInProjectUtilsKt;
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator;
import org.jetbrains.kotlin.idea.extensions.gradle.RepositoryDescription;
import org.jetbrains.kotlin.idea.util.VersioningKt;
import org.jetbrains.kotlin.idea.versions.KotlinRuntimeLibraryUtilKt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.kotlin.idea.versions.KotlinRuntimeLibraryUtilKt.getLastStableKnownCompilerVersionShort;

public class ConfigureDialogWithModulesAndVersion extends DialogWrapper {
    private static final String VERSIONS_LIST_URL =
            "https://search.maven.org/solrsearch/select?q=g:%22org.jetbrains.kotlin%22+AND+a:%22kotlin-stdlib%22&core=gav&rows=20&wt=json";

    @NotNull private final String minimumVersion;

    private final ChooseModulePanel chooseModulePanel;

    private JPanel contentPane;
    private JPanel chooseModulesPanelPlace;
    private JComboBox<String> kotlinVersionComboBox;
    private JPanel infoPanel;

    private final AsyncProcessIcon processIcon = new AsyncProcessIcon("loader");

    public ConfigureDialogWithModulesAndVersion(
            @NotNull Project project,
            @NotNull KotlinProjectConfigurator configurator,
            @NotNull Collection<Module> excludeModules,
            @NotNull String minimumVersion
    ) {
        super(project);

        setTitle(KotlinJvmBundle.message("configure.kotlin.title", configurator.getPresentableText()));

        this.minimumVersion = minimumVersion;
        init();

        ProgressManager.getInstance().run(new Task.Backgroundable(
                project, KotlinJvmBundle.message("configure.kotlin.find.maven.versions"), false
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                loadKotlinVersions();
            }
        });

        kotlinVersionComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(@NotNull ActionEvent e) {
                updateComponents();
            }
        });

        kotlinVersionComboBox.addItem(KotlinJvmBundle.message("configure.kotlin.loading"));
        kotlinVersionComboBox.setEnabled(false);

        processIcon.resume();
        infoPanel.add(processIcon, BorderLayout.CENTER);

        chooseModulePanel = new ChooseModulePanel(project, configurator, excludeModules);
        chooseModulesPanelPlace.add(chooseModulePanel.getContentPane(), BorderLayout.CENTER);

        updateComponents();
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
        }
        catch (Throwable t) {
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
            infoPanel.setToolTipText(KotlinJvmBundle.message("configure.kotlin.cant.load.versions"));
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
        public static Collection<String> loadVersions(String minimumVersion) throws Exception {
        List<String> versions = new ArrayList<>();

        String kotlinCompilerVersionShort = getLastStableKnownCompilerVersionShort(KotlinPluginLayout.getInstance());
        RepositoryDescription repositoryDescription = ConfigureKotlinInProjectUtilsKt.getRepositoryForVersion(kotlinCompilerVersionShort);
        if (repositoryDescription != null && repositoryDescription.getBintrayUrl() != null) {
            HttpURLConnection eapConnection = HttpConfigurable.getInstance().openHttpConnection(repositoryDescription.getBintrayUrl() + kotlinCompilerVersionShort);
            try {
                int timeout = (int) TimeUnit.SECONDS.toMillis(30);
                eapConnection.setConnectTimeout(timeout);
                eapConnection.setReadTimeout(timeout);

                if (eapConnection.getResponseCode() == 200) {
                    versions.add(kotlinCompilerVersionShort);
                }
            }
            finally {
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
                JsonElement rootElement = new JsonParser().parse(streamReader);
                JsonArray docsElements = rootElement.getAsJsonObject().get("response").getAsJsonObject().get("docs").getAsJsonArray();

                for (JsonElement element : docsElements) {
                    String versionNumber = element.getAsJsonObject().get("v").getAsString();
                    if (VersionComparatorUtil.compare(minimumVersion, versionNumber) <= 0) {
                        versions.add(versionNumber);
                    }
                }
            }
            finally {
                Closeables.closeQuietly(streamReader);
            }
        }
        finally {
            urlConnection.disconnect();
        }
        Collections.sort(versions, VersionComparatorUtil.COMPARATOR.reversed());

        // Handle the case when the new version has just been released and the Maven search index hasn't been updated yet
        if (!VersioningKt.isEap(kotlinCompilerVersionShort) && !KotlinPluginUtil.isSnapshotVersion() &&
            !kotlinCompilerVersionShort.contains("dev") && !versions.contains(kotlinCompilerVersionShort)) {
            versions.add(0, kotlinCompilerVersionShort);
        }

        return versions;
    }

    private void updateComponents() {
        setOKActionEnabled(kotlinVersionComboBox.isEnabled());
    }
}
