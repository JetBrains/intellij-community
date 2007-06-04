package com.intellij.openapi.components.impl.stores;


import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.FileBasedStorage");

  private final String myFilePath;
  private final IFile myFile;
  protected final String myRootElementName;
  private Integer myUpToDateHash;

  public FileBasedStorage(
    @Nullable TrackingPathMacroSubstitutor pathMacroManager,
    final String filePath,
    String rootElementName,
    Disposable parentDisposable,
    PicoContainer picoContainer) {
    super(pathMacroManager, parentDisposable, rootElementName);

    myRootElementName = rootElementName;
    myFilePath = filePath;
    myFile = FILE_SYSTEM.createFile(myFilePath);

    VirtualFileTracker virtualFileTracker = (VirtualFileTracker)picoContainer.getComponentInstanceOfType(VirtualFileTracker.class);

    final String path = myFile.getAbsolutePath();
    final String fileUrl = LocalFileSystem.PROTOCOL + "://" + path.replace(File.separatorChar, '/');

    MessageBus messageBus = (MessageBus)picoContainer.getComponentInstanceOfType(MessageBus.class);

    final Listener listener = messageBus.syncPublisher(StateStorage.STORAGE_TOPIC);

    virtualFileTracker.addTracker(fileUrl, new VirtualFileAdapter() {
      public void contentsChanged(final VirtualFileEvent event) {
        listener.storageFileChanged(event);
      }
    }, true, this);
  }

  protected MySaveSession createSaveSession(final XmlElementStorage.MyExternalizationSession externalizationSession) {
    return new FileSaveSession(externalizationSession);
  }

  protected class FileSaveSession extends MySaveSession {
    public FileSaveSession(MyExternalizationSession externalizationSession) {
      super(externalizationSession);
    }

    protected boolean _needsSave() throws StateStorageException {
      final int hash = myStorageData.getHash();

      if (myUpToDateHash != null && hash == myUpToDateHash.intValue()) return false;

      myUpToDateHash = null;
      try {
        if (!myFile.exists()) return true;

        final byte[] text = StorageUtil.printDocument(getDocumentToSave());

        if (Arrays.equals(myFile.loadBytes(), text)) {
          myUpToDateHash = hash;
          return false;
        }

        return true;
      }
      catch (IOException e) {
        LOG.debug(e);
        return true;
      }
    }

    protected void doSave() throws StateStorageException {
      myUpToDateHash = myStorageData.getHash();

      final byte[] text = StorageUtil.printDocument(getDocumentToSave());

      StorageUtil.save(myFile, text);
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
        return null;
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
