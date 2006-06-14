package com.intellij.openapi.roots.ui.util;

import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.LightFilePointer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import javax.swing.*;
import java.io.File;

// Author: dyoma

public class CellAppearanceUtils {
  public static final Icon FOLDER_ICON = IconLoader.getIcon("/nodes/folder.png");
  public static final Icon INVALID_ICON = IconLoader.getIcon("/nodes/ppInvalid.png");
  public static final Icon SOURCE_FOLDERS_ICON = IconLoader.getIcon("/nodes/sourceFolder.png");
  public static final Icon JAVA_DOC_FOLDER = IconLoader.getIcon("/nodes/javaDocFolder.png");
  public static final Icon TEST_SOURCE_FOLDER = IconLoader.getIcon("/nodes/testSourceFolder.png");
  public static final Icon CLASSES_FOLDER = IconLoader.getIcon("/nodes/compiledClassesFolder.png");
  public static final Icon EXCLUDE_FOLDERS_ICON = excludeIcon(SOURCE_FOLDERS_ICON);
  public static final Icon EXCLUDE_FOLDER_ICON = excludeIcon(FOLDER_ICON);
  public static final CellAppearance EMPTY = new EmptyAppearance();
  public static final Icon GENERIC_JDK_ICON = IconLoader.getIcon("/general/jdk.png");
  public static final String NO_JDK = ProjectBundle.message("jdk.missing.item");

  public static SimpleTextAttributes createSimpleCellAttributes(boolean isSelected){
    return isSelected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
  }


  public static CellAppearance forVirtualFilePointer(VirtualFilePointer filePointer) {
    return filePointer.isValid() ?
           forValidVirtualFile(filePointer.getFile()) :
           forInvalidVirtualFilePointer(filePointer);
  }

  public static CellAppearance forVirtualFile(VirtualFile virtualFile) {
    return virtualFile.isValid() ?
           forValidVirtualFile(virtualFile) :
           forInvalidVirtualFilePointer(new LightFilePointer(virtualFile.getUrl()));
  }

  private static SimpleTextCellAppearance forInvalidVirtualFilePointer(VirtualFilePointer filePointer) {
    return SimpleTextCellAppearance.invalid(filePointer.getPresentableUrl(), INVALID_ICON);
  }

  private static CellAppearance forValidVirtualFile(VirtualFile virtualFile) {
    final VirtualFileSystem fileSystem = virtualFile.getFileSystem();
    if (fileSystem.getProtocol().equals(JarFileSystem.PROTOCOL)) {
      return new JarSubfileCellAppearance(virtualFile);
    }
    if (fileSystem instanceof HttpFileSystem) {
      return new HttpUrlCellAppearance(virtualFile);
    }
    if (virtualFile.isDirectory()) {
      return SimpleTextCellAppearance.normal(virtualFile.getPresentableUrl(), FOLDER_ICON);
    }
    return new ValidFileCellAppearance(virtualFile);
  }

  public static Icon iconForFile(VirtualFile file) {
    if (file.getFileSystem().getProtocol().equals(JarFileSystem.PROTOCOL) && file.getParent() == null) {
      return file.getIcon();
    }
    if (file.isDirectory()) return FOLDER_ICON;
    return file.getIcon();
  }

