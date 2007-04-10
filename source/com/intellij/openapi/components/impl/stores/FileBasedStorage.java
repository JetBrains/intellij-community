package com.intellij.openapi.components.impl.stores;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.FileBasedStorage");

  private final String myFilePath;
  private final IFile myFile;
  protected final String myRootElementName;

  public FileBasedStorage(@Nullable PathMacroSubstitutor pathMacroManager, final String filePath, String rootElementName) {
    super(pathMacroManager);
    myRootElementName = rootElementName;
    myFilePath = filePath;
    myFile = FILE_SYSTEM.createFile(myFilePath);
  }

  public void doSave() throws StateStorage.StateStorageException {
    try {
      final Ref<IOException> refIOException = Ref.create(null);
      final byte[] text = printDocument();

      final IFile ioFile = FILE_SYSTEM.createFile(myFilePath);

      if (ioFile.exists()) {
        final byte[] bytes = ioFile.loadBytes();
        if (Arrays.equals(bytes, text)) return;
        IFile backupFile = deleteBackup(myFilePath);
        ioFile.renameTo(backupFile);
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (!ioFile.exists()) {
            ioFile.createParentDirs();
          }

          try {
            getOrCreateVirtualFile(ioFile).setBinaryContent(text);
          }
          catch (IOException e) {
            refIOException.set(e);
          }

          deleteBackup(myFilePath);
        }
      });
      if (refIOException.get() != null) {
        throw new StateStorage.StateStorageException(refIOException.get());
      }
    }
    catch (StateStorage.StateStorageException e) {
      throw new StateStorage.StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  public boolean needsSave() throws StateStorage.StateStorageException {
    sort();
    try {
      final IFile ioFile = FILE_SYSTEM.createFile(myFilePath);
      if (!ioFile.exists()) return true;

      final byte[] text = printDocument();

      if (Arrays.equals(ioFile.loadBytes(), text)) return false;

      return true;
    }
    catch (IOException e) {
      LOG.debug(e);
      return true;
    }
  }

  private byte[] printDocument() throws StateStorage.StateStorageException {
    try {
      return JDOMUtil.writeDocument(getDocument(), SystemProperties.getLineSeparator()).getBytes(CharsetToolkit.UTF8);
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
      throw new StateStorage.StateStorageException(e);
    }
  }

  public List<VirtualFile> getAllStorageFiles() {
    final VirtualFile virtualFile = getVirtualFile(FILE_SYSTEM.createFile(myFilePath));
    if (virtualFile != null) return Collections.singletonList(virtualFile);
    return Collections.emptyList();
  }

  private static IFile deleteBackup(final String path) {
    IFile backupFile = FILE_SYSTEM.createFile(path + "~");
    if (backupFile.exists()) {
      backupFile.delete();
    }
    return backupFile;
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return getVirtualFile(FILE_SYSTEM.createFile(myFilePath));
  }


  private VirtualFile getOrCreateVirtualFile(IFile ioFile) throws IOException {
    VirtualFile vFile = getVirtualFile(ioFile);

    if (vFile == null) {
      vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    }

    if (vFile == null) {
      final IFile parentFile = ioFile.getParentFile();
      final VirtualFile parentVFile =
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentFile); // need refresh if the directory has just been created
      if (parentVFile == null) {
        throw new IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile.getPath()));
      }
      vFile = parentVFile.createChildData(this, ioFile.getName());
    }

    return vFile;
  }

  @Nullable
  private static VirtualFile getVirtualFile(final IFile ioFile) {
    return LocalFileSystem.getInstance().findFileByIoFile(ioFile);
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
