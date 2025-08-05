// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.NlsContexts.NotificationTitle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PythonSdkUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.jetbrains.python.statistics.PythonCompatibilityInspectionAdvertiserIdsHolder.*;

/**
 * @author Mikhail Golubev
 */
public final class PyCompatibilityInspectionAdvertiser implements Annotator {

  private static final Key<Boolean> DONT_SHOW_BALLOON = Key.create("showingPyCompatibilityAdvertiserBalloon");

  // Allow to show declined suggestion multiple times to ease debugging
  private static final boolean SHOW_ONCE_FOR_VERSION = true;

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PyFile pyFile) {

      final Project project = element.getProject();

      final VirtualFile vFile = pyFile.getVirtualFile();
      if (vFile != null && FileIndexFacade.getInstance(project).isInLibraryClasses(vFile)) {
        return;
      }

      final Boolean showingFlag = project.getUserData(DONT_SHOW_BALLOON);
      if (showingFlag != null && showingFlag.booleanValue()) {
        return;
      }

      if (!moduleUsesPythonSdk(pyFile)) {
        return;
      }

      final int inspectionVersion = getSettings(project).version;
      if (inspectionVersion < PyCompatibilityInspection.LATEST_INSPECTION_VERSION) {
        if (isCompatibilityInspectionEnabled(element)) {
          final LanguageLevel pyVersion = getLatestConfiguredCompatiblePython3Version(element);
          if (pyVersion != null && pyVersion.isOlderThan(LanguageLevel.getLatest())) {
            showStalePython3VersionWarning(pyFile, project, pyVersion);
          }
        }
        else if (containsFutureImports(pyFile)) {
          showSingletonNotification(
            project, PyBundle.message("python.compatibility.inspection.advertiser.using.future.imports.warning.message"), USING_FUTURE_IMPORTS);
        }
        else if (PyPsiUtils.containsImport(pyFile, "six")) {
          showSingletonNotification(
            project, PyBundle.message("python.compatibility.inspection.advertiser.using.six.warning.message"), USING_SIX_PACKAGE);
        }
      }
    }
  }

  private static boolean moduleUsesPythonSdk(@NotNull PyFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file.getVirtualFile(), file.getProject());
    if (module != null) {
      return PythonSdkUtil.findPythonSdk(module) != null;
    }
    return false;
  }

  private static @Nullable LanguageLevel getLatestConfiguredCompatiblePython3Version(@NotNull PsiElement element) {
    final LanguageLevel latestVersion = getLatestConfiguredCompatiblePythonVersion(element);
    return latestVersion != null && !latestVersion.isPython2() ? latestVersion : null;
  }

  private static void showStalePython3VersionWarning(@NotNull PyFile file,
                                                     @NotNull Project project,
                                                     @NotNull LanguageLevel latestConfiguredVersion) {
    final List<LanguageLevel> versionsToEnable = getVersionsNewerThan(latestConfiguredVersion);
    final String versionsList = StringUtil.join(versionsToEnable, ",&nbsp;");
    final String message =
      PyBundle.message("python.compatibility.inspection.advertiser.version.stale.python3.version.warning.message",
                       latestConfiguredVersion,
                       versionsList);
    showSingletonNotification(
      project,
      PyBundle.message("python.compatibility.inspection.advertiser.notifications.title"),
      message,
      STALE_PYTHON_VERSION,
      (notification, event) -> {
        final boolean enabled = "#yes".equals(event.getDescription());
        if (enabled) {
          enableVersions(project, file, versionsToEnable);
        }
        if (enabled || SHOW_ONCE_FOR_VERSION) {
          getSettings(project).version = PyCompatibilityInspection.LATEST_INSPECTION_VERSION;
        }
      });
  }

  private static @NotNull List<LanguageLevel> getVersionsNewerThan(@NotNull LanguageLevel version) {
    final List<LanguageLevel> result = new ArrayList<>();
    final LanguageLevel latest = LanguageLevel.getLatest();
    for (LanguageLevel level : PyCompatibilityInspection.SUPPORTED_LEVELS) {
      if (version.isOlderThan(level) && latest.isAtLeast(level)) {
        result.add(level);
      }
    }
    return result;
  }

  private static void enableVersions(@NotNull Project project, @NotNull PsiElement file, @NotNull List<LanguageLevel> versions) {
    final InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    final String shortName = getCompatibilityInspectionShortName();
    final InspectionToolWrapper tool = profile.getInspectionTool(shortName, project);
    if (tool != null) {
      profile.modifyProfile(model -> {
        final PyCompatibilityInspection inspection = (PyCompatibilityInspection)model.getUnwrappedTool(shortName, file);
        assert inspection != null;
        List<String> newVersions = new ArrayList<>(ContainerUtil.map(versions, LanguageLevel::toString));
        newVersions.removeAll(inspection.ourVersions);
        inspection.ourVersions.addAll(newVersions);
      });
      EditInspectionToolsSettingsAction.editToolSettings(project, profile, shortName);
    }
  }

  private static void showSingletonNotification(@NotNull Project project, @NotificationContent String msg, @NotNull String displayId) {
    showSingletonNotification(
      project,
      PyBundle.message("python.compatibility.inspection.advertiser.notifications.title"),
      msg,
      displayId,
      (notification, event) -> {
        final boolean enabled = "#yes".equals(event.getDescription());
        if (enabled) {
          enableCompatibilityInspection(project);
        }
        if (enabled || SHOW_ONCE_FOR_VERSION) {
          getSettings(project).version = PyCompatibilityInspection.LATEST_INSPECTION_VERSION;
        }
      });
  }

  private static void showSingletonNotification(@NotNull Project project,
                                                @NotNull @NotificationTitle String title,
                                                @NotNull @NotificationContent String htmlContent,
                                                @NotNull String displayId,
                                                @NotNull NotificationListener listener) {
    project.putUserData(DONT_SHOW_BALLOON, true);
    NotificationGroupManager.getInstance().getNotificationGroup("Python Compatibility Inspection Advertiser")
      .createNotification(title, htmlContent, NotificationType.INFORMATION)
      .setDisplayId(displayId)
      .setSuggestionType(true)
      .setListener((notification, event) -> {
        try {
          listener.hyperlinkUpdate(notification, event);
        }
        finally {
          notification.expire();
        }
      })
      .notify(project);
  }

  private static boolean containsFutureImports(@NotNull PyFile file) {
    for (PyFromImportStatement importStatement : file.getFromImports()) {
      if (importStatement.isFromFuture()) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCompatibilityInspectionEnabled(@NotNull PsiElement anchor) {
    final InspectionProfile profile = InspectionProfileManager.getInstance(anchor.getProject()).getCurrentProfile();
    final InspectionToolWrapper tool = profile.getInspectionTool(getCompatibilityInspectionShortName(), anchor.getProject());
    return tool != null && profile.isToolEnabled(HighlightDisplayKey.findById(tool.getID()), anchor);
  }

  private static void enableCompatibilityInspection(@NotNull Project project) {
    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    final InspectionToolWrapper tool = profile.getInspectionTool(getCompatibilityInspectionShortName(), project);
    if (tool != null) {
      profile.setToolEnabled(tool.getShortName(), true);
      EditInspectionToolsSettingsAction.editToolSettings(project, profile, getCompatibilityInspectionShortName());
    }
  }

  private static @Nullable LanguageLevel getLatestConfiguredCompatiblePythonVersion(@NotNull PsiElement anchor) {
    final InspectionProfile profile = InspectionProfileManager.getInstance(anchor.getProject()).getCurrentProfile();
    final PyCompatibilityInspection inspection =
      (PyCompatibilityInspection)profile.getUnwrappedTool(getCompatibilityInspectionShortName(), anchor);
    assert inspection != null;
    final JDOMExternalizableStringList versions = inspection.ourVersions;
    if (versions.isEmpty()) {
      return null;
    }
    return StreamEx.of(versions)
      .map(LanguageLevel::fromPythonVersion)
      .max(Comparator.naturalOrder())
      .orElse(null);
  }

  private static @NotNull String getCompatibilityInspectionShortName() {
    return PyCompatibilityInspection.class.getSimpleName();
  }

  private static @NotNull PyCompatibilityInspectionAdvertiserSettings getSettings(@NotNull Project project) {
    return project.getService(PyCompatibilityInspectionAdvertiserSettings.class);
  }
}
