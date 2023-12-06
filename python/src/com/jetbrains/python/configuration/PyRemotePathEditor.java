// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSourceItem;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.target.PyTargetAwareAdditionalData;
import com.jetbrains.python.ui.targetPathEditor.ManualPathEntryDialog;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class PyRemotePathEditor extends PythonPathEditor {
  private static final Logger LOG = Logger.getInstance(PyRemotePathEditor.class);
  private final RemoteSdkProperties myRemoteSdkData;
  @NotNull private final Project myProject;
  @NotNull private final Sdk mySdk;

  private final List<PathMappingSettings.PathMapping> myNewMappings = new ArrayList<>();

  PyRemotePathEditor(@NotNull Project project, @NotNull Sdk sdk) {
    super(PyBundle.message("python.sdk.configuration.tab.title"), OrderRootType.CLASSES,
          FileChooserDescriptorFactory.createAllButJarContentsDescriptor());
    myProject = project;
    mySdk = sdk;
    myRemoteSdkData = (RemoteSdkProperties)mySdk.getSdkAdditionalData();
  }

  @Override
  protected String getPresentablePath(VirtualFile value) {
    String path = value.getPath();
    return myRemoteSdkData.getPathMappings().convertToRemote(path);
  }

  @Override
  protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
    toolbarDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final VirtualFile[] added = doAddItems();
        if (added.length > 0) {
          setModified(true);
        }
        requestDefaultFocus();
        setSelectedRoots(added);
      }
    });

    super.addToolbarButtons(toolbarDecorator);
  }

  @Override
  protected VirtualFile[] doAddItems() {
    try {
      String[] files = chooseRemoteFiles();

      final String sourcesLocalPath = PySdkExtKt.getRemoteSourcesLocalPath(mySdk).toString();

      VirtualFile[] vFiles = new VirtualFile[files.length];

      int i = 0;
      for (String file : files) {
        String localRoot = PyRemoteSourceItem.localPathForRemoteRoot(sourcesLocalPath, file);

        myNewMappings.add(new PathMappingSettings.PathMapping(localRoot, file));
        myRemoteSdkData.getPathMappings().addMappingCheckUnique(localRoot, file);

        if (!new File(localRoot).exists()) {
          new File(localRoot).mkdirs();
        }
        vFiles[i++] = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(localRoot));
      }

      vFiles = adjustAddedFileSet(myPanel, vFiles);
      List<VirtualFile> added = new ArrayList<>(vFiles.length);
      for (VirtualFile vFile : vFiles) {
        if (addElement(vFile)) {
          added.add(vFile);
        }
      }
      return VfsUtilCore.toVirtualFileArray(added);
    }
    catch (Exception e) {
      LOG.error("Failed to add Python paths", e);
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  private String @NotNull [] chooseRemoteFiles() throws ExecutionException, InterruptedException {
    SdkAdditionalData sdkAdditionalData = mySdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PyRemoteSdkAdditionalDataBase) {
      PythonRemoteInterpreterManager remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance();
      if (remoteInterpreterManager == null) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      return remoteInterpreterManager.chooseRemoteFiles(myProject, (PyRemoteSdkAdditionalDataBase)sdkAdditionalData, false);
    }
    else if (sdkAdditionalData instanceof PyTargetAwareAdditionalData) {
      var dialog = new ManualPathEntryDialog(myProject,
                                             ((PyTargetAwareAdditionalData)sdkAdditionalData).getTargetEnvironmentConfiguration());
      if (dialog.showAndGet()) {
        return new String[]{dialog.getPath()};
      }
      else {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
    }
    else {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  @Override
  public void apply(SdkModificator sdkModificator) {
    if (sdkModificator.getSdkAdditionalData() instanceof RemoteSdkProperties) {
      for (PathMappingSettings.PathMapping mapping : myNewMappings) {
        ((RemoteSdkProperties)sdkModificator.getSdkAdditionalData()).getPathMappings()
          .addMappingCheckUnique(mapping.getLocalRoot(), mapping.getRemoteRoot());
      }
    }
    super.apply(sdkModificator);
  }
}
