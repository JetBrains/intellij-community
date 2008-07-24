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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.CustomBinaryPatchApplier;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.*;
import java.util.*;

public class ShelveChangesManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager");

  @NonNls private static final String PATCH_EXTENSION = "patch";

  public static ShelveChangesManager getInstance(Project project) {
    return project.getComponent(ShelveChangesManager.class);
  }

  private final Project myProject;
  private final MessageBus myBus;
  private final List<ShelvedChangeList> myShelvedChangeLists = new ArrayList<ShelvedChangeList>();
  @NonNls private static final String ELEMENT_CHANGELIST = "changelist";
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
      ShelvedChangeList data = new ShelvedChangeList();
      data.readExternal(child);
      if (new File(data.PATH).exists()) {
        myShelvedChangeLists.add(data);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for(ShelvedChangeList data: myShelvedChangeLists) {
      Element child = new Element(ELEMENT_CHANGELIST);
      data.writeExternal(child);
      element.addContent(child);
    }
  }

  public List<ShelvedChangeList> getShelvedChangeLists() {
    return Collections.unmodifiableList(myShelvedChangeLists);
  }

  public ShelvedChangeList shelveChanges(final Collection<Change> changes, final String commitMessage) throws IOException, VcsException {
    final List<Change> textChanges = new ArrayList<Change>();
    final List<ShelvedBinaryFile> binaryFiles = new ArrayList<ShelvedBinaryFile>();
    for(Change change: changes) {
      if (ChangesUtil.getFilePath(change).isDirectory()) {
        continue;
      }
      if (change.getBeforeRevision() instanceof BinaryContentRevision || change.getAfterRevision() instanceof BinaryContentRevision) {
        binaryFiles.add(shelveBinaryFile(change));
      }
      else {
        textChanges.add(change);
      }
    }

    File patchPath = getPatchPath(commitMessage);
    Writer writer;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(patchPath));
    }
    catch(IOException ex) {
      patchPath = getPatchPath("shelved_change");
      writer = new OutputStreamWriter(new FileOutputStream(patchPath));
    }
    try {
      List<FilePatch> patches = PatchBuilder.buildPatch(textChanges, myProject.getBaseDir().getPresentableUrl(), true, false);
      UnifiedDiffWriter.write(patches, writer, "\n");
    }
    finally {
      writer.close();
    }

    RollbackChangesDialog.doRollback(myProject, changes, true, false);

    final ShelvedChangeList changeList = new ShelvedChangeList(patchPath.toString(), commitMessage.replace('\n', ' '), binaryFiles);
    myShelvedChangeLists.add(changeList);
    notifyStateChanged();
    return changeList;
  }

  private ShelvedBinaryFile shelveBinaryFile(final Change change) throws IOException {
    final ContentRevision beforeRevision = change.getBeforeRevision();
    final ContentRevision afterRevision = change.getAfterRevision();
    File beforeFile = beforeRevision == null ? null : beforeRevision.getFile().getIOFile();
    File afterFile = afterRevision == null ? null : afterRevision.getFile().getIOFile();
    String shelvedPath = null;
    if (afterFile != null) {
      String shelvedName = FileUtil.getNameWithoutExtension(afterFile.getName());
      String shelvedExt = FileUtil.getExtension(afterFile.getName());
      File shelvedFile = FileUtil.findSequentNonexistentFile(new File(myShelfPath), shelvedName, shelvedExt);

      FileUtil.copy(afterRevision.getFile().getIOFile(), shelvedFile);
      shelvedPath = shelvedFile.getPath();
    }
    String beforePath = ChangesUtil.getProjectRelativePath(myProject, beforeFile);
    String afterPath = ChangesUtil.getProjectRelativePath(myProject, afterFile);
    return new ShelvedBinaryFile(beforePath, afterPath, shelvedPath);
  }

  private void notifyStateChanged() {
    myBus.syncPublisher(SHELF_TOPIC).stateChanged(new ChangeEvent(this));
  }

  private File getPatchPath(@NonNls final String commitMessage) {
    File file = new File(myShelfPath);
    if (!file.exists()) {
      file.mkdirs();
    }

    return suggestPatchName(commitMessage, file);
  }

  public static File suggestPatchName(final String commitMessage, final File file) {
    @NonNls String defaultPath = commitMessage.replace(' ', '_')
                                              .replace('.', '_')
                                              .replace(File.separatorChar, '_')
                                              .replace('\t', '_')
                                              .replace('\n', '_')
                                              .replace(':', '_')
                                              .replace('*', '_')
                                              .replace('?', '_')
                                              .replace('<', '_')
                                              .replace('>', '_')
                                              .replace('/', '_')
                                              .replace('"', '_');
    if (defaultPath.length() == 0) {
      defaultPath = "unnamed";
    }
    return FileUtil.findSequentNonexistentFile(file, defaultPath, PATCH_EXTENSION);
  }

  @Nullable
  public void unshelveChangeList(final ShelvedChangeList changeList, @Nullable final List<ShelvedChange> changes,
                                           @Nullable final List<ShelvedBinaryFile> binaryFiles, final LocalChangeList targetChangeList) {
    List<FilePatch> remainingPatches = new ArrayList<FilePatch>();

    final List<TextFilePatch> textFilePatches;
    try {
      textFilePatches = loadTextPatches(changeList, changes, remainingPatches);
    }
    catch (IOException e) {
      LOG.info(e);
      PatchApplier.showError(myProject, "Cannot load patch(es): " + e.getMessage(), true);
      return;
    }
    catch (PatchSyntaxException e) {
      PatchApplier.showError(myProject, "Cannot load patch(es): " + e.getMessage(), true);
      LOG.info(e);
      return;
    }

    final List<FilePatch> patches = new ArrayList<FilePatch>(textFilePatches);

    final List<ShelvedBinaryFile> remainingBinaries = new ArrayList<ShelvedBinaryFile>();
    final List<ShelvedBinaryFile> binaryFilesToUnshelve = getBinaryFilesToUnshelve(changeList, binaryFiles, remainingBinaries);

    for (final ShelvedBinaryFile shelvedBinaryFile : binaryFilesToUnshelve) {
      patches.add(new ShelvedBinaryFilePatch(shelvedBinaryFile));
    }

    final BinaryPatchApplier binaryPatchApplier = new BinaryPatchApplier(binaryFilesToUnshelve.size());
    final PatchApplier patchApplier = new PatchApplier(myProject, myProject.getBaseDir(), patches, targetChangeList, binaryPatchApplier);
    patchApplier.execute();
    remainingPatches.addAll(patchApplier.getRemainingPatches());

    if ((remainingPatches.size() == 0) && remainingBinaries.isEmpty()) {
      deleteChangeList(changeList);
    }
    else {
      saveRemainingPatches(changeList, remainingPatches);
    }
  }

  private List<TextFilePatch> loadTextPatches(final ShelvedChangeList changeList, final List<ShelvedChange> changes, final List<FilePatch> remainingPatches)
      throws IOException, PatchSyntaxException {
    final List<TextFilePatch> textFilePatches;
    textFilePatches = loadPatches(changeList.PATH);

    if (changes != null) {
      final Iterator<TextFilePatch> iterator = textFilePatches.iterator();
      while (iterator.hasNext()) {
        TextFilePatch patch = iterator.next();
        if (!needUnshelve(patch, changes)) {
          remainingPatches.add(patch);
          iterator.remove();
        }
      }
    }
    return textFilePatches;
  }

  private class BinaryPatchApplier implements CustomBinaryPatchApplier {
    private final List<FilePatch> myAppliedPatches;

    private BinaryPatchApplier(final int binaryCount) {
      myAppliedPatches = new ArrayList<FilePatch>();
    }

    @NotNull
    public ApplyPatchStatus apply(final List<Pair<VirtualFile, FilePatch>> patches) throws IOException {
      for (Pair<VirtualFile, FilePatch> patch : patches) {
        final ShelvedBinaryFilePatch shelvedPatch = (ShelvedBinaryFilePatch) patch.getSecond();
        unshelveBinaryFile(shelvedPatch.getShelvedBinaryFile(), patch.getFirst());
        myAppliedPatches.add(shelvedPatch);
      }
      return ApplyPatchStatus.SUCCESS;
    }

    @NotNull
    public List<FilePatch> getAppliedPatches() {
      return myAppliedPatches;
    }
  }

  private static List<ShelvedBinaryFile> getBinaryFilesToUnshelve(final ShelvedChangeList changeList,
                                                                  final List<ShelvedBinaryFile> binaryFiles,
                                                                  final List<ShelvedBinaryFile> remainingBinaries) {
    if (binaryFiles == null) {
      return new ArrayList<ShelvedBinaryFile>(changeList.getBinaryFiles());
    }
    ArrayList<ShelvedBinaryFile> result = new ArrayList<ShelvedBinaryFile>();
    for(ShelvedBinaryFile file: changeList.getBinaryFiles()) {
      if (binaryFiles.contains(file)) {
        result.add(file);
      } else {
        remainingBinaries.add(file);
      }
    }
    return result;
  }

  @Nullable
  private FilePath unshelveBinaryFile(final ShelvedBinaryFile file, @NotNull final VirtualFile patchTarget) throws IOException {
    final Ref<FilePath> result = new Ref<FilePath>();
    final Ref<IOException> ex = new Ref<IOException>();
    final Ref<VirtualFile> patchedFileRef = new Ref<VirtualFile>();
    final File shelvedFile = file.SHELVED_PATH == null ? null : new File(file.SHELVED_PATH);
    
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          result.set(new FilePathImpl(patchTarget));
          if (shelvedFile == null) {
            patchTarget.delete(this);
          }
          else {
            patchTarget.setBinaryContent(FileUtil.loadFileBytes(shelvedFile));
            patchedFileRef.set(patchTarget);
          }
        }
        catch (IOException e) {
          ex.set(e);
        }
      }
    });
    if (!ex.isNull()) {
      throw ex.get();
    }
    if (shelvedFile != null) {
      FileUtil.delete(shelvedFile);
    }
    return result.get();
  }

  private static boolean needUnshelve(final FilePatch patch, final List<ShelvedChange> changes) {
    for(ShelvedChange change: changes) {
      if (Comparing.equal(patch.getBeforeName(), change.getBeforePath())) {
        return true;
      }
    }
    return false;
  }

  private void saveRemainingPatches(final ShelvedChangeList changeList, final List<FilePatch> remainingPatches) {
    OutputStreamWriter writer;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(changeList.PATH));
      try {
        UnifiedDiffWriter.write(remainingPatches, writer, "\n");
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    changeList.clearLoadedChanges();
    notifyStateChanged();
  }

  public void deleteChangeList(final ShelvedChangeList changeList) {
    FileUtil.delete(new File(changeList.PATH));
    for(ShelvedBinaryFile binaryFile: changeList.getBinaryFiles()) {
      final String path = binaryFile.SHELVED_PATH;
      if (path != null) {
        FileUtil.delete(new File(path));
      }
    }
    myShelvedChangeLists.remove(changeList);
    notifyStateChanged();
  }

  public void renameChangeList(final ShelvedChangeList changeList, final String newName) {
    changeList.DESCRIPTION = newName;
    notifyStateChanged();
  }

  public static List<TextFilePatch> loadPatches(final String patchPath) throws IOException, PatchSyntaxException {
    char[] text = FileUtil.loadFileText(new File(patchPath));
    PatchReader reader = new PatchReader(new CharArrayCharSequence(text));
    return reader.readAllPatches();
  }

  private static class ShelvedBinaryFilePatch extends FilePatch {
    private final ShelvedBinaryFile myShelvedBinaryFile;

    public ShelvedBinaryFilePatch(final ShelvedBinaryFile shelvedBinaryFile) {
      myShelvedBinaryFile = shelvedBinaryFile;
      setBeforeName(myShelvedBinaryFile.BEFORE_PATH);
      setAfterName(myShelvedBinaryFile.AFTER_PATH);
    }

    @Override
    public String getBeforeFileName() {
      String[] pathNameComponents = myShelvedBinaryFile.BEFORE_PATH.replace(File.separatorChar, '/').split("/");
      return pathNameComponents [pathNameComponents.length-1];
    }

    @Override
    public String getAfterFileName() {
      String[] pathNameComponents = myShelvedBinaryFile.AFTER_PATH.replace(File.separatorChar, '/').split("/");
      return pathNameComponents [pathNameComponents.length-1];    
    }

    protected void applyCreate(final VirtualFile newFile) throws IOException, ApplyPatchException {
    }
    protected ApplyPatchStatus applyChange(final VirtualFile fileToPatch) throws IOException, ApplyPatchException {
      return null;
    }
    public boolean isNewFile() {
      return myShelvedBinaryFile.BEFORE_PATH == null;
    }
    public boolean isDeletedFile() {
      return myShelvedBinaryFile.AFTER_PATH == null;
    }

    public ShelvedBinaryFile getShelvedBinaryFile() {
      return myShelvedBinaryFile;
    }
  }
}
