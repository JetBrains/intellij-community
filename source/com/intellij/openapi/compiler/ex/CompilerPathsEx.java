package com.intellij.openapi.compiler.ex;

import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public class CompilerPathsEx extends CompilerPaths {

  public static class FileVisitor {
    protected void accept(final VirtualFile file, final String fileRoot, final String filePath) {
      if (file.isDirectory()) {
        acceptDirectory(file, fileRoot, filePath);
      }
      else {
        acceptFile(file, fileRoot, filePath);
      }
    }

    protected void acceptFile(VirtualFile file, String fileRoot, String filePath) {
    }

    protected void acceptDirectory(final VirtualFile file, final String fileRoot, final String filePath) {
      final VirtualFile[] children = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
        public VirtualFile[] compute() {
          return file.getChildren();
        }
      });
      for (int idx = 0; idx < children.length; idx++) {
        final VirtualFile child = children[idx];
        final String name = child.getName();
        final StringBuffer buf = new StringBuffer(filePath.length() + "/".length() + name.length());
        buf.append(filePath).append("/").append(name);
        accept(child, fileRoot, buf.toString());
      }
    }
  }

  public static void visitFiles(final VirtualFile[] directories, final FileVisitor visitor) {
    for (int idx = 0; idx < directories.length; idx++) {
      final VirtualFile outputDir = directories[idx];
      final String path = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return outputDir.getPath();
        }
      });
      visitor.accept(outputDir, path, path);
    }
  }

  public static Set<String> getOutputFiles(final Project project) {
    final Set<String> result = new HashSet<String>();
    final VirtualFile[] outputDirectories = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
      public VirtualFile[] compute() {
        return CompilerPathsEx.getOutputDirectories(ModuleManager.getInstance(project).getModules());
      }
    });
    visitFiles(outputDirectories, new FileVisitor() {
      protected void acceptFile(VirtualFile file, String fileRoot, String filePath) {
        if (!(file.getFileSystem()  instanceof JarFileSystem)){
          result.add(filePath);
        }
      }
    });
    return result;
  }

  public static String getCompilationClasspath(Module module) {
    final StringBuffer classpathBuffer = new StringBuffer();
    final OrderEntry[] orderEntries = getSortedOrderEntries(module);
    for (int i = 0; i < orderEntries.length; i++) {
      final OrderEntry orderEntry = orderEntries[i];
      final VirtualFile[] files = orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES);
      for (int j = 0; j < files.length; j++) {
        final String path = PathUtil.getLocalPath(files[j]);
        if (path == null) {
          continue;
        }
        if (classpathBuffer.length() > 0) {
          classpathBuffer.append(File.pathSeparatorChar);
        }
        classpathBuffer.append(path);
      }
    }
    return classpathBuffer.toString();
  }

  private static OrderEntry[] getSortedOrderEntries(Module module) {
    //return ModuleRootManager.getInstance(module).getOrderEntries();
    // TODO: this is a patch for SCR 36800, After J2EE Compiler copying mechanizm is fixed,
    // TODO: remove all the code below and uncomment the line above
    final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    final List<OrderEntry> result = new ArrayList<OrderEntry>();
    final List<OrderEntry> moduleOrderEntries = new ArrayList<OrderEntry>();
    int insertIndex = 0;
    for (int idx = 0; idx < orderEntries.length; idx++) {
      OrderEntry orderEntry = orderEntries[idx];
      if (orderEntry instanceof ModuleOrderEntry) {
        moduleOrderEntries.add(orderEntry);
      }
      else {
        result.add(orderEntry);
        if (orderEntry instanceof ModuleSourceOrderEntry) {
          insertIndex = result.size() - 1;
        }
      }
    }
    if (moduleOrderEntries.size() > 0) {
      result.addAll(insertIndex, moduleOrderEntries);
    }
    return result.toArray(new OrderEntry[result.size()]);
  }

  public static String[] getOutputPaths(Module[] modules) {
    final Set<String> outputPaths = new OrderedSet<String>((TObjectHashingStrategy<String>)TObjectHashingStrategy.CANONICAL);
    for (int idx = 0; idx < modules.length; idx++) {
      Module module = modules[idx];
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      String outputPathUrl = moduleRootManager.getCompilerOutputPathUrl();
      if (outputPathUrl != null) {
        outputPaths.add(VirtualFileManager.extractPath(outputPathUrl).replace('/', File.separatorChar));
      }

      String outputPathForTestsUrl = moduleRootManager.getCompilerOutputPathForTestsUrl();
      if (outputPathForTestsUrl != null) {
        outputPaths.add(VirtualFileManager.extractPath(outputPathForTestsUrl).replace('/', File.separatorChar));
      }
    }
    return outputPaths.toArray(new String[outputPaths.size()]);
  }

  public static VirtualFile[] getOutputDirectories(final Module[] modules) {
    final Set<VirtualFile> dirs = new OrderedSet<VirtualFile>((TObjectHashingStrategy<VirtualFile>)TObjectHashingStrategy.CANONICAL);
    for (int idx = 0; idx < modules.length; idx++) {
      Module module = modules[idx];
      final VirtualFile outputDir = getModuleOutputDirectory(module, false);
      if (outputDir != null) {
        dirs.add(outputDir);
      }
      VirtualFile testsOutputDir = getModuleOutputDirectory(module, true);
      if (testsOutputDir != null) {
        dirs.add(testsOutputDir);
      }
    }
    return dirs.toArray(new VirtualFile[dirs.size()]);
  }
}
