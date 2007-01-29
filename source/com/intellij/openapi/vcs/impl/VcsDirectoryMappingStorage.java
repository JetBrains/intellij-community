/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jdom.Element;

/**
 * @author yole
 */
public class VcsDirectoryMappingStorage implements ProjectComponent, JDOMExternalizable {
  private ProjectLevelVcsManager myVcsManager;

  public VcsDirectoryMappingStorage(final ProjectLevelVcsManager vcsManager) {
    myVcsManager = vcsManager;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VcsDirectoryMappings";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    ((ProjectLevelVcsManagerImpl) myVcsManager).readDirectoryMappings(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    ((ProjectLevelVcsManagerImpl) myVcsManager).writeDirectoryMappings(element);
  }
}