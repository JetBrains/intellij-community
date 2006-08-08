package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *  @author dsl
 */
public class LibraryImpl implements Library.ModifiableModel, LibraryEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.impl.LibraryImpl");
  @NonNls private static final String LIBRARY_NAME_ATTR = "name";
  @NonNls private static final String ROOT_PATH_ELEMENT = "root";
  private String myName;
  private final LibraryTable myLibraryTable;
  private HashMap<OrderRootType, VirtualFilePointerContainer> myRoots;
  private LibraryImpl mySource;

  private final MyRootProviderImpl myRootProvider = new MyRootProviderImpl();
  @NonNls public static final String ELEMENT = "library";
  private ModifiableRootModel myRootModel;

  LibraryImpl(String name, LibraryTable table) {
    myName = name;
    myLibraryTable = table;
    myRoots = initRoots();
    mySource = null;
  }

  LibraryImpl(LibraryTable table) {
    myLibraryTable = table;
    myRoots = initRoots();
    mySource = null;
  }


  LibraryImpl() {
    myLibraryTable = null;
    myRoots = initRoots();
    mySource = null;
  }

  LibraryImpl(LibraryImpl that) {
    myName = that.myName;
    myRoots = initRoots();
    mySource = that;
    myLibraryTable = that.myLibraryTable;
    for (OrderRootType rootType : SERIALIZABLE_ROOT_TYPES) {
      final VirtualFilePointerContainer thisContainer = myRoots.get(rootType);
      final VirtualFilePointerContainer thatContainer = that.myRoots.get(rootType);
      thisContainer.addAll(thatContainer);
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
    final VirtualFilePointerContainer result = myRoots.get(rootType);
    return result.getFiles();
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
    return that;
  }

  public void setRootModel(ModifiableRootModel rootModel) {
    LOG.assertTrue(myLibraryTable == null);
    myRootModel = rootModel;
  }

  public boolean allPathsValid(OrderRootType type) {
    final VirtualFile[] files = getFiles(type);
    final Set<String> validUrls;
    if (files.length > 0) {
      validUrls = new HashSet<String>();
      for (VirtualFile file : files) {
        validUrls.add(file.getUrl());
      }
    }
    else {
      validUrls = Collections.emptySet();
    }

    final String[] urls = getUrls(type);
    for (String url : urls) {
      if (!validUrls.contains(url)) {
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
      if (rootChild == null) continue;
      roots.readExternal(rootChild, ROOT_PATH_ELEMENT);
    }
  }


  public void writeExternal(Element rootElement) throws WriteExternalException {
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

  public boolean removeRoot(String url, OrderRootType rootType) {
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer byUrl = container.findByUrl(url);
    if (byUrl != null) {
      container.remove(byUrl);
      return true;
    } else {
      return false;
    }
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
    final boolean sameName = Comparing.equal(mySource.myName, myName);
    if (!sameName) return true;
    final OrderRootType[] allTypes = OrderRootType.ALL_TYPES;
    for (OrderRootType type : allTypes) {
      final String[] orderedRootUrls1 = getUrls(type);
      final String[] orderedRootUrls2 = mySource.getUrls(type);
      if (!Arrays.equals(orderedRootUrls1, orderedRootUrls2)) {
        return true;
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
    } else if (myRootModel != null) {
      LOG.assertTrue(myRootModel.isWritable());
    }
    final boolean sameName = Comparing.equal(model.myName, myName);
    boolean sameRoots = true;
    for (final OrderRootType rootType : SERIALIZABLE_ROOT_TYPES) {
      final VirtualFilePointerContainer container = myRoots.get(rootType);
      final VirtualFilePointerContainer thatContainer = model.myRoots.get(rootType);
      sameRoots = Arrays.equals(container.getUrls(), thatContainer.getUrls());
      if (!sameRoots) break;
    }
    final boolean isChanged = sameName && sameRoots;
    if (isChanged) return;
    if (!sameName) {
      myName = model.myName;
      if (myLibraryTable instanceof LibraryTableBase) {
        ((LibraryTableBase)myLibraryTable).fireLibraryRenamed(this);
      }
    }
    if (!sameRoots) {
      myRoots = model.myRoots;
      myRootProvider.fireRootSetChanged();
    }
  }

  private class MyRootProviderImpl extends RootProviderBaseImpl {
    public String[] getUrls(OrderRootType rootType) {
      return LibraryImpl.this.getUrls(rootType);
    }

    public void fireRootSetChanged() {
      super.fireRootSetChanged();
    }
  }

  public LibraryTable getTable() {
    return myLibraryTable;
  }
}
