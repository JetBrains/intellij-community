package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.RemoteRepositoryInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * @author nik
 */
public class LibraryDownloadingMirrorsMap {
  private Map<RemoteRepositoryInfo, String> myMirrors = new HashMap<RemoteRepositoryInfo, String>();

  public LibraryDownloadingMirrorsMap() {
  }

  public LibraryDownloadingMirrorsMap(RemoteRepositoryInfo[] remoteRepositories) {
    for (RemoteRepositoryInfo remoteRepository : remoteRepositories) {
      registerRepository(remoteRepository);
    }
  }

  private void registerRepository(final RemoteRepositoryInfo remoteRepository) {
    if (!myMirrors.containsKey(remoteRepository)) {
      myMirrors.put(remoteRepository, remoteRepository.getDefaultMirror());
    }
  }

  public LibraryDownloadingMirrorsMap(LibraryDownloadInfo[] libraryDownloadInfos) {
    for (LibraryDownloadInfo downloadInfo : libraryDownloadInfos) {
      RemoteRepositoryInfo remoteRepository = downloadInfo.getRemoteRepository();
      if (remoteRepository != null) {
        registerRepository(remoteRepository);
      }
    }
  }

  public List<RemoteRepositoryInfo> getRemoteRepositories() {
    return new ArrayList<RemoteRepositoryInfo>(myMirrors.keySet());
  }

  public String getDownloadingUrl(LibraryDownloadInfo downloadInfo) {
    String mirror = getSelectedMirror(downloadInfo);
    if (mirror != null) {
      return downloadInfo.getDownloadUrl(mirror);
    }
    return downloadInfo.getDownloadUrl();
  }

  @Nullable
  private String getSelectedMirror(@NotNull LibraryDownloadInfo downloadInfo) {
    RemoteRepositoryInfo remoteRepository = downloadInfo.getRemoteRepository();
    return remoteRepository != null ? myMirrors.get(remoteRepository) : null;
  }

  public String getSelectedMirror(@NotNull RemoteRepositoryInfo remoteRepository) {
    return myMirrors.get(remoteRepository);
  }

  public String getPresentableUrl(LibraryDownloadInfo downloadInfo) {
    String mirror = getSelectedMirror(downloadInfo);
    if (mirror != null) {
      return downloadInfo.getPresentableUrl(mirror);
    }
    return downloadInfo.getPresentableUrl();
  }

  public void setMirror(final RemoteRepositoryInfo remoteRepository, String mirror) {
    if (mirror == null) return;

    mirror = mirror.trim();
    if (!mirror.endsWith("/")) {
      mirror += "/";
    }
    myMirrors.put(remoteRepository, mirror);
  }
}
