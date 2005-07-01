package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.CollectUtil;
import com.intellij.util.containers.Convertor;
import org.jdom.Element;

import java.util.*;

/**
 *  @author dsl
 */
public abstract class LibraryTableBase implements JDOMExternalizable, LibraryTable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.libraries.LibraryTableBase");
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private LibraryModel myModel = new LibraryModel();


  public LibraryTable.ModifiableModel getModifiableModel() {
    return new LibraryModel(myModel);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myModel.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myModel.writeExternal(element);
  }

  public Library[] getLibraries() {
    return myModel.getLibraries();
  }

  public Iterator getLibraryIterator() {
    return myModel.getLibraryIterator();
  }

  public Library getLibraryByName(String name) {
    return myModel.getLibraryByName(name);
  }

  public void addListener(Listener listener) {
    myDispatcher.addListener(listener);
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


  public Library createLibrary() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    return createLibrary(null);
  }


  public String getComponentName() {
    return "libraryTable";
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

  public void removeLibrary(Library library) {
    final LibraryTable.ModifiableModel modifiableModel = getModifiableModel();
    modifiableModel.removeLibrary(library);
    modifiableModel.commit();
  }

  public void commit(LibraryModel model) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    List<LibraryImpl> addedLibraries = new ArrayList<LibraryImpl>(model.myLibraries);
    addedLibraries.removeAll(myModel.myLibraries);
    List<LibraryImpl> removedLibraries = new ArrayList<LibraryImpl>(myModel.myLibraries);
    removedLibraries.removeAll(model.myLibraries);

    for (LibraryImpl library : removedLibraries) {
      fireBeforeLibraryRemoved(library);
    }
    myModel = model;
    for (LibraryImpl library : removedLibraries) {
      fireAfterLibraryRemoved(library);
    }
    for (LibraryImpl library : addedLibraries) {
      fireLibraryAdded(library);
    }
  }

  private void fireAfterLibraryRemoved(LibraryImpl library) {
    myDispatcher.getMulticaster().afterLibraryRemoved(library);
  }

  private class LibraryModel implements LibraryTable.ModifiableModel {
    private final ArrayList<LibraryImpl> myLibraries = new ArrayList<LibraryImpl>();
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

    public Iterator getLibraryIterator() {
      return Collections.unmodifiableList(myLibraries).iterator();
    }

    public Library getLibraryByName(String name) {
      for (LibraryImpl myLibrary : myLibraries) {
        LibraryImpl library = (LibraryImpl)myLibrary;
        if (Comparing.equal(name, library.getName())) return library;
      }
      return null;
    }


    public Library[] getLibraries() {
      return (Library[]) myLibraries.toArray(new Library[myLibraries.size()]);
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

    public void removeLibrary(Library library) {
      assertWritable();
      myLibraries.remove(library);
    }

    public boolean isChanged() {
      if (!myWritable) return false;
      Set<LibraryImpl> thisLibraries = new com.intellij.util.containers.HashSet<LibraryImpl>(myLibraries);
      Set<LibraryImpl> thatLibraries = new com.intellij.util.containers.HashSet<LibraryImpl>(myModel.myLibraries);
      return !thisLibraries.equals(thatLibraries);
    }

    public void readExternal(Element element) throws InvalidDataException {
      HashMap<String, LibraryImpl> libraries = new HashMap<String, LibraryImpl>();
      for (LibraryImpl library : myLibraries) {
        libraries.put(library.getName(), library);
      }

      final List libraryElements = element.getChildren(LibraryImpl.ELEMENT);
      for (Object libraryElement1 : libraryElements) {
        Element libraryElement = (Element)libraryElement1;
        final LibraryImpl library = new LibraryImpl(LibraryTableBase.this);
        library.readExternal(libraryElement);
        if (library.getName() != null) {
          LibraryImpl oldLibrary = libraries.get(library.getName());
          if (oldLibrary != null) {
            myLibraries.remove(oldLibrary);
          }
          myLibraries.add(library);
          fireLibraryAdded(library);
        }
      }
    }

    public void writeExternal(Element element) throws WriteExternalException {
      for (LibraryImpl myLibrary : myLibraries) {
        LibraryImpl library = (LibraryImpl)myLibrary;
        if (library.getName() != null) {
          library.writeExternal(element);
        }
      }
    }
  }
}
