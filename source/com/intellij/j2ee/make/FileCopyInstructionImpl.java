package com.intellij.j2ee.make;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

public class FileCopyInstructionImpl extends BuildInstructionBase implements FileCopyInstruction {
  private File myFile;
  private boolean myIsDirectory;
  // for a directory keep the subset of changed files that need to be copied
  private List<FileCopyInstructionImpl> myChangedSet;

  public FileCopyInstructionImpl(File source,
                                 boolean isDirectory,
                                 Module module,
                                 String outputRelativePath) {
    super(outputRelativePath, module);
    setFile(source, isDirectory);
  }

  public void addFilesToExploded(CompileContext context,
                                 File outputDir,
                                 Set<String> writtenPaths,
                                 FileFilter fileFilter) throws IOException {
    if (myChangedSet == null) {
      final File to = MakeUtil.canonicalRelativePath(outputDir, getOutputRelativePath());
      // todo check for recursive copying
      if (!MakeUtil.checkFileExists(getFile(), context)) return;
      MakeUtil.getInstance().copyFile(getFile(), to, context, writtenPaths, fileFilter);
    }
    else {
      for (int i = 0; i < myChangedSet.size(); i++) {
        FileCopyInstructionImpl singleFileCopyInstruction = myChangedSet.get(i);
        singleFileCopyInstruction.addFilesToExploded(context, outputDir, writtenPaths, fileFilter);
      }
    }
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitFileCopyInstruction(this);
  }

  public File findFileByRelativePath(String relativePath) {
    if (!relativePath.startsWith(getOutputRelativePath())) return null;
    final String pathFromFile = relativePath.substring(getOutputRelativePath().length());
    if (!myIsDirectory) {
      return pathFromFile.equals("") ? myFile : null;
    }
    final File file = MakeUtil.canonicalRelativePath(myFile, pathFromFile);

    return file.exists() ? file : null;
  }

  public void addFilesToJar(CompileContext context,
                            File jarFile,
                            JarOutputStream outputStream,
                            BuildRecipe dependencies,
                            Set<String> writtenRelativePaths,
                            FileFilter fileFilter) throws IOException {
    final String outputRelativePath = getOutputRelativePath();

    File file = getFile();
    if (isExternalDependencyInstruction()) {
      // copy dependent file along with jar file
      final File toFile = MakeUtil.canonicalRelativePath(jarFile, outputRelativePath);
      MakeUtil.getInstance().copyFile(file, toFile, context, new HashSet<String>(), fileFilter);
      dependencies.addInstruction(this);
    }
    else {
      boolean ok = ZipUtil.addFileOrDirRecursively(outputStream, jarFile, file, outputRelativePath, fileFilter, writtenRelativePaths);
      if (!ok) {
        MakeUtil.reportRecursiveCopying(context, file.getPath(), jarFile.getPath(), "",
                                                      "Please setup jar file location outside directory '" + file.getPath() + "'.");
      }
    }
  }

  public String toString() {
    if (myChangedSet == null) {
      String s = "Copy "+getFile();
      if (getModule() != null) {
        s += "(from '"+ModuleUtil.getModuleNameInReadAction(getModule())+"')";
      }
      s += "->"+getOutputRelativePath();
      return s;
    }
    else {
      String s = "Copy (Incr "+myFile+")";
      for (int i = 0; i < myChangedSet.size(); i++) {
        FileCopyInstructionImpl fileCopyInstruction = myChangedSet.get(i);
        s += fileCopyInstruction +", ";
      }
      return s;
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileCopyInstruction)) return false;

    final FileCopyInstruction item = (FileCopyInstruction) o;

    if (getFile() != null ? !getFile().equals(item.getFile()) : item.getFile() != null) return false;

    return true;
  }

  public int hashCode() {
    return getFile() != null ? getFile().hashCode() : 0;
  }

  public File getFile() {
    return myFile;
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  public void setFile(File file, boolean isDirectory) {
    myFile = file;
    myIsDirectory = isDirectory;
  }

  // incremental compiler integration support
  // instruction implementation should only process the intersection of files it owns and files passed in this method
  public void addFileToChangedSet(FileCopyInstructionImpl item) {
    myChangedSet.add(item);
  }

  public void clearChangedSet() {
    myChangedSet = new ArrayList<FileCopyInstructionImpl>();
  }
}
