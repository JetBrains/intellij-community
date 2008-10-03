package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
*/
public class LibraryCompositionSettings {
  @NonNls private static final String DEFAULT_LIB_FOLDER = "lib";
  private final LibraryInfo[] myLibraryInfos;
  private final String myBaseDirectoryForDownloadedFiles;
  private final String myTitle;
  private String myDirectoryForDownloadedLibrariesPath;
  private Set<VirtualFile> myAddedJars = new LinkedHashSet<VirtualFile>();
  private boolean myDownloadLibraries = true;
  private Set<Library> myUsedLibraries = new LinkedHashSet<Library>();
  private LibrariesContainer.LibraryLevel myLibraryLevel = LibrariesContainer.LibraryLevel.PROJECT;
  private String myLibraryName;
  private Icon myIcon;

  public LibraryCompositionSettings(final @NotNull LibraryInfo[] libraryInfos,
                                    final @NotNull String defaultLibraryName,
                                    final @NotNull String baseDirectoryForDownloadedFiles,
                                    final String title, @Nullable Icon icon) {
    myLibraryInfos = libraryInfos;
    myBaseDirectoryForDownloadedFiles = baseDirectoryForDownloadedFiles;
    myTitle = title;
    myLibraryName = defaultLibraryName;
    myIcon = icon;
  }

  @NotNull
  public LibraryInfo[] getLibraryInfos() {
    return myLibraryInfos;
  }

  @NotNull
  public String getBaseDirectoryForDownloadedFiles() {
    return myBaseDirectoryForDownloadedFiles;
  }

  public void setDirectoryForDownloadedLibrariesPath(final String directoryForDownloadedLibrariesPath) {
    myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
  }

  public Set<VirtualFile> getAddedJars() {
    return myAddedJars;
  }

  public void setAddedJars(final Collection<VirtualFile> addedJars) {
    myAddedJars.clear();
    myAddedJars.addAll(addedJars);
  }

  public boolean isDownloadLibraries() {
    return myDownloadLibraries;
  }

  public void setDownloadLibraries(final boolean downloadLibraries) {
    myDownloadLibraries = downloadLibraries;
  }

  public void setUsedLibraries(Collection<Library> addedLibraries) {
    myUsedLibraries.clear();
    myUsedLibraries.addAll(addedLibraries);
  }

  public void setLibraryLevel(final LibrariesContainer.LibraryLevel libraryLevel) {
    myLibraryLevel = libraryLevel;
  }

  public void setLibraryName(final String libraryName) {
    myLibraryName = libraryName;
  }

  public String getDirectoryForDownloadedLibrariesPath() {
    if (myDirectoryForDownloadedLibrariesPath == null) {
      myDirectoryForDownloadedLibrariesPath = myBaseDirectoryForDownloadedFiles + "/" + DEFAULT_LIB_FOLDER;
    }
    return myDirectoryForDownloadedLibrariesPath;
  }

  public String getTitle() {
    return myTitle;
  }

  public boolean downloadFiles(final @NotNull LibraryDownloadingMirrorsMap mirrorsMap, @NotNull LibrariesContainer librariesContainer, final @NotNull JComponent parent) {
    if (myDownloadLibraries) {
      RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(getLibraryInfos());

      List<VirtualFile> roots = new ArrayList<VirtualFile>();
      roots.addAll(myAddedJars);
      for (Library library : myUsedLibraries) {
        roots.addAll(Arrays.asList(librariesContainer.getLibraryFiles(library, OrderRootType.CLASSES)));
      }
      VirtualFile[] jars = roots.toArray(new VirtualFile[roots.size()]);
      RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = requiredLibraries.checkLibraries(jars);
      if (info != null) {
        LibraryDownloadInfo[] downloadingInfos = LibraryDownloader.getDownloadingInfos(info.getLibraryInfos());
        if (downloadingInfos.length > 0) {
          LibraryDownloader downloader = new LibraryDownloader(downloadingInfos, null, parent,
                                                               getDirectoryForDownloadedLibrariesPath(), myLibraryName,
                                                               mirrorsMap);
          VirtualFile[] files = downloader.download();
          if (files.length != downloadingInfos.length) {
            return false;
          }
          myAddedJars.addAll(Arrays.asList(files));
        }
      }
    }
    return true;
  }

  @Nullable
  private Library createLibrary(final ModifiableRootModel rootModel, @Nullable LibrariesContainer additionalContainer) {
    if (!myAddedJars.isEmpty()) {
      VirtualFile[] roots = myAddedJars.toArray(new VirtualFile[myAddedJars.size()]);
      return LibrariesContainerFactory.createLibrary(additionalContainer, LibrariesContainerFactory.createContainer(rootModel),
                                                     myLibraryName, myLibraryLevel, roots, VirtualFile.EMPTY_ARRAY);
    }
    return null;
  }

  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return myLibraryLevel;
  }

  public String getLibraryName() {
    return myLibraryName;
  }

  public Collection<Library> getUsedLibraries() {
    return Collections.unmodifiableCollection(myUsedLibraries);
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public Library addLibraries(final ModifiableRootModel rootModel, final List<Library> addedLibraries) {
    return addLibraries(rootModel, addedLibraries, null);
  }

  @Nullable
  public Library addLibraries(final @NotNull ModifiableRootModel rootModel, final @NotNull List<Library> addedLibraries,
                              final @Nullable LibrariesContainer librariesContainer) {
    Library library = createLibrary(rootModel, librariesContainer);

    if (library != null) {
      addedLibraries.add(library);
      if (getLibraryLevel() != LibrariesContainer.LibraryLevel.MODULE) {
        rootModel.addLibraryEntry(library);
      }
    }
    for (Library usedLibrary : getUsedLibraries()) {
      addedLibraries.add(usedLibrary);
      rootModel.addLibraryEntry(usedLibrary);
    }
    return library;
  }
}
