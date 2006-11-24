/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2006
 * Time: 19:59:36
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.*;
import java.util.*;

public class ShelveChangesManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager");

  @NonNls private static final String PATCH_EXTENSION = ".patch";

  public static ShelveChangesManager getInstance(Project project) {
    return project.getComponent(ShelveChangesManager.class);
  }

  private Project myProject;
  private MessageBus myBus;
  private List<ShelvedChangeListData> myShelvedChangeListDatas = new ArrayList<ShelvedChangeListData>();
  @NonNls private static final String ELEMENT_CHANGELIST = "changelist";
  @NonNls private static final String ATTRIBUTE_DATE = "date";
  @NonNls private String myShelfPath;

  public static final Topic<ChangeListener> SHELF_TOPIC = new Topic<ChangeListener>("shelf updates", ChangeListener.class);

  public ShelveChangesManager(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public void projectOpened() {
    myShelfPath = PathManager.getConfigPath() + File.separator + "shelf";
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ShelveChangesManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    //noinspection unchecked
    final List<Element> children = (List<Element>)element.getChildren(ELEMENT_CHANGELIST);
    for(Element child: children) {
      ShelvedChangeListData data = new ShelvedChangeListData();
      data.readExternal(child);
      myShelvedChangeListDatas.add(data);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for(ShelvedChangeListData data: myShelvedChangeListDatas) {
      Element child = new Element(ELEMENT_CHANGELIST);
      data.writeExternal(child);
      element.addContent(child);
    }
  }

  public List<ShelvedChangeListData> getShelvedChangeLists() {
    return Collections.unmodifiableList(myShelvedChangeListDatas);
  }

  public void shelveChanges(final Collection<Change> changes, final String commitMessage) throws IOException {
    File patchPath = getPatchPath(commitMessage);
    Writer writer = new OutputStreamWriter(new FileOutputStream(patchPath));
    try {
      List<FilePatch> patches = PatchBuilder.buildPatch(changes, myProject.getProjectFile().getParent().getPresentableUrl());
      UnifiedDiffWriter.write(patches, writer);
    }
    finally {
      writer.close();
    }

    RollbackChangesDialog.doRollback(myProject, changes, true, false);

    myShelvedChangeListDatas.add(new ShelvedChangeListData(patchPath.toString(), commitMessage));
    notifyStateChanged();
  }

  private void notifyStateChanged() {
    myBus.syncPublisher(SHELF_TOPIC).stateChanged(new ChangeEvent(this));
  }

  private File getPatchPath(final String commitMessage) {
    File file = new File(myShelfPath);
    if (!file.exists()) {
      file.mkdirs();
    }

    @NonNls String defaultPath = commitMessage.replace(' ', ' ').replace('.', '_').replace(File.separatorChar, '_');
    if (defaultPath.length() == 0) {
      defaultPath = "unnamed";
    }
    String path = defaultPath + PATCH_EXTENSION;
    int index = 0;
    while(new File(file, path).exists()) {
      index++;
      path = defaultPath + index + PATCH_EXTENSION;
    }
    return new File(file, path);
  }

  public void unshelveChangeList(final ShelvedChangeListData change) {
    try {
      List<FilePatch> patches = loadPatches(change.PATH);
      VirtualFile baseDir = myProject.getProjectFile().getParent();
      if (!ApplyPatchAction.applyPatch(myProject, patches, baseDir, 0)) {
        return;
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    catch (PatchSyntaxException e) {
      LOG.error(e);
      return;
    }
    FileUtil.delete(new File(change.PATH));
    myShelvedChangeListDatas.remove(change);
    notifyStateChanged();    
  }

  public static List<FilePatch> loadPatches(final String patchPath) throws IOException, PatchSyntaxException {
    char[] text = FileUtil.loadFileText(new File(patchPath));
    PatchReader reader = new PatchReader(new CharArrayCharSequence(text));
    return reader.readAllPatches();
  }

  public static class ShelvedChangeListData implements JDOMExternalizable {
    public String PATH;
    public String DESCRIPTION;
    public Date DATE;
    private List<ShelvedChange> myChanges;

    public ShelvedChangeListData() {
    }

    public ShelvedChangeListData(final String path, final String description) {
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
          final List<FilePatch> list = loadPatches(PATH);
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
}