package com.intellij.openapi.components.impl.stores;


import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.FileBasedStorage");

  private final String myFilePath;
  private final IFile myFile;
  protected final String myRootElementName;
  private Integer myUpToDateTreeHash;

  public FileBasedStorage(@Nullable PathMacroSubstitutor pathMacroManager, final String filePath, String rootElementName) {
    super(pathMacroManager);
    myRootElementName = rootElementName;
    myFilePath = filePath;
    myFile = FILE_SYSTEM.createFile(myFilePath);
  }

  public void doSave() throws StateStorage.StateStorageException {
    final Document document = getDocument();

    myUpToDateTreeHash = JDOMUtil.getTreeHash(document.getRootElement());
    final byte[] text = StorageUtil.printDocument(document);

    StorageUtil.save(myFile, text);
  }

  public boolean needsSave() throws StateStorage.StateStorageException {
    sort();

    final Document document = getDocument();
    if (myUpToDateTreeHash != null && JDOMUtil.getTreeHash(document.getRootElement()) == myUpToDateTreeHash.intValue()) return false;

    myUpToDateTreeHash = null;
    try {
      if (!myFile.exists()) return true;

      final byte[] text = StorageUtil.printDocument(document);

      if (Arrays.equals(myFile.loadBytes(), text)) {
        myUpToDateTreeHash = JDOMUtil.getTreeHash(document.getRootElement());
        return false;
      }

      return true;
    }
    catch (IOException e) {
      LOG.debug(e);
      return true;
    }
  }

  public List<VirtualFile> getAllStorageFiles() {
    final VirtualFile virtualFile = StorageUtil.getVirtualFile(myFile);
    if (virtualFile != null) return Collections.singletonList(virtualFile);
    return Collections.emptyList();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return StorageUtil.getVirtualFile(myFile);
  }


  public IFile getFile() {
    return myFile;
  }

  @Nullable
  protected Document loadDocument() throws StateStorage.StateStorageException {
    try {
      if (!myFile.exists() || myFile.length() == 0) {
        return new Document(new Element(myRootElementName));
      }
      else {
        return JDOMUtil.loadDocument(myFile);
      }
    }
    catch (JDOMException e) {
      throw new StateStorage.StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  public String getFileName() {
    return myFile.getName();
  }

  public String getFilePath() {
    return myFilePath;
  }

  public void setDefaultState(final Element element) {
    element.setName(myRootElementName);
    super.setDefaultState(element);
  }
}
