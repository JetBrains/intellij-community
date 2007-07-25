/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FrameworkSupportConfigurable {

  @Nullable
  public abstract JComponent getComponent();

  public abstract void addSupport(Module module, ModifiableRootModel model);

}
