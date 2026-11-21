// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.backend.TargetPythonInterpreter;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.remote.PyRemoteSourceItem;
import com.jetbrains.python.ui.targetPathEditor.ManualPathEntryDialog;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The path editor of a target interpreter. A path that the user adds is a path on the target: the editor maps it to a
 * local copy in the remote sources of the interpreter, and keeps the new mapping, see {@link #getPathMappings()}.
 */
@ApiStatus.Internal
final class PyRemotePathEditor extends PythonPathEditor {
  private static final Logger LOG = Logger.getInstance(PyRemotePathEditor.class);
  private final @NotNull Project myProject;
  private final @NotNull TargetPythonInterpreter myInterpreter;
  /** The path mappings of the interpreter, with the mappings of the paths that the user added here. */
  private final @NotNull PathMappingSettings myPathMappings;
  private boolean myMappingsChanged;

  PyRemotePathEditor(@NotNull Project project, @NotNull TargetPythonInterpreter interpreter) {
    super(PyBundle.message("python.sdk.configuration.tab.title"), OrderRootType.CLASSES,
          FileChooserDescriptorFactory.createAllButJarContentsDescriptor());
    myProject = project;
    myInterpreter = interpreter;
    PathMappingSettings stored = PythonInterpreterExtKt.getPathMappings(interpreter);
    myPathMappings = new PathMappingSettings(stored != null ? new ArrayList<>(stored.getPathMappings()) : new ArrayList<>());
  }

  /** The path mappings of the interpreter with the new ones, see {@code PythonInterpreterDetails.writePathMappings}. */
  @NotNull PathMappingSettings getPathMappings() {
    return myPathMappings;
  }

  /** Whether the user added a path, so the path mappings have a new mapping. */
  boolean isPathMappingsChanged() {
    return myMappingsChanged;
  }

  @Override
  protected String getPresentablePath(VirtualFile value) {
    return myPathMappings.convertToRemote(value.getPath());
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
      Path sourcesLocalPath = PythonInterpreterExtKt.getRemoteSourcesLocalPath(myInterpreter);
      if (sourcesLocalPath == null) return VirtualFile.EMPTY_ARRAY;

      VirtualFile[] vFiles = new VirtualFile[files.length];

      int i = 0;
      for (String file : files) {
        String localRoot = PyRemoteSourceItem.localPathForRemoteRoot(sourcesLocalPath.toString(), file);

        myPathMappings.addMappingCheckUnique(localRoot, file);
        myMappingsChanged = true;

        if (!new File(localRoot).exists()) {
          new File(localRoot).mkdirs();
        }
        vFiles[i++] = StandardFileSystems.local().refreshAndFindFileByPath(new File(localRoot).getAbsolutePath());
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

  private String @NotNull [] chooseRemoteFiles() {
    var dialog = new ManualPathEntryDialog(myProject, myInterpreter.getTargetConfiguration());
    return dialog.showAndGet() ? new String[]{dialog.getPath()} : ArrayUtil.EMPTY_STRING_ARRAY;
  }
}
