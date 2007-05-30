package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ConvertingIterator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;

/**
 *  @author dsl
 */
public class ModuleLibraryTable implements LibraryTable, LibraryTable.ModifiableModel {
  private RootModelImpl myRootModel;
  private static final ModuleLibraryOrderEntryCondition MODULE_LIBRARY_ORDER_ENTRY_FILTER = new ModuleLibraryOrderEntryCondition();
  private static final OrderEntryToLibraryConvertor ORDER_ENTRY_TO_LIBRARY_CONVERTOR = new OrderEntryToLibraryConvertor();
  private ProjectRootManagerImpl myProjectRootManager;
  private VirtualFilePointerManager myFilePointerManager;
  public static final LibraryTablePresentation MODULE_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    public String getDisplayName(boolean plural) {
      return ProjectBundle.message("module.library.display.name", plural ? 2 : 1);
    }

    public String getDescription() {
      return ProjectBundle.message("libraries.node.text.module");
    }

    public String getLibraryTableEditorTitle() {
      return ProjectBundle.message("library.configure.module.title");
    }
  };

  ModuleLibraryTable(RootModelImpl rootModel, ProjectRootManagerImpl projectRootManager, VirtualFilePointerManager filePointerManager) {
    myRootModel = rootModel;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;
  }

  @NotNull
  public Library[] getLibraries() {
    final ArrayList<Library> result = new ArrayList<Library>();
    final Iterator<Library> libraryIterator = getLibraryIterator();
    ContainerUtil.addAll(result, libraryIterator);
    return result.toArray(new Library[result.size()]);
  }

  public Library createLibrary() {
    final ModuleLibraryOrderEntryImpl orderEntry = new ModuleLibraryOrderEntryImpl(myRootModel, myProjectRootManager, myFilePointerManager);
    myRootModel.addOrderEntry(orderEntry);
    return orderEntry.getLibrary();
  }

  public Library createLibrary(String name) {
    final ModuleLibraryOrderEntryImpl orderEntry = new ModuleLibraryOrderEntryImpl(name, myRootModel, myProjectRootManager, myFilePointerManager);
    myRootModel.addOrderEntry(orderEntry);
    return orderEntry.getLibrary();
  }

  public void removeLibrary(@NotNull Library library) {
    final Iterator<OrderEntry> orderIterator = myRootModel.getOrderIterator();
    while (orderIterator.hasNext()) {
      OrderEntry orderEntry = orderIterator.next();
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry) orderEntry;
        if (libraryOrderEntry.isModuleLevel()) {
          if (library.equals(libraryOrderEntry.getLibrary())) {
            myRootModel.removeOrderEntry(orderEntry);
            library.dispose();
            return;
          }
        }
      }
    }
  }

  @NotNull
  public Iterator<Library> getLibraryIterator() {
    FilteringIterator<OrderEntry, LibraryOrderEntry> filteringIterator =
      new FilteringIterator<OrderEntry, LibraryOrderEntry>(myRootModel.getOrderIterator(), MODULE_LIBRARY_ORDER_ENTRY_FILTER);
    return new ConvertingIterator<LibraryOrderEntry, Library>(filteringIterator, ORDER_ENTRY_TO_LIBRARY_CONVERTOR);
  }

  public String getTableLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  public LibraryTablePresentation getPresentation() {
    return MODULE_LIBRARY_TABLE_PRESENTATION;
  }

  public boolean isEditable() {
    return true;
  }

  @Nullable
  public Library getLibraryByName(@NotNull String name) {
    final Iterator<Library> libraryIterator = getLibraryIterator();
    while (libraryIterator.hasNext()) {
      Library library = libraryIterator.next();
      if (name.equals(library.getName())) return library;
    }
    return null;
  }

  public void addListener(Listener listener) {
    throw new UnsupportedOperationException();
  }

  public void addListener(Listener listener, Disposable parentDisposable) {
    throw new UnsupportedOperationException("Method addListener is not yet implemented in " + getClass().getName());
  }

  public void removeListener(Listener listener) {
    throw new UnsupportedOperationException();
  }



  private static class ModuleLibraryOrderEntryCondition implements Condition<OrderEntry> {
    public boolean value(OrderEntry entry) {
      return entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).isModuleLevel() && ((LibraryOrderEntry)entry).getLibrary() != null;
    }
  }

  private static class OrderEntryToLibraryConvertor implements Convertor<LibraryOrderEntry, Library> {
    public Library convert(LibraryOrderEntry o) {
      return o.getLibrary();
    }
  }

  public void commit() {
  }

  public boolean isChanged() {
    return myRootModel.isChanged();
  }

  public LibraryTable.ModifiableModel getModifiableModel() {
    return this;
  }
}
