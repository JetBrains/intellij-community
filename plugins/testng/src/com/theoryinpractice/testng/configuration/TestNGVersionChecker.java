/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.theoryinpractice.testng.configuration;

import com.intellij.CommonBundle;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterClass;
import org.testng.remote.strprotocol.TestResultMessage;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * User: anna
 * Date: 3/15/12
 */
public class TestNGVersionChecker {
  private static final Logger LOG = Logger.getInstance("#" + TestNGVersionChecker.class.getName());

  private static final String TEST_NG_VERSIONS_INCOMPATIBILITY = "<html><b>!!!TestNG protocol incompatibility!!!</b><br>";
  private static final String COPY_MESSAGE = "In order to use your project testng.jar, please, <a href=\"copy\">copy</a> it in the plugin lib directory.</html>";

  @Nullable
  public static String getVersionIncompatibilityMessage(Project project, GlobalSearchScope scope, String pathToBundledJar) {
    final String protocolClassMessageClass = TestResultMessage.class.getName();
    final PsiClass psiProtocolClass = JavaPsiFacade.getInstance(project).findClass(protocolClassMessageClass, scope);
    if (psiProtocolClass != null) {
      final String instanceFieldName = "m_instanceName";
      final ZipFile workingLibrary = getZipLibrary(project, scope);
      if (workingLibrary != null) {
        final VirtualFile bundledJar = LocalFileSystem.getInstance().findFileByPath(pathToBundledJar);
        if (bundledJar != null) {
          final String jarVersion = JarVersionDetectionUtil.detectJarVersion(workingLibrary);
          final JarFileSystem jarFileSystem = JarFileSystem.getInstance();
          final VirtualFile bundledJarJar = jarFileSystem.getJarRootForLocalFile(bundledJar);
          if (bundledJarJar == null) return null;
          String bundledVersion;
          try {
            bundledVersion = JarVersionDetectionUtil.detectJarVersion(jarFileSystem.getJarFile(bundledJarJar));
          }
          catch (IOException e) {
            return null;
          }
          final String incompatibilityMessage = TEST_NG_VERSIONS_INCOMPATIBILITY +
                                                "Right now " + ApplicationNamesInfo.getInstance().getFullProductName() + 
                                                " does not support testng version (v." + jarVersion + ") used in your project due to the protocol changes on the TestNG side.<br>" +
                                                "Bundled jar (v." + bundledVersion + ") was used instead to run your tests.<br>" + COPY_MESSAGE;
          try {
            final Class aClass = Class.forName(protocolClassMessageClass);
            aClass.getDeclaredField(instanceFieldName);
            if (psiProtocolClass.findFieldByName(instanceFieldName, false) == null) {
              return incompatibilityMessage;
            }
          }
          catch (NoSuchFieldException e) {
            if (psiProtocolClass.findFieldByName(instanceFieldName, false) != null) {
              return incompatibilityMessage;
            }
          }
          catch (Exception ignore) {
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile getWorkingLibrary(Project project, GlobalSearchScope scope) {
    final VirtualFile library = getLibrary(project, scope);
    if (library != null) {
      return JarFileSystem.getInstance().getVirtualFileForJar(library);
    }
    return null;
  }

  @Nullable
  public static ZipFile getZipLibrary(Project project, GlobalSearchScope scope) {
    final VirtualFile library = getLibrary(project, scope);
    if (library != null) {
      try {
        return JarFileSystem.getInstance().getJarFile(library);
      }
      catch (IOException e) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile getLibrary(Project project, GlobalSearchScope scope) {
    final String protocolClassMessageClass = TestResultMessage.class.getName();
    final PsiClass psiProtocolClass = JavaPsiFacade.getInstance(project).findClass(protocolClassMessageClass, scope);
    if (psiProtocolClass != null) {
      final PsiFile containingFile = psiProtocolClass.getContainingFile();
      if (containingFile != null) {
        final VirtualFile file = containingFile.getVirtualFile();
        if (file != null) {
          final VirtualFileSystem fileSystem = file.getFileSystem();
          if (fileSystem instanceof JarFileSystem) {
            return file;
          }
        }
      }
    }
    return null;
  }

  static class MyCopyJarListener implements HyperlinkListener {
    private final GlobalSearchScope myScope;
    private final Project myProject;

    public MyCopyJarListener(GlobalSearchScope scope, Project project) {
      myScope = scope;
      myProject = project;
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        final String testNGPluginLibPath = PathManager.getHomePath() + File.separator + "plugins" + File.separator + "testng" + File.separator + "lib";
        
        final VirtualFile library = getWorkingLibrary(myProject, myScope);
        if (library != null) {
          try {
            final String jarName = new File(PathUtil.getJarPathForClass(AfterClass.class)).getName();
            PluginDownloader.replaceLib(testNGPluginLibPath, jarName, library);
            final Application app = ApplicationManager.getApplication();
            final String updateSuccessfullyMessage = "Internal testng.jar was successfully updated. ";
            if (app.isRestartCapable()) {
              final String restartMessage = updateSuccessfullyMessage +
                                            "Would you like to restart " + ApplicationNamesInfo.getInstance().getFullProductName() + " to apply changes?";
              if (Messages.showOkCancelDialog(myProject, restartMessage, CommonBundle.getWarningTitle(), "Restart", "Postpone",
                                              Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE) {
                ApplicationManager.getApplication().restart();
              }
            }
            else {
              final String message = updateSuccessfullyMessage + 
                                     "Restart " + ApplicationNamesInfo.getInstance().getFullProductName() + " in order to apply changes.";
              TestsUIUtil.NOTIFICATION_GROUP.createNotification(message, MessageType.INFO).notify(myProject);
            }
          }
          catch (IOException e1) {
            LOG.info(e1);
          }
        }
      }
    }
  }
}
