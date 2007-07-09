package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class LibraryTableBase implements PersistentStateComponent<Element>, LibraryTable, BaseComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.libraries.LibraryTableBase");
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private LibraryModel myModel = new LibraryModel();


  public LibraryTable.ModifiableModel getModifiableModel() {
    return new LibraryModel(myModel);
  }

  public Element getState() {
    final Element element = new Element("state");
    myModel.writeExternal(element);
    return element;
  }

  public void loadState(final Element object) {
    try {
      myModel.readExternal(object);
    }
    catch (InvalidDataException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public Library[] getLibraries() {
    return myModel.getLibraries();
  }

  @NotNull
  public Iterator<Library> getLibraryIterator() {
    return myModel.getLibraryIterator();
  }

  public Library getLibraryByName(@NotNull String name) {
    return myModel.getLibraryByName(name);
  }

  public void addListener(Listener listener) {
    myDispatcher.addListener(listener);
  }

  public void addListener(Listener listener, Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public void removeListener(Listener listener) {
    myDispatcher.removeListener(listener);
  }

  private void fireLibraryAdded (Library library) {
    myDispatcher.getMulticaster().afterLibraryAdded(library);
  }

  private void fireBeforeLibraryRemoved (Library library) {
    myDispatcher.getMulticaster().beforeLibraryRemoved(library);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "libraryTable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    for (Library library : getLibraries()) {
      Disposer.dispose(library);
    }
  }

  public Library createLibrary() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    return createLibrary(null);
  }

  public void fireLibraryRenamed(LibraryImpl library) {
    myDispatcher.getMulticaster().afterLibraryRenamed(library);
  }

  public Library createLibrary(String name) {
    final LibraryTable.ModifiableModel modifiableModel = getModifiableModel();
    final Library library = modifiableModel.createLibrary(name);
    modifiableModel.commit();
    return library;
  }

  public void removeLibrary(@NotNull Library library) {
    final LibraryTable.ModifiableModel modifiableModel = getModifiableModel();
    modifiableModel.removeLibrary(library);
    modifiableModel.commit();
  }

  private void commit(LibraryModel model) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    List<Library> addedLibraries = new ArrayList<Library>(model.myLibraries);
    addedLibraries.removeAll(myModel.myLibraries);
    List<Library> removedLibraries = new ArrayList<Library>(myModel.myLibraries);
    removedLibraries.removeAll(model.myLibraries);

    for (Library library : removedLibraries) {
      fireBeforeLibraryRemoved(library);
    }
    myModel = model;
    for (Library library : removedLibraries) {
      library.dispose();
      fireAfterLibraryRemoved(library);
    }
    for (Library library : addedLibraries) {
      fireLibraryAdded(library);
    }
  }

  private void fireAfterLibraryRemoved(Library library) {
    myDispatcher.getMulticaster().afterLibraryRemoved(library);
  }

  public void readExternal(final Element element) throws InvalidDataException {
    myModel = new LibraryModel();
    myModel.readExternal(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    myModel.writeExternal(element);
  }

  public class LibraryModel implements LibraryTable.ModifiableModel, JDOMExternalizable {
    private final ArrayList<Library> myLibraries = new ArrayList<Library>();
    private boolean myWritable;

    private LibraryModel() {
      myWritable = false;
    }

    private LibraryModel(LibraryModel that) {
      myWritable = true;
      myLibraries.addAll(that.myLibraries);
    }

    public void commit() {
      myWritable = false;
      LibraryTableBase.this.commit(this);
    }

    @NotNull
    public Iterator<Library> getLibraryIterator() {
      return Collections.unmodifiableList(myLibraries).iterator();
    }

    public Library getLibraryByName(@NotNull String name) {
      for (Library myLibrary : myLibraries) {
        LibraryImpl library = (LibraryImpl)myLibrary;
        if (Comparing.equal(name, library.getName())) return library;
      }
      @NonNls final String libraryPrefix = "library.";
      final String libPath = System.getProperty(libraryPrefix + name);
      if (libPath != null) {
        final LibraryImpl library = new LibraryImpl(name, LibraryTableBase.this);
        library.addRoot(libPath, OrderRootType.CLASSES);
        return library;
      }
      return null;
    }


    @NotNull
    public Library[] getLibraries() {
      return myLibraries.toArray(new Library[myLibraries.size()]);
    }

    private void assertWritable() {
      LOG.assertTrue(myWritable);
    }

    public Library createLibrary(String name) {
      assertWritable();
      final LibraryImpl library = new LibraryImpl(name, LibraryTableBase.this);
      myLibraries.add(library);
      return library;
    }

    public void removeLibrary(@NotNull Library library) {
      assertWritable();
      myLibraries.remove(library);
    }

    public boolean isChanged() {
      if (!myWritable) return false;
      Set<Library> thisLibraries = new HashSet<Library>(myLibraries);
      Set<Library> thatLibraries = new HashSet<Library>(myModel.myLibraries);
      return !thisLibraries.equals(thatLibraries);
    }

    public void readExternal(Element element) throws InvalidDataException {
      HashMap<String, Library> libraries = new HashMap<String, Library>();
      for (Library library : myLibraries) {
        libraries.put(library.getName(), library);
      }

      final List libraryElements = element.getChildren(LibraryImpl.ELEMENT);
      for (Object libraryElement1 : libraryElements) {
        Element libraryElement = (Element)libraryElement1;
        final LibraryImpl library = new LibraryImpl(LibraryTableBase.this);
        library.readExternal(libraryElement);
        if (library.getName() != null) {
          Library oldLibrary = libraries.get(library.getName());
          if (oldLibrary != null) {
            myLibraries.remove(oldLibrary);
          }
          myLibraries.add(library);
          fireLibraryAdded(library);
        }
      }
    }

    public void writeExternal(Element element) {
      for (Library library : myLibraries) {
        if (library.getName() != null) {
          library.writeExternal(element);
        }
      }
    }
  }
}
