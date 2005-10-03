package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 *  @author dsl
 */
public abstract class ContentFolderBaseImpl extends RootModelComponentBase implements ContentFolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleContentFolderBaseImpl");
  private VirtualFilePointer myFilePointer;
  protected final ContentEntryImpl myContentEntry;
  @NonNls protected static final String URL_ATTR = "url";

  ContentFolderBaseImpl(VirtualFile file, ContentEntryImpl contentEntry) {
    super(contentEntry.getRootModel());
    myContentEntry = contentEntry;
    myFilePointer = contentEntry.getRootModel().pointerFactory().create(file);
  }

  protected ContentFolderBaseImpl(ContentFolderBaseImpl that, ContentEntryImpl contentEntry) {
    super(contentEntry.getRootModel());
    myContentEntry = contentEntry;
    myFilePointer =
    contentEntry.getRootModel().pointerFactory().duplicate(that.myFilePointer);
  }

  ContentFolderBaseImpl(Element element, ContentEntryImpl contentEntry) throws InvalidDataException {
    super(contentEntry.getRootModel());
    final String path = element.getAttributeValue(URL_ATTR);
    if (path == null) throw new InvalidDataException();
    myContentEntry = contentEntry;
    myFilePointer = myContentEntry.getRootModel().pointerFactory().create(path);
  }

  public VirtualFile getFile() {
    final VirtualFile file = myFilePointer.getFile();
    if (file == null || file.isDirectory()) {
      return file;
    }
    else {
      return null;
    }
  }

  public ContentEntry getContentEntry() {
    return myContentEntry;
  }

  protected void writeFolder(Element element, String elementName) {
    LOG.assertTrue(element.getName().equals(elementName));
    element.setAttribute(URL_ATTR, myFilePointer.getUrl());
  }

  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public boolean isSynthetic() {
    return false;
  }

  protected void dispose() {
    super.dispose();
    VirtualFilePointerManager.getInstance().kill(myFilePointer);
  }
}