  public static CellAppearance forOrderEntry(OrderEntry orderEntry, boolean selected) {
    if (orderEntry instanceof JdkOrderEntry) {
      JdkOrderEntry jdkLibraryEntry = (JdkOrderEntry)orderEntry;
      ProjectJdk jdk = jdkLibraryEntry.getJdk();
      if (!orderEntry.isValid()) {
        return SimpleTextCellAppearance.invalid(jdkLibraryEntry.getJdkName(), INVALID_ICON);
      }
      return forJdk(jdk, false, selected);
    }
    else if (!orderEntry.isValid()) {
      return SimpleTextCellAppearance.invalid(orderEntry.getPresentableName(), INVALID_ICON);
    }
    else if (orderEntry instanceof LibraryOrderEntry) {
      LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
      final Library library = libraryOrderEntry.getLibrary();
      if (!libraryOrderEntry.isValid()){ //library can be removed
        return SimpleTextCellAppearance.invalid(orderEntry.getPresentableName(), INVALID_ICON);
      }
      return forLibrary(library);
    }
    else if (orderEntry.isSynthetic()) {
      String presentableName = orderEntry.getPresentableName();
      Icon icon = orderEntry instanceof ModuleSourceOrderEntry ? sourceFolderIcon(false) : null;
      return new SimpleTextCellAppearance(presentableName, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
    else if (orderEntry instanceof ModuleOrderEntry) {
      final Icon icon = IconUtilEx.getIcon(((ModuleOrderEntry)orderEntry).getModule(), 0);
      return SimpleTextCellAppearance.normal(orderEntry.getPresentableName(), icon);
    }
    else return CompositeAppearance.single(orderEntry.getPresentableName());
  }

  public static CellAppearance forLibrary(Library library) {
    String name = library.getName();
    if (name != null) {
      return SimpleTextCellAppearance.normal(name, Icons.LIBRARY_ICON);
    }
    String[] files = library.getUrls(OrderRootType.CLASSES);
    if (files.length == 0) {
      return SimpleTextCellAppearance.invalid(ProjectBundle.message("library.empty.library.item"), Icons.LIBRARY_ICON);
    }
    if (files.length == 1) {
      return forVirtualFilePointer(new LightFilePointer(files[0]));
    }
    throw new RuntimeException(library.toString());
  }

  public static SimpleTextCellAppearance forSourceFolder(SourceFolder folder) {
    return formatRelativePath(folder, FOLDER_ICON);
  }

  public static Icon sourceFolderIcon(boolean testSource) {
    return testSource ? TEST_SOURCE_FOLDER : SOURCE_FOLDERS_ICON;
  }

  public static CellAppearance forExcludeFolder(ExcludeFolder folder) {
    return formatRelativePath(folder, EXCLUDE_FOLDER_ICON);
  }

  public static CellAppearance forContentFolder(ContentFolder folder) {
    if (folder instanceof SourceFolder) {
      return forSourceFolder((SourceFolder)folder);
    }
    else if (folder instanceof ExcludeFolder) {
      return forExcludeFolder((ExcludeFolder)folder);
    }
    else {
      throw new RuntimeException(folder.getClass().getName());
    }
  }

  public static CellAppearance forModule(Module module) {
    return SimpleTextCellAppearance.normal(module.getName(), IconUtilEx.getIcon(module, 0));
  }

  public static CellAppearance forContentEntry(ContentEntry contentEntry) {
    return forVirtualFilePointer(new LightFilePointer(contentEntry.getUrl()));
  }

  public static SimpleTextCellAppearance formatRelativePath(ContentFolder folder, Icon icon) {
    VirtualFilePointer contentFile = new LightFilePointer(folder.getContentEntry().getUrl());
    VirtualFilePointer folderFile = new LightFilePointer(folder.getUrl());
    if (!contentFile.isValid()) return forInvalidVirtualFilePointer(folderFile);
    String contentPath = contentFile.getFile().getPath();
    char separator = File.separatorChar;
    String relativePath;
    SimpleTextAttributes textAttributes;
    if (!folderFile.isValid()) {
      textAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
      String absolutePath = folderFile.getPresentableUrl();
      relativePath =
      absolutePath.startsWith(contentPath) ? absolutePath.substring(contentPath.length()) : absolutePath;
    }
    else {
      relativePath = VfsUtil.getRelativePath(folderFile.getFile(), contentFile.getFile(), separator);
      textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    if (relativePath == null) relativePath = "";
    relativePath = relativePath.length() == 0 ? "." + File.separatorChar : relativePath;
    return new SimpleTextCellAppearance(relativePath, icon, textAttributes);
  }

  public static CellAppearance forJdk(ProjectJdk jdk, boolean isInComboBox, final boolean selected) {
    if (jdk == null) {
      return SimpleTextCellAppearance.invalid(NO_JDK, INVALID_ICON);
    }
    String name = jdk.getName();
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.setIcon(jdk.getSdkType().getIcon());
    VirtualFile homeDirectory = jdk.getHomeDirectory();
    SimpleTextAttributes attributes = (homeDirectory != null && homeDirectory.isValid())
                                      ? createSimpleCellAttributes(selected)
                                      : SimpleTextAttributes.ERROR_ATTRIBUTES;
    CompositeAppearance.DequeEnd ending = appearance.getEnding();
    ending.addText(name, attributes);
    String versionString = jdk.getVersionString();
    if (versionString != null && !versionString.equals(name)) {
      SimpleTextAttributes textAttributes = isInComboBox ? SimpleTextAttributes.SYNTHETIC_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
      ending.addComment(versionString, textAttributes);
    }
    return ending.getAppearance();
  }

  public static Icon excludeIcon(Icon icon) {
    return IconLoader.getDisabledIcon(icon);
  }

  public static CompositeAppearance forFile(File file) {
    String absolutePath = file.getAbsolutePath();
    if (!file.exists()) return CompositeAppearance.invalid(absolutePath);
    if (file.isDirectory()) {
      CompositeAppearance appearance = CompositeAppearance.single(absolutePath);
      appearance.setIcon(FOLDER_ICON);
      return appearance;
    }
    String name = file.getName();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    File parent = file.getParentFile();
    CompositeAppearance appearance = CompositeAppearance.textComment(name, parent.getAbsolutePath());
    appearance.setIcon(fileType.getIcon());
    return appearance;
  }

  public static CellAppearance forProjectJdk(final Project project) {
    final ProjectRootManager projectRootManager = ProjectRootManagerEx.getInstance(project);
    final ProjectJdk projectJdk = projectRootManager.getProjectJdk();
    final CellAppearance appearance;
    if (projectJdk != null) {
      appearance = forJdk(projectJdk, false, false);
    }
    else {
      // probably invalid JDK
      final String projectJdkName = projectRootManager.getProjectJdkName();
      if (projectJdkName != null) {
        appearance = SimpleTextCellAppearance.invalid(ProjectBundle.message("jdk.combo.box.invalid.item", projectJdkName), INVALID_ICON);
      }
      else {
        appearance = forJdk(null, false, false);
      }
    }
    return appearance;
  }

  private static class EmptyAppearance implements CellAppearance {
    public void customize(SimpleColoredComponent component) {
    }

    public String getText() {
      return "";
    }
  }
}
