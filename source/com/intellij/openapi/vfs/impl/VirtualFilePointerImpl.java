package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.PathUtil;
import org.jdom.Element;

public class VirtualFilePointerImpl extends UserDataHolderBase implements VirtualFilePointer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.SmartVirtualFilePointerImpl");
  private String myUrl; // is null when myFile is not null
  private VirtualFile myFile;
  private VirtualFilePointerListener myListener;
  private boolean myInitialized = false;
  private boolean myWasRecentlyValid = false;
  private boolean myIsDead = false;
  private VirtualFileManager myVirtualFileManager;

  VirtualFilePointerImpl(VirtualFile file, VirtualFilePointerListener listener, VirtualFileManager virtualFileManager) {
    LOG.assertTrue(file != null);
    myFile = file;
    myUrl = null;
    myListener = listener;
    myVirtualFileManager = virtualFileManager;
  }

  VirtualFilePointerImpl(String url, VirtualFilePointerListener listener, VirtualFileManager virtualFileManager) {
    LOG.assertTrue(url != null);
    myFile = null;
    myUrl = url;
    myListener = listener;
    myVirtualFileManager = virtualFileManager;
  }

  VirtualFilePointerImpl(VirtualFilePointerImpl that, VirtualFilePointerListener listener, VirtualFileManager virtualFileManager) {
    if (that.myFile != null) {
      myFile = that.myFile;
      myUrl = that.myUrl;
    } else {
      myFile = null;
      myUrl = that.myUrl;
    }
    myListener = listener;
    myVirtualFileManager = virtualFileManager;
  }

  public String getFileName() {
    LOG.assertTrue(!myIsDead);
    if (!myInitialized) update();

    if (myFile != null) {
      return myFile.getName();
    } else {
      int index = myUrl.lastIndexOf('/');
      return (index >= 0) ? myUrl.substring(index + 1) : myUrl;
    }
  }

  public VirtualFile getFile() {
    LOG.assertTrue(!myIsDead);
    if (!myInitialized) update();
    return myFile;
  }

  public String getUrl() {
    LOG.assertTrue(!myIsDead);
    if (!myInitialized) update();
    if (myUrl != null) {
      return myUrl;
    } else {
      return myFile.getUrl();
    }
  }

  public String getPresentableUrl() {
    LOG.assertTrue(!myIsDead);
    if (!myInitialized) update();

    return PathUtil.toPresentableUrl(getUrl());
  }

  public boolean isValid() {
    LOG.assertTrue(!myIsDead);
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

  public VirtualFilePointerListener getListener() {
    return myListener;
  }

  void die() {
    myIsDead = true;
  }

  boolean isDead() {
    return myIsDead;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myUrl = element.getAttributeValue("url");
    myFile = null;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!myInitialized) update();

    element.setAttribute("url", getUrl());
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
