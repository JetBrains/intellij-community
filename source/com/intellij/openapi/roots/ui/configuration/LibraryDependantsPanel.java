/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 14-Aug-2006
 * Time: 15:50:48
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.OrderPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Dead code now
 */
public class LibraryDependantsPanel extends OrderPanel<ModifiableRootModel> {
  private Library myLibrary;

  public LibraryDependantsPanel(final Library library) {
    super(ModifiableRootModel.class, true);
    myLibrary = library;
    getEntryTable().setDefaultRenderer(ModifiableRootModel.class, new DefaultTableCellRenderer(){
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        final Component cellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof ModifiableRootModel){
          final ModifiableRootModel model = (ModifiableRootModel)value;
          final Module module = model.getModule();
          setText(module.getName());
          setIcon(module.getModuleType().getNodeIcon(false));
        }
        return cellRendererComponent; 
      }
    });
    getEntryTable().setDefaultRenderer(Boolean.class, new BooleanTableCellRenderer());

    setBorder(BorderFactory.createTitledBorder("Used In"));
  }


  public boolean isCheckable(final ModifiableRootModel entry) {
    return true;
  }

  public boolean isChecked(final ModifiableRootModel model) {
    final OrderEntry[] entries = model.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
        if (Comparing.equal(libraryOrderEntry.getLibrary(), myLibrary)) {
          return true;
        }
      }
    }
    return false;
  }

  public void setChecked(final ModifiableRootModel model, final boolean checked) {
    if (checked) {
      model.addLibraryEntry(myLibrary);
    }
    else {
      final OrderEntry[] entries = model.getOrderEntries();
      for (OrderEntry entry : entries) {
        if (entry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
          if (Comparing.equal(libraryOrderEntry.getLibrary(), myLibrary)) {
            model.removeOrderEntry(libraryOrderEntry);
            break;
          }
        }
      }
    }
  }
}