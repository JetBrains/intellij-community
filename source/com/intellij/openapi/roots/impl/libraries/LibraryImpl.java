package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 *  @author dsl
 */
public class LibraryImpl implements LibraryEx.ModifiableModelEx, LibraryEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.impl.LibraryImpl");
  @NonNls static final String LIBRARY_NAME_ATTR = "name";
  @NonNls private static final String ROOT_PATH_ELEMENT = "root";
  @NonNls public static final String ELEMENT = "library";
  @NonNls private static final String JAR_DIRECTORY_ELEMENT = "jarDirectory";
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String RECURSIVE_ATTR = "recursive";
  private String myName;
  private final LibraryTable myLibraryTable;
  private Map<OrderRootType, VirtualFilePointerContainer> myRoots;
  private Map<String, Boolean> myJarDirectories = new HashMap<String, Boolean>();
  private List<LocalFileSystem.WatchRequest> myWatchRequests = new ArrayList<LocalFileSystem.WatchRequest>();
  private LibraryImpl mySource;

  private final MyRootProviderImpl myRootProvider = new MyRootProviderImpl();
  private ModifiableRootModel myRootModel;

  LibraryImpl(String name, LibraryTable table) {
    myName = name;
    myLibraryTable = table;
    myRoots = initRoots();
    mySource = null;
  }

  LibraryImpl(final LibraryTable table) {
    myLibraryTable = table;
    myRoots = initRoots();
    mySource = null; 
  }


  LibraryImpl() {
    myLibraryTable = null;
    myRoots = initRoots();
    mySource = null;
  }

  private LibraryImpl(LibraryImpl that) {
    myName = that.myName;
    myRoots = initRoots();
    mySource = that;
    myLibraryTable = that.myLibraryTable;
    for (OrderRootType rootType : SERIALIZABLE_ROOT_TYPES) {
      final VirtualFilePointerContainer thisContainer = myRoots.get(rootType);
      final VirtualFilePointerContainer thatContainer = that.myRoots.get(rootType);
      thisContainer.addAll(thatContainer);
    }
    myJarDirectories.putAll(that.myJarDirectories);
  }

  public void dispose() {
    if (myWatchRequests.size() > 0) {
      LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
      myWatchRequests.clear();
    }
  }

  public String getName() {
    return myName;
  }

  public String[] getUrls(OrderRootType rootType) {
    final VirtualFilePointerContainer result = myRoots.get(rootType);
    return result.getUrls();
  }

  public VirtualFile[] getFiles(OrderRootType rootType) {
    final List<VirtualFile> expanded = new ArrayList<VirtualFile>();
    for (VirtualFile file : myRoots.get(rootType).getFiles()) {
      if (file.isDirectory()) {
        final Boolean expandRecursively = myJarDirectories.get(file.getUrl());
        if (expandRecursively != null) {
          addChildren(file, expanded, expandRecursively.booleanValue());
          continue;
        }
      }
      expanded.add(file);
    }
    return expanded.toArray(new VirtualFile[expanded.size()]);
  }

  private static void addChildren(final VirtualFile dir, final List<VirtualFile> container, final boolean recursively) {
    for (VirtualFile child : dir.getChildren()) {
      final FileType fileType = child.getFileType();
      if (StdFileTypes.ARCHIVE.equals(fileType)) {
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          builder.append(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, child.getPath()));
          builder.append(JarFileSystem.JAR_SEPARATOR);
          final VirtualFile jarRoot = VirtualFileManager.getInstance().findFileByUrl(builder.toString());
          if (jarRoot != null) {
            container.add(jarRoot);
          }
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
      else {
        if (recursively && child.isDirectory()) {
          addChildren(child, container, recursively);
        }
      }
    }
  }

  public void setName(String name) {
    LOG.assertTrue(isWritable());
    myName = name;
  }

  public Library.ModifiableModel getModifiableModel() {
    return new LibraryImpl(this);
  }

  public Library cloneLibrary() {
    LOG.assertTrue(myLibraryTable == null);
    final LibraryImpl that = new LibraryImpl(this);
    that.mySource = null;
    that.updateWatchedRoots();
    return that;
  }

  public void setRootModel(ModifiableRootModel rootModel) {
    LOG.assertTrue(myLibraryTable == null);
    myRootModel = rootModel;
  }

  public boolean allPathsValid(OrderRootType type) {
    final List<VirtualFilePointer> pointers = myRoots.get(type).getList();
    for (VirtualFilePointer pointer : pointers) {
      if (!pointer.isValid()) {
        return false;
      }
    }
    return true;
  }

  public RootProvider getRootProvider() {
    return myRootProvider;
  }

  private static HashMap<OrderRootType, VirtualFilePointerContainer> initRoots() {
    final HashMap<OrderRootType, VirtualFilePointerContainer> result =
      new HashMap<OrderRootType, VirtualFilePointerContainer>(5);

    final VirtualFilePointerContainer classesRoots = VirtualFilePointerManager.getInstance().createContainer();
    result.put(OrderRootType.CLASSES, classesRoots);
    result.put(OrderRootType.COMPILATION_CLASSES, classesRoots);
    result.put(OrderRootType.CLASSES_AND_OUTPUT, classesRoots);
    result.put(OrderRootType.JAVADOC, VirtualFilePointerManager.getInstance().createContainer());
    result.put(OrderRootType.SOURCES, VirtualFilePointerManager.getInstance().createContainer());
    return result;
  }

  private static final OrderRootType[] SERIALIZABLE_ROOT_TYPES = {
    OrderRootType.CLASSES, OrderRootType.JAVADOC, OrderRootType.SOURCES
  };

  public void readExternal(Element element) throws InvalidDataException {
    myName = element.getAttributeValue(LIBRARY_NAME_ATTR);
    for (OrderRootType rootType : SERIALIZABLE_ROOT_TYPES) {
      VirtualFilePointerContainer roots = myRoots.get(rootType);
      final Element rootChild = element.getChild(rootType.name());
      if (rootChild == null) {
        continue;
      }
      roots.readExternal(rootChild, ROOT_PATH_ELEMENT);
    }
    myJarDirectories.clear();
    final List jarDirs = element.getChildren(JAR_DIRECTORY_ELEMENT);
    for (Object item : jarDirs) {
      final Element jarDir = (Element)item;
      final String url = jarDir.getAttributeValue(URL_ATTR);
      final String recursive = jarDir.getAttributeValue(RECURSIVE_ATTR);
      if (url != null) {
        myJarDirectories.put(url, Boolean.valueOf(Boolean.parseBoolean(recursive)));
      }
    }
    updateWatchedRoots();
  }


  public void writeExternal(Element rootElement) {
    Element element = new Element(ELEMENT);
    if (myName != null) {
      element.setAttribute(LIBRARY_NAME_ATTR, myName);
    }
    for (OrderRootType rootType : SERIALIZABLE_ROOT_TYPES) {
      final Element rootTypeElement = new Element(rootType.name());
      final VirtualFilePointerContainer roots = myRoots.get(rootType);
      roots.writeExternal(rootTypeElement, ROOT_PATH_ELEMENT);
      element.addContent(rootTypeElement);
    }
    List<String> urls = new ArrayList<String>(myJarDirectories.keySet());
    Collections.sort(urls, new Comparator<String>() {
      public int compare(final String url1, final String url2) {
        return url1.compareToIgnoreCase(url2);
      }
    });
    for (String url : urls) {
      final Element jarDirElement = new Element(JAR_DIRECTORY_ELEMENT);
      jarDirElement.setAttribute(URL_ATTR, url);
      jarDirElement.setAttribute(RECURSIVE_ATTR, myJarDirectories.get(url).toString());
      element.addContent(jarDirElement);
    }
    rootElement.addContent(element);
  }

  private boolean isWritable() {
    return mySource != null;
  }

  public void addRoot(String url, OrderRootType rootType) {
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(url);
  }

  public void addRoot(VirtualFile file, OrderRootType rootType) {
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(file);
  }

  public void addJarDirectory(final String url, final boolean recursive) {
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(OrderRootType.CLASSES);
    container.add(url);
    myJarDirectories.put(url, Boolean.valueOf(recursive));
  }

  public void addJarDirectory(final VirtualFile file, final boolean recursive) {
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(OrderRootType.CLASSES);
    container.add(file);
    myJarDirectories.put(file.getUrl(), Boolean.valueOf(recursive));
  }

  public boolean isJarDirectory(final String url) {
    return myJarDirectories.containsKey(url);
  }

  public boolean isValid(final String url, final OrderRootType rootType) {
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer fp = container.findByUrl(url);
    return fp != null && fp.isValid();
  }

  public boolean removeRoot(String url, OrderRootType rootType) {
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer byUrl = container.findByUrl(url);
    if (byUrl != null) {
      container.remove(byUrl);
      myJarDirectories.remove(url);
      return true;
    } 
    return false;
  }

  public void moveRootUp(String url, OrderRootType rootType) {
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.moveUp(url);
  }

  public void moveRootDown(String url, OrderRootType rootType) {
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.moveDown(url);
  }

  public boolean isChanged() {
    return !Comparing.equal(mySource.myName, myName) || areRootchChanged(mySource);
  }

  private boolean areRootchChanged(final LibraryImpl that) {
    final OrderRootType[] allTypes = OrderRootType.ALL_TYPES;
    for (OrderRootType type : allTypes) {
      final String[] urls = getUrls(type);
      final String[] thatUrls = that.getUrls(type);
      if (urls.length != thatUrls.length) {
        return true;
      }
      for (int idx = 0; idx < urls.length; idx++) {
        final String url = urls[idx];
        final String thatUrl = thatUrls[idx];
        if (!Comparing.equal(url, thatUrl)) {
          return true;
        }
        final Boolean jarDirRecursive = myJarDirectories.get(url);
        final Boolean sourceJarDirRecursive = that.myJarDirectories.get(thatUrl);
        if (jarDirRecursive == null? sourceJarDirRecursive != null : !jarDirRecursive.equals(sourceJarDirRecursive)) {
          return true;
        }
      }
    }
    return false;
  }

  public Library getSource() {
    return mySource;
  }

  public void commit() {
    LOG.assertTrue(mySource != null);
    mySource.commit(this);
    mySource = null;
  }

  private void commit(LibraryImpl model) {
    if (myLibraryTable != null) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    } 
    else if (myRootModel != null) {
      LOG.assertTrue(myRootModel.isWritable());
    }
    if (!Comparing.equal(model.myName, myName)) {
      myName = model.myName;
      if (myLibraryTable instanceof LibraryTableBase) {
        ((LibraryTableBase)myLibraryTable).fireLibraryRenamed(this);
      }
    }
    if (areRootchChanged(model)) {
      myRoots = model.myRoots;
      myJarDirectories = model.myJarDirectories;
      updateWatchedRoots();
      myRootProvider.fireRootSetChanged();
    }
  }

  private void updateWatchedRoots() {
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    if (myWatchRequests.size() > 0) {
      fs.removeWatchedRoots(myWatchRequests);
      myWatchRequests.clear();
    }
    final VirtualFileManager fm = VirtualFileManager.getInstance();
    for (String url : myJarDirectories.keySet()) {
      if (fm.getFileSystem(VirtualFileManager.extractProtocol(url)) instanceof LocalFileSystem) {
        final boolean watchRecursively = myJarDirectories.get(url).booleanValue();
        final LocalFileSystem.WatchRequest request = fs.addRootToWatch(VirtualFileManager.extractPath(url), watchRecursively);
        myWatchRequests.add(request);
      }
    }
  }

  private class MyRootProviderImpl extends RootProviderBaseImpl {
    public String[] getUrls(OrderRootType rootType) {
      final VirtualFile[] files = getFiles(rootType);
      if (files.length == 0) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      final String[] urls = new String[files.length];
      for (int i = 0; i < files.length; i++) {
        urls[i] = files[i].getUrl();
      }
      return urls;
    }

    public void fireRootSetChanged() {
      super.fireRootSetChanged();
    }
  }

  public LibraryTable getTable() {
    return myLibraryTable;
  }
}
