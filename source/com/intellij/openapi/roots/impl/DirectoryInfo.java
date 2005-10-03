package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ArrayListSet;

import java.util.Set;

public class DirectoryInfo {
  public Module module; // module to which content it belongs or null
  public boolean isInModuleSource; // true if files in this directory belongs to sources of the module (if field 'module' is not null)
  public boolean isTestSource; // (makes sense only if isInModuleSource is true)
  public boolean isInLibrarySource; // true if it's a directory with sources of some library
  public String packageName; // package name; makes sense only when at least one of isInModuleSource, isInLibrary or isInLibrarySource is true
  public VirtualFile libraryClassRoot; // class root in library
  public VirtualFile contentRoot;
  public VirtualFile sourceRoot;

  /**
   *  orderEntry to (classes of) which a directory belongs
   */
  public Set<OrderEntry> orderEntries = new ArrayListSet<OrderEntry>();

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DirectoryInfo)) return false;

    final DirectoryInfo info = (DirectoryInfo)o;

    if (isInLibrarySource != info.isInLibrarySource) return false;
    if (isInModuleSource != info.isInModuleSource) return false;
    if (isTestSource != info.isTestSource) return false;
    if (module != null ? !module.equals(info.module) : info.module != null) return false;
    if (packageName != null ? !packageName.equals(info.packageName) : info.packageName != null) return false;
    if (orderEntries != null ? !orderEntries.equals(info.orderEntries) : info.orderEntries != null) return false;
    if (!Comparing.equal(libraryClassRoot, info.libraryClassRoot)) return false;
    if (!Comparing.equal(contentRoot, info.contentRoot)) return false;
    if (!Comparing.equal(sourceRoot, info.sourceRoot)) return false;

    return true;
  }

  public int hashCode() {
    return (packageName != null ? packageName.hashCode() : 0);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DirectoryInfo{" +
           "module=" + module +
           ", isInModuleSource=" + isInModuleSource +
           ", isTestSource=" + isTestSource +
           ", isInLibrarySource=" + isInLibrarySource +
           ", packageName=" + packageName +
           ", libraryClassRoot=" + libraryClassRoot +
           ", contentRoot=" + contentRoot +
           ", sourceRoot=" + sourceRoot +
           "}";
  }
}
