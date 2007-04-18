/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@State(
  name = "VcsDirectoryMappings",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )
    ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class VcsDirectoryMappingStorage implements ProjectComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsDirectoryMappingStorage");

  private ProjectLevelVcsManager myVcsManager;

  public VcsDirectoryMappingStorage(final ProjectLevelVcsManager vcsManager) {
    myVcsManager = vcsManager;
  }

  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
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