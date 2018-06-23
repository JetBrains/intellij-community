/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.uast.test.env;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.MetaLanguage;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.psi.FileContextProvider;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.impl.JavaClassSupersImpl;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.psi.util.JavaClassSupers;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class TestCoreEnvironment extends AbstractCoreEnvironment {
  private static final Object APPLICATION_LOCK = new Object();
  private static volatile JavaCoreApplicationEnvironment sEnvironment = null;

  private final Disposable mDisposable;
  private volatile JavaCoreProjectEnvironment mProjectEnvironment = null;

  public TestCoreEnvironment(Disposable disposable) {
    mDisposable = disposable;
  }

  @Override
  public void dispose() {
    Disposer.dispose(mDisposable);
  }

  @Override
  public MockProject getProject() {
    JavaCoreProjectEnvironment projectEnvironment = getProjectEnvironment();
    if (projectEnvironment == null) {
      return null;
    }
    return projectEnvironment.getProject();
  }

  @Override
  public void addJavaSourceRoot(@NotNull File root) {
    VirtualFileSystem vfs = StandardFileSystems.local();
    try {
      addDirectoryToClassPath(vfs, root);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void addDirectoryToClassPath(VirtualFileSystem vfs, File root) throws IOException {
    VirtualFile virtualFile = vfs.findFileByPath(root.getCanonicalPath());
    assert virtualFile != null;
    getProjectEnvironment().addSourcesToClasspath(virtualFile);
  }

  public void addJar(@NotNull File root) {
    getProjectEnvironment().addJarToClassPath(root);
  }

  public JavaCoreProjectEnvironment getProjectEnvironment() {
    if (mProjectEnvironment != null) {
      return mProjectEnvironment;
    }
    synchronized (APPLICATION_LOCK) {
      if (mProjectEnvironment != null) {
        return mProjectEnvironment;
      }
      JavaCoreApplicationEnvironment coreEnvironment = getCoreEnvironment();

      mProjectEnvironment = new TestJavaCoreProjectEnvironment(coreEnvironment);

      Disposer.register(mDisposable, new Disposable() {
        @Override
        public void dispose() {
          mProjectEnvironment = null;
        }
      });

      return mProjectEnvironment;
    }
  }

  private static JavaCoreApplicationEnvironment getCoreEnvironment() {
    if (sEnvironment != null) {
      return sEnvironment;
    }
    synchronized (APPLICATION_LOCK) {
      if (sEnvironment != null) {
        return sEnvironment;
      }
      Disposable parentDisposable = Disposer.newDisposable();
      Extensions.cleanRootArea(parentDisposable);
      registerAppExtensionPoints();
      JavaCoreApplicationEnvironment coreEnvironment = new JavaCoreApplicationEnvironment(parentDisposable);
      coreEnvironment.registerApplicationService(JavaClassSupers.class, new JavaClassSupersImpl());

      sEnvironment = coreEnvironment;

      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          synchronized (APPLICATION_LOCK) {
            JavaCoreApplicationEnvironment environment = sEnvironment;
            sEnvironment = null;
            Disposer.dispose(environment.getParentDisposable());
            ZipHandler.clearFileAccessorCache();
          }
        }
      });
      return sEnvironment;
    }
  }

  private static void registerAppExtensionPoints() {
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), BinaryFileStubBuilders.EP_NAME, FileTypeExtensionPoint.class);
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), FileContextProvider.EP_NAME, FileContextProvider.class);
    //
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), MetaDataContributor.EP_NAME, MetaDataContributor.class);
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), PsiAugmentProvider.EP_NAME, PsiAugmentProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), JavaMainMethodProvider.EP_NAME, JavaMainMethodProvider.class);
    //
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), ContainerProvider.EP_NAME, ContainerProvider.class);
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), ClassFileDecompilers.EP_NAME, ClassFileDecompilers.Decompiler.class);
    CoreApplicationEnvironment.registerExtensionPoint(
      Extensions.getRootArea(), MetaLanguage.EP_NAME, MetaLanguage.class);
  }

  private class TestJavaCoreProjectEnvironment extends JavaCoreProjectEnvironment {
    TestJavaCoreProjectEnvironment(JavaCoreApplicationEnvironment coreEnvironment) {
      super(TestCoreEnvironment.this.mDisposable, coreEnvironment);
      registerProjectExtensions();
    }

    @Override
    protected void preregisterServices() {
      registerProjectExtensionPoints();
    }

    private void registerProjectExtensionPoints() {
      ExtensionsArea area = Extensions.getArea(myProject);
      CoreApplicationEnvironment.registerExtensionPoint(
        area, PsiTreeChangePreprocessor.EP_NAME, PsiTreeChangePreprocessor.class);
      CoreApplicationEnvironment.registerExtensionPoint(
        area, PsiElementFinder.EP_NAME, PsiElementFinder.class);
    }

    private void registerProjectExtensions() {
      ExtensionsArea area = Extensions.getArea(myProject);

      myProject.registerService(CoreJavaFileManager.class,
                                ((CoreJavaFileManager)ServiceManager.getService(myProject, JavaFileManager.class)));

      area.getExtensionPoint(PsiElementFinder.EP_NAME).registerExtension(
        new PsiElementFinderImpl(myProject, ServiceManager
          .getService(myProject, JavaFileManager.class)));
    }
  }
}
