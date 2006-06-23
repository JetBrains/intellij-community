package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ConvertingIterator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.FilteringIterator;
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

  ModuleLibraryTable(RootModelImpl rootModel, ProjectRootManagerImpl projectRootManager, VirtualFilePointerManager filePointerManager) {
    myRootModel = rootModel;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;
  }

  public Library[] getLibraries() {
    final ArrayList result = new ArrayList();
    final Iterator libraryIterator = getLibraryIterator();
    ContainerUtil.addAll(result, libraryIterator);
    return (Library[]) result.toArray(new Library[result.size()]);
  }

  public Library createLibrary() {
    final ModuleLibraryOrderEntryImpl orderEntry = new ModuleLibraryOrderEntryImpl(myRootModel, myProjectRootManager, myFilePointerManager);
    myRootModel.addOrderEntry(orderEntry);
    return orderEntry.getLibrary();
  }

  public Library createLibrary(String name) {
    final ModuleLibraryOrderEntryImpl orderEntry = new ModuleLibraryOrderEntryImpl(name, myRootModel, myProjectRootManager, VirtualFilePointerManager.getInstance());
    myRootModel.addOrderEntry(orderEntry);
    return orderEntry.getLibrary();
  }

  public void removeLibrary(Library library) {
    final Iterator orderIterator = myRootModel.getOrderIterator();
    while (orderIterator.hasNext()) {
      OrderEntry orderEntry = (OrderEntry) orderIterator.next();
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry) orderEntry;
        if (libraryOrderEntry.isModuleLevel()) {
          if (library.equals(libraryOrderEntry.getLibrary())) {
            myRootModel.removeOrderEntry(orderEntry);
            return;
          }
        }
      }
    }
  }

  public Iterator getLibraryIterator() {
    return new ConvertingIterator(
            new FilteringIterator(myRootModel.getOrderIterator(), MODULE_LIBRARY_ORDER_ENTRY_FILTER),
            ORDER_ENTRY_TO_LIBRARY_CONVERTOR);
  }

  public String getTableLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  @Nullable
  public Library getLibraryByName(String name) {
    final Iterator libraryIterator = getLibraryIterator();
    while (libraryIterator.hasNext()) {
      Library library = (Library) libraryIterator.next();
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
      return (entry instanceof LibraryOrderEntry) && ((LibraryOrderEntry)entry).isModuleLevel() && ((LibraryOrderEntry)entry).getLibrary() != null;
    }
  }

  private static class OrderEntryToLibraryConvertor implements Convertor {
    public Object convert(Object o) {
      return ((LibraryOrderEntry) o).getLibrary();
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
