// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;

/**
 * TODO: Merge PortableStatus and Status to single class.
 *
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 */
public class PortableStatus extends Status {

  private boolean myConflicted;
  private Getter<Info> myInfoGetter;
  private Info myInfo;
  private String myPath;
  private boolean myFileExists;

  public PortableStatus() {
    myInfoGetter = () -> null;
    setCommittedRevision(Revision.UNDEFINED);
  }

  @Override
  public void setIsConflicted(boolean isConflicted) {
    myConflicted = isConflicted;
    super.setIsConflicted(isConflicted);
  }

  public void setInfoGetter(Getter<Info> infoGetter) {
    myInfoGetter = infoGetter;
  }

  @Override
  public boolean isConflicted() {
    return myConflicted;
  }

  private Info initInfo() {
    if (myInfo == null) {
      final StatusType contentsStatus = getContentsStatus();
      if (contentsStatus == null || StatusType.UNKNOWN.equals(contentsStatus)) {
        return null;
      }
      myInfo = myInfoGetter.get();
    }
    return myInfo;
  }

  public Info getInfo() {
    return initInfo();
  }

  @Override
  @NotNull
  public NodeKind getKind() {
    if (myFileExists) return super.getKind();
    final Info info = initInfo();
    if (info != null) {
      return info.getNodeKind();
    }
    return super.getKind();
  }

  /**
   * Gets the URL (repository location) of the ancestor from which the item
   * was copied. That is when the item is added with history.
   *
   * @return the item ancestor's URL
   */
  @Override
  public Url getCopyFromURL() {
    if (! isCopied()) return null;
    final Info info = initInfo();
    if (info == null) return null;
    return initInfo().getCopyFromUrl();
  }

  @Override
  public Url getURL() {
    Url url = super.getURL();

    if (url == null) {
      Info info = initInfo();
      url = info != null ? info.getUrl() : url;
    }

    return url;
  }

  @Override
  public Url getRepositoryRootURL() {
    Url url = super.getRepositoryRootURL();

    if (url == null) {
      Info info = initInfo();
      url = info != null ? info.getRepositoryRootUrl() : url;
    }

    return url;
  }

  @Override
  public File getFile() {
    File file = super.getFile();

    if (file == null) {
      Info info = initInfo();
      file = info != null ? info.getFile() : file;
    }

    return file;
  }

  @NotNull
  @Override
  public Revision getRevision() {
    final Revision revision = super.getRevision();
    if (revision.isValid()) return revision;

    final StatusType status = getContentsStatus();
    if (StatusType.STATUS_NONE.equals(status) || StatusType.STATUS_UNVERSIONED.equals(status) ||
        StatusType.STATUS_ADDED.equals(status)) return revision;

    final Info info = initInfo();
    return info == null ? revision : info.getRevision();
  }

  /**
   * Returns a tree conflict description.
   *
   * @return tree conflict description; {@code null} if no conflict
   *         description exists on this item
   * @since 1.3
   */
  @Override
  @Nullable
  public TreeConflictDescription getTreeConflict() {
    if (! isConflicted()) return null;
    final Info info = initInfo();
    return info == null ? null : info.getTreeConflict();
  }

  public void setPath(String path) {
    myPath = path;
  }

  public String getPath() {
    return myPath;
  }

  public void setKind(boolean exists, @NotNull NodeKind kind) {
    myFileExists = exists;
    setKind(kind);
  }
}
