/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;


public class UnknownModuleType extends JavaModuleType {

  public UnknownModuleType(String id) {
    super(id);
  }

  public String getName() {
    return "Unknown module type. Used \"" + super.getName() + "\" as a substitute";
  }
}