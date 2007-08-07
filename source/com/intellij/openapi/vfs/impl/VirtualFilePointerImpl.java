package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class VirtualFilePointerImpl extends UserDataHolderBase implements VirtualFilePointer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.SmartVirtualFilePointerImpl");
  private String myUrl; // is null when myFile is not null
  private VirtualFile myFile;
  private boolean myInitialized = false;
  private boolean myWasRecentlyValid = false;
  private final VirtualFileManager myVirtualFileManager;
  @NonNls private static final String ATTRIBUTE_URL = "url";

  VirtualFilePointerImpl(VirtualFile file, VirtualFileManager virtualFileManager) {
    LOG.assertTrue(file != null,"Virtual file should not be null!");
    myFile = file;
    myUrl = null;
    myVirtualFileManager = virtualFileManager;
  }

  VirtualFilePointerImpl(String url, VirtualFileManager virtualFileManager) {
    LOG.assertTrue(url != null, "Url should not be null!");
    myFile = null;
    myUrl = url;
    myVirtualFileManager = virtualFileManager;
  }

  @NotNull
  public String getFileName() {
    if (!myInitialized) update();

    if (myFile != null) {
      return myFile.getName();
    } else {
      int index = myUrl.lastIndexOf('/');
      return (index >= 0) ? myUrl.substring(index + 1) : myUrl;
    }
  }

  public VirtualFile getFile() {
    if (!myInitialized) update();
    if (myFile != null && !myFile.isValid()) {
      myUrl = myFile.getUrl();
      myFile = null;
      update();
    }
    return myFile;
  }

  @NotNull
  public String getUrl() {
    if (!myInitialized) update();
    if (myUrl != null) {
      return myUrl;
    } else {
      return myFile.getUrl();
    }
  }

  @NotNull
  public String getPresentableUrl() {
    if (!myInitialized) update();

    return PathUtil.toPresentableUrl(getUrl());
  }

  public boolean isValid() {
    if (!myInitialized) update();

    return myFile != null; // && myFile.isValid();
  }

  public void update() {
    myInitialized = true;

    if (!isValid()) {
      LOG.assertTrue(myUrl != null);
      myFile = myVirtualFileManager.findFileByUrl(myUrl);
      if (myFile != null) {
        myUrl = null;
      }
    }
    else if (!myFile.exists()) {
      myUrl = myFile.getUrl();
      myFile = null;
    }

    myWasRecentlyValid = isValid();
  }

  public void invalidateByDeletion() {
    myInitialized = true;
    LOG.assertTrue(myFile != null);
    myUrl = myFile.getUrl();
    myFile = null;
    myWasRecentlyValid = false;
  }

  public boolean wasRecentlyValid() {
    return myWasRecentlyValid;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myUrl = element.getAttributeValue(ATTRIBUTE_URL);
    myFile = null;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!myInitialized) update();

    element.setAttribute(ATTRIBUTE_URL, getUrl());
  }

  boolean willValidityChange() {
    if (!myInitialized) update();

    if (myWasRecentlyValid) {
      LOG.assertTrue(myFile != null);
      return !myFile.isValid();
    }
    else {
      LOG.assertTrue(myUrl != null);
      final VirtualFile fileByUrl = myVirtualFileManager.findFileByUrl(myUrl);
      return fileByUrl != null;
    }
  }
}
