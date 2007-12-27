/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class CompilerProjectExtensionImpl extends CompilerProjectExtension {
  @NonNls private static final String OUTPUT_TAG = "output";
  @NonNls private static final String URL = "url";

  private VirtualFilePointer myCompilerOutput;
  private LocalFileSystem.WatchRequest myCompilerOutputWatchRequest;
  private Project myProject;

  public CompilerProjectExtensionImpl(final Project project) {
    myProject = project;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    final Element outputPathChild = element.getChild(OUTPUT_TAG);
    if (outputPathChild != null) {
      String outputPath = outputPathChild.getAttributeValue(URL);
      myCompilerOutput = VirtualFilePointerManager.getInstance().create(outputPath, null);
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    if (myCompilerOutput != null) {
      final Element pathElement = new Element(OUTPUT_TAG);
      pathElement.setAttribute(URL, myCompilerOutput.getUrl());
      element.addContent(pathElement);
    }
  }

  @Nullable
  public VirtualFile getCompilerOutput() {
    if (myCompilerOutput == null) return null;
    return myCompilerOutput.getFile();
  }

  @Nullable
  public String getCompilerOutputUrl() {
    if (myCompilerOutput == null) return null;
    return myCompilerOutput.getUrl();
  }

  public VirtualFilePointer getCompilerOutputPointer() {
    return myCompilerOutput;
  }

  public void setCompilerOutputPointer(VirtualFilePointer pointer) {
    myCompilerOutput = pointer;
  }

  public void setCompilerOutputUrl(String compilerOutputUrl) {
    myCompilerOutput = VirtualFilePointerManager.getInstance().create(compilerOutputUrl,
                                                                      ProjectRootManagerImpl.getInstanceImpl(myProject).getVirtualFilePointerListener());
    final LocalFileSystem.WatchRequest watchRequest =
      LocalFileSystem.getInstance().addRootToWatch(ProjectRootManagerImpl.extractLocalPath(compilerOutputUrl), true);
    if (myCompilerOutputWatchRequest != null) {
      LocalFileSystem.getInstance().removeWatchedRoot(myCompilerOutputWatchRequest);
    }
    myCompilerOutputWatchRequest = watchRequest;
  }

  @NotNull
  public Set<String> getRootsToWatch() {
    final Set<String> rootsToWatch = new HashSet<String>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      final String compilerOutputPath = ProjectRootManagerImpl.extractLocalPath(moduleRootManager.getCompilerOutputPathUrl());
      if (compilerOutputPath.length() > 0) {
        rootsToWatch.add(compilerOutputPath);
      }
      final String compilerOutputPathForTests =
        ProjectRootManagerImpl.extractLocalPath(moduleRootManager.getCompilerOutputPathForTestsUrl());
      if (compilerOutputPathForTests.length() > 0) {
        rootsToWatch.add(compilerOutputPathForTests);
      }
    }

    if (myCompilerOutput != null) {
      final String url = myCompilerOutput.getUrl();
      rootsToWatch.add(ProjectRootManagerImpl.extractLocalPath(url));
    }
    return rootsToWatch;
  }
}