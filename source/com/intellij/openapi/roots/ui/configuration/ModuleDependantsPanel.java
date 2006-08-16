/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 14-Aug-2006
 * Time: 15:31:35
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.ui.OrderPanel;

/**
 * Dead code now
 */
public class ModuleDependantsPanel extends OrderPanel<ModifiableRootModel> {
  private Module myModule;

  public ModuleDependantsPanel(final Module module) {
    super(ModifiableRootModel.class, true);
    myModule = module;
  }


  public boolean isCheckable(final ModifiableRootModel entry) {
    return true;
  }

  public boolean isChecked(final ModifiableRootModel model) {
    final OrderEntry[] entries = model.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof ModuleOrderEntry) {
        final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        if (moduleOrderEntry.getModule() == myModule) {
          return true;
        }
      }
    }
    return false;
  }

  public void setChecked(final ModifiableRootModel model, final boolean checked) {
    if (checked) {
      model.addModuleOrderEntry(myModule);
    }
    else {
      final OrderEntry[] entries = model.getOrderEntries();
      for (OrderEntry entry : entries) {
        if (entry instanceof ModuleOrderEntry) {
          final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
          if (moduleOrderEntry.getModule() == myModule) {
            model.removeOrderEntry(moduleOrderEntry);
            break;
          }
        }
      }
    }
  }
}