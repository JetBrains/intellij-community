package com.intellij.ide.util.importProject;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
*         Date: Jul 13, 2007
*/
public class ModuleDescriptor {
  final Set<File> myContentRoots = new HashSet<File>();
  final Set<File> mySourceRoots = new HashSet<File>();
  final Set<File> myLibraryFiles = new HashSet<File>();
  final Set<ModuleDescriptor> myDependencies = new HashSet<ModuleDescriptor>();
  
  public ModuleDescriptor(final File contentRoot, final File sourceRoot) {
    myContentRoots.add(contentRoot);
    mySourceRoots.add(sourceRoot);
  }

  public Set<File> getContentRoots() {
    return myContentRoots;
  }

  public Set<File> getSourceRoots() {
    return mySourceRoots;
  }
  
  public void addContentRoot(File contentRoot) {
    myContentRoots.add(contentRoot);
  }
  
  public void removeContentRoot(File contentRoot) {
    myContentRoots.remove(contentRoot);
  }
  
  public void addSourceRoot(File sourceRoot) {
    mySourceRoots.add(sourceRoot);
  }
  
  public void addDependencyOn(ModuleDescriptor dependence) {
    myDependencies.add(dependence);
  }
  
  public void removeDependencyOn(ModuleDescriptor module) {
    myDependencies.remove(module);
  }
  
  public void addLibraryFile(File libFile) {
    myLibraryFiles.add(libFile);
  }

  public Set<File> getLibraryFiles() {
    return myLibraryFiles;
  }

  public Set<ModuleDescriptor> getDependencies() {
    return Collections.unmodifiableSet(myDependencies);
  }

  /**
   * For debug purposes only
   */
  public String toString() {
    @NonNls final StringBuilder builder = new StringBuilder();
    builder.append("[Module: ").append(myContentRoots).append(" | ");
    for (File sourceRoot : mySourceRoots) {
      builder.append(sourceRoot.getName()).append(",");
    }
    builder.append("]");
    return builder.toString();
  }
}
