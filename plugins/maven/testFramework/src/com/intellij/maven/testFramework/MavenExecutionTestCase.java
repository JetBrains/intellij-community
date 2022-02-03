// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.IdeaTestUtil;

import java.io.File;

public abstract class MavenExecutionTestCase extends MavenMultiVersionImportingTestCase {

  private static final String JDK_NAME = "MavenExecutionTestJDK";
  private String myJdkHome;

  @Override
  public void setUp() throws Exception {
    edt(() -> {
      myJdkHome = IdeaTestUtil.requireRealJdkHome();
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), myJdkHome);
      super.setUp();

      WriteAction.runAndWait(() -> {
        Sdk oldJdk = ProjectJdkTable.getInstance().findJdk(JDK_NAME);
        if (oldJdk != null) {
          ProjectJdkTable.getInstance().removeJdk(oldJdk);
        }
        VirtualFile jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myJdkHome));
        Sdk jdk = SdkConfigurationUtil.setupSdk(new Sdk[0], jdkHomeDir, JavaSdk.getInstance(), true, null, JDK_NAME);
        assertNotNull("Cannot create JDK for " + myJdkHome, jdk);
        ProjectJdkTable.getInstance().addJdk(jdk);
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
        if (projectRootManager.getProjectSdk() == null) {
          projectRootManager.setProjectSdk(jdk);
        }
      });
    });
  }

  @Override
  public void tearDown() throws Exception {
    edt(() -> {
      if (myJdkHome == null) {
        //super.setUp() wasn't called
        return;
      }
      Sdk jdk = ProjectJdkTable.getInstance().findJdk(JDK_NAME);
      if (jdk != null) {
        WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().removeJdk(jdk));
      }

      super.tearDown();
    });
  }
}
