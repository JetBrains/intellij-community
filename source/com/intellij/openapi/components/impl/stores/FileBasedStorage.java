package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DOMUtil;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.FileBasedStorage");

  private final String myFilePath;
  private final String myRootElementName;
  private Document myDocument;
  private final File myFile;
  private static final String UTF_8 = "utf-8";

  public FileBasedStorage(@Nullable PathMacroSubstitutor pathMacroManager, final String filePath, String rootElementName) {
    super(pathMacroManager);
    myFilePath = filePath;
    myRootElementName = rootElementName;
    myFile = new File(myFilePath);
  }

  @Nullable
  protected Element getRootElement() throws StateStorageException {
    return getDocument().getDocumentElement();
  }

  public void save() throws StateStorageException {
    if (!needsSave()) return;

    try {
      final Ref<IOException> refIOException = Ref.create(null);
      try {
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
      }
      finally {
        if (refIOException.get() != null) {
          throw refIOException.get();
        }
      }
    }
    catch (StateStorageException e) {
      throw new StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
  }

  public boolean needsSave() throws StateStorageException {
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

  private byte[] printDocument() throws StateStorageException {
    try {
      return DOMUtil.print(getDocument()).getBytes(UTF_8);
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
      throw new StateStorageException(e);
    }
  }

  public List<VirtualFile> getAllStorageFiles() {
    final VirtualFile virtualFile = getVirtualFile(FILE_SYSTEM.createFile(myFilePath));
    if (virtualFile != null && !virtualFile.isWritable()) return Collections.singletonList(virtualFile);
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
  public VirtualFile getVirtualFile() throws IOException {
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

  public Document getDocument() throws StateStorageException {
    if (myDocument == null) {
      try {
        if (!myFile.exists() || myFile.length() == 0) {
          myDocument = DOMUtil.createDocument();
          final Element rootElement = myDocument.createElement(myRootElementName);
          myDocument.appendChild(rootElement);
        }
        else {
          myDocument = DOMUtil.load(myFile);
        }
      }
      catch (IOException e) {
        throw new StateStorageException(e);
      }
      catch (ParserConfigurationException e) {
        throw new StateStorageException(e);
      }
      catch (SAXException e) {
        throw new StateStorageException(e);
      }
    }

    return myDocument;
  }

  public String getFileName() {
    return myFile.getName();
  }

  public String getFilePath() {
    return myFilePath;
  }
}
