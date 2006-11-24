/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.11.2006
 * Time: 20:20:04
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.FileStatus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ShelvedChangeList implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList");

  @NonNls private static final String ATTRIBUTE_DATE = "date";

  public String PATH;
  public String DESCRIPTION;
  public Date DATE;
  private List<ShelvedChange> myChanges;

  public ShelvedChangeList() {
  }

  public ShelvedChangeList(final String path, final String description) {
    PATH = path;
    DESCRIPTION = description;
    DATE = new Date();
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    DATE = new Date(Long.parseLong(element.getAttributeValue(ATTRIBUTE_DATE)));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    element.setAttribute(ATTRIBUTE_DATE, Long.toString(DATE.getTime()));
  }

  @Override
  public String toString() {
    return DESCRIPTION;
  }

  public List<ShelvedChange> getChanges() {
    if (myChanges == null) {
      try {
        final List<FilePatch> list = ShelveChangesManager.loadPatches(PATH);
        myChanges = new ArrayList<ShelvedChange>();
        for(FilePatch patch: list) {
          FileStatus status;
          if (patch.isNewFile()) {
            status = FileStatus.ADDED;
          }
          else if (patch.isDeletedFile()) {
            status = FileStatus.DELETED;
          }
          else {
            status = FileStatus.MODIFIED;
          }
          myChanges.add(new ShelvedChange(PATH, patch.getBeforeName(), status));
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return myChanges;
  }
}