package com.intellij.openapi.compiler.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;

import java.io.File;
import java.util.Set;

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
      ProgressManager.getInstance().checkCanceled();
      final VirtualFile[] children = file.getChildren();
      for (final VirtualFile child : children) {
        final String name = child.getName();
        final String _filePath;
        final StringBuilder buf = StringBuilderSpinAllocator.alloc();
        try {
          buf.append(filePath).append("/").append(name);
          _filePath = buf.toString();
        }
        finally {
          StringBuilderSpinAllocator.dispose(buf);
        }
        accept(child, fileRoot, _filePath);
      }
    }
  }

  public static void visitFiles(final VirtualFile[] directories, final FileVisitor visitor) {
    for (final VirtualFile outputDir : directories) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final String path = outputDir.getPath();
          visitor.accept(outputDir, path, path);
        }
      });
    }
  }

  public static String getCompilationClasspath(Module module) {
    final StringBuffer classpathBuffer = new StringBuffer();
    final OrderEntry[] orderEntries = getOrderEntries(module);
    for (final OrderEntry orderEntry : orderEntries) {
      final VirtualFile[] files = orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES);
      for (VirtualFile file : files) {
        final String path = PathUtil.getLocalPath(file);
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

  public static OrderEntry[] getOrderEntries(Module module) {
    return ModuleRootManager.getInstance(module).getOrderEntries();
    // TODO: this is a patch for SCR 36800, After J2EE Compiler copying mechanizm is fixed,
    // TODO: remove all the code below and uncomment the line above
    /*
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
    */
  }

  public static String[] getOutputPaths(Module[] modules) {
    final Set<String> outputPaths = new OrderedSet<String>((TObjectHashingStrategy<String>)TObjectHashingStrategy.CANONICAL);
    for (Module module : modules) {
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
    for (Module module : modules) {
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
