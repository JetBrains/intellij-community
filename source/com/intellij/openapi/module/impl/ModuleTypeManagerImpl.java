/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleTypeManagerImpl extends ModuleTypeManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleTypeManagerImpl");

  private List<ModuleType> myModuleTypes = new ArrayList<ModuleType>();

  public ModuleTypeManagerImpl() {
    registerDefaultTypes();
  }

  public void registerModuleType(ModuleType type) {
    for (int i = 0; i < myModuleTypes.size(); i++) {
      ModuleType oldType = myModuleTypes.get(i);
      if (oldType.getId().equals(type.getId())) {
        LOG.error("Trying to register a module type that claunches with existing one. Old=" + oldType + ", new = " + type);
        return;
      }
    }
    myModuleTypes.add(type);
  }

  public ModuleType[] getRegisteredTypes() {
    return myModuleTypes.toArray(new ModuleType[myModuleTypes.size()]);
  }

  public ModuleType findByID(String moduleTypeID) {
    for (int i = 0; i < myModuleTypes.size(); i++) {
      ModuleType type = myModuleTypes.get(i);
      if (type.getId().equals(moduleTypeID)) return type;
    }

    return new UnknownModuleType(moduleTypeID);
  }

  public String getComponentName() {
    return "ModuleTypeManager";
  }

  public void initComponent() {
  }

  private void registerDefaultTypes() {
    ModuleType.JAVA = new JavaModuleType();
    ModuleType.WEB = new WebModuleType();
    ModuleType.EJB = new EjbModuleType();
    ModuleType.J2EE_APPLICATION = new J2EEApplicationModuleType();

    registerModuleType(ModuleType.JAVA);
    registerModuleType(ModuleType.WEB);
    registerModuleType(ModuleType.EJB);
    registerModuleType(ModuleType.J2EE_APPLICATION);
  }

  public void disposeComponent() {
  }
}