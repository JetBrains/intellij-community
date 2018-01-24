/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.sdk.PythonSdkType;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyCompatibilityInspectionAdvertiser implements Annotator {

  private static final String NOTIFICATIONS_TITLE = "Python Versions Compatibility";
  private static final NotificationGroup BALLOON_NOTIFICATIONS = new NotificationGroup("Python Compatibility Inspection Advertiser",
                                                                                       NotificationDisplayType.STICKY_BALLOON, false);
  private static final Key<Boolean> DONT_SHOW_BALLOON = Key.create("showingPyCompatibilityAdvertiserBalloon");

  // Allow to show declined suggestion multiple times to ease debugging
  private static final boolean SHOW_ONCE_FOR_VERSION = true;

  @Language("HTML")
  private static final String YES_NO_REFS = "<a href=\"#yes\">Yes</a>&nbsp;&nbsp;<a href=\"#no\">No</a>";
  private static final String USING_FUTURE_IMPORTS = "Your source code contains __future__ imports";
  private static final String USING_SIX = "Your source code imports the 'six' package";

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PyFile) {

      final PyFile pyFile = (PyFile)element;
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
          showInspectionAdvertisement(project, USING_FUTURE_IMPORTS);
        }
        else if (containsSixImport(pyFile)) {
          showInspectionAdvertisement(project, USING_SIX);
        }
      }
    }
  }

  private static boolean moduleUsesPythonSdk(@NotNull PyFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file.getVirtualFile(), file.getProject());
    if (module != null) {
      return PythonSdkType.findPythonSdk(module) != null;
    }
    return false;
  }

  @Nullable
  private static LanguageLevel getLatestConfiguredCompatiblePython3Version(@NotNull PsiElement element) {
    final LanguageLevel latestVersion = getLatestConfiguredCompatiblePythonVersion(element);
    return latestVersion != null && !latestVersion.isPython2() ? latestVersion : null;
  }

  private static void showStalePython3VersionWarning(@NotNull PyFile file, 
                                                     @NotNull Project project, 
                                                     @NotNull LanguageLevel latestConfiguredVersion) {
    final List<LanguageLevel> versionsToEnable = getVersionsNewerThan(latestConfiguredVersion);
    final String versionsList = StringUtil.join(versionsToEnable, ",&nbsp;");
    final String message =
      String.format("Code compatibility inspection is configured for Python versions up to %s.<br/>" +
                    "Would you like to enable it for Python %s?<br/>" + YES_NO_REFS, latestConfiguredVersion, versionsList);
    showSingletonNotification(project, NOTIFICATIONS_TITLE, message, NotificationType.INFORMATION, (notification, event) -> {
        final boolean enabled = "#yes".equals(event.getDescription());
        if (enabled) {
          enableVersions(project, file, versionsToEnable);
        }
        if (enabled || SHOW_ONCE_FOR_VERSION) {
          getSettings(project).version = PyCompatibilityInspection.LATEST_INSPECTION_VERSION;
        }
      });
  }

  @NotNull
  private static List<LanguageLevel> getVersionsNewerThan(@NotNull LanguageLevel version) {
    final List<LanguageLevel> result = new ArrayList<>();
    final LanguageLevel latest = LanguageLevel.getLatest();
    for (LanguageLevel level : LanguageLevel.SUPPORTED_LEVELS) {
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
        inspection.ourVersions.addAll(ContainerUtil.map(versions, LanguageLevel::toString));
      });
      EditInspectionToolsSettingsAction.editToolSettings(project, profile, shortName);
    }
  }

  private static void showInspectionAdvertisement(@NotNull Project project, @NotNull String message) {
    final String msg = message + ".<br/>Would you like to enable Code compatibility inspection?<br/>" + YES_NO_REFS;
    showSingletonNotification(project, NOTIFICATIONS_TITLE, msg, NotificationType.INFORMATION, (notification, event) -> {
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
                                                @NotNull String title, 
                                                @NotNull String htmlContent, 
                                                @NotNull NotificationType type,
                                                @NotNull NotificationListener listener) {
    project.putUserData(DONT_SHOW_BALLOON, true);
    BALLOON_NOTIFICATIONS.createNotification(title, htmlContent, type, (notification, event) -> {
      try {
        listener.hyperlinkUpdate(notification, event);
      }
      finally {
        notification.expire();
      }
    }).notify(project);
  }

  private static boolean containsSixImport(@NotNull PyFile file) {
    for (PyFromImportStatement importStatement : file.getFromImports()) {
      final QualifiedName name = importStatement.getImportSourceQName();
      if (name != null && "six".equals(name.toString())) {
        return true;
      }
    }
    for (PyImportElement importElement : file.getImportTargets()) {
      final QualifiedName name = importElement.getImportedQName();
      if (name != null && "six".equals(name.getFirstComponent())) {
        return true;
      }
    }
    return false;
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
  
  @Nullable
  private static LanguageLevel getLatestConfiguredCompatiblePythonVersion(@NotNull PsiElement anchor) {
    final InspectionProfile profile = InspectionProfileManager.getInstance(anchor.getProject()).getCurrentProfile();
    final PyCompatibilityInspection inspection = (PyCompatibilityInspection)profile.getUnwrappedTool(getCompatibilityInspectionShortName(), anchor);
    final JDOMExternalizableStringList versions = inspection.ourVersions;
    if (versions.isEmpty()) {
      return null;
    }
    final String maxVersion = Collections.max(versions);
    return LanguageLevel.fromPythonVersion(maxVersion);
  }

  @NotNull
  private static String getCompatibilityInspectionShortName() {
    return PyCompatibilityInspection.class.getSimpleName();
  }
  
  @NotNull
  private static PyCompatibilityInspectionAdvertiserSettings getSettings(@NotNull Project project) {
    return ServiceManager.getService(project, PyCompatibilityInspectionAdvertiserSettings.class);
  }
}
