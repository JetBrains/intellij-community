// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnDiffProvider;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.history.HistoryClient;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.fileEditor.impl.LoadTextUtil.getTextByBinaryPresentation;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnUtil.*;

public class SvnAnnotationProvider implements AnnotationProvider, VcsCacheableAnnotationProvider {
  private static final Object MERGED_KEY = new Object();
  @NotNull private final SvnVcs myVcs;

  public SvnAnnotationProvider(@NotNull SvnVcs vcs) {
    myVcs = vcs;
  }

  @Override
  @NotNull
  public FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException {
    SvnDiffProvider provider = (SvnDiffProvider)myVcs.getDiffProvider();
    SvnRevisionNumber currentRevision = ((SvnRevisionNumber)provider.getCurrentRevision(file));
    VcsRevisionDescription lastChangedRevision = provider.getCurrentRevisionDescription(file);
    if (lastChangedRevision == null) {
      throw new VcsException("Can not get current revision for file " + file.getPath());
    }
    final Revision revision = ((SvnRevisionNumber)lastChangedRevision.getRevisionNumber()).getRevision();
    if (!revision.isValid()) {
      throw new VcsException(
        "Can not get last changed revision for file: " + file.getPath() + "\nPlease run svn info for this file and file an issue.");
    }
    return annotate(file, currentRevision, lastChangedRevision.getRevisionNumber(), () -> {
      byte[] data =
        getFileContents(myVcs, Target.on(virtualToIoFile(file).getAbsoluteFile()), Revision.BASE, Revision.UNDEFINED);
      return getTextByBinaryPresentation(data, file, false, false).toString();
    });
  }

  @Override
  @NotNull
  public FileAnnotation annotate(@NotNull VirtualFile file, @NotNull VcsFileRevision revision) throws VcsException {
    return annotate(file, ((SvnFileRevision)revision).getRevisionNumber(), revision.getRevisionNumber(), () -> {
      byte[] bytes = VcsHistoryUtil.loadRevisionContent(revision);
      return getTextByBinaryPresentation(bytes, file, false, false).toString();
    });
  }

  @NotNull
  private FileAnnotation annotate(@NotNull VirtualFile file,
                                  @NotNull SvnRevisionNumber revisionNumber,
                                  @NotNull VcsRevisionNumber lastChangedRevision,
                                  @NotNull Throwable2Computable<String, VcsException, IOException> contentLoader) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException(SvnBundle.message("exception.text.cannot.annotate.directory"));
    }
    final FileAnnotation[] annotation = new FileAnnotation[1];
    final VcsException[] exception = new VcsException[1];

    Runnable command = () -> {
      final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      final File ioFile = virtualToIoFile(file).getAbsoluteFile();
      Info info = null;
      try {

        String contents = contentLoader.compute();
        final SvnFileAnnotation result = new SvnFileAnnotation(myVcs, file, contents, lastChangedRevision);

        info = myVcs.getInfo(ioFile);
        if (info == null) {
          exception[0] = new SvnBindException("File '" + ioFile + "' is not under version control");
          return;
        }
        Url url = info.getURL();
        Revision endRevision = revisionNumber.getRevision();
        if (Revision.WORKING.equals(endRevision)) {
          endRevision = info.getRevision();
        }
        if (progress != null) {
          progress.setText(SvnBundle.message("progress.text.computing.annotation", file.getName()));
        }

        // ignore mime type=true : IDEA-19562
        final AnnotationConsumer annotateHandler = createAnnotationHandler(progress, result);

        boolean calculateMergeinfo = myVcs.getSvnConfiguration().isShowMergeSourcesInAnnotate() && checkRepositoryVersion15(myVcs, url);
        final MySteppedLogGetter logGetter = new MySteppedLogGetter(
          myVcs, ioFile, progress,
          myVcs.getFactory(ioFile).createHistoryClient(), endRevision, result,
          url, calculateMergeinfo, file.getCharset());

        logGetter.go();
        final LinkedList<Revision> rp = logGetter.getRevisionPoints();

        // TODO: only 2 elements will be in rp and for loop will be executed only once - probably rewrite with Pair
        AnnotateClient annotateClient = myVcs.getFactory(ioFile).createAnnotateClient();
        for (int i = 0; i < rp.size() - 1; i++) {
          annotateClient.annotate(Target.on(ioFile), rp.get(i + 1), rp.get(i), calculateMergeinfo, getLogClientOptions(myVcs),
                                  annotateHandler);
        }

        if (rp.get(1).getNumber() > 0) {
          result.setFirstRevision(rp.get(1));
        }
        annotation[0] = result;
      }
      catch (IOException e) {
        exception[0] = new VcsException(e);
      }
      catch (SvnBindException e) {
        handleSvnException(ioFile, info, e, file, revisionNumber, annotation, exception);
      }
      catch (VcsException e) {
        exception[0] = e;
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(command, SvnBundle.message("action.text.annotate"), false, myVcs.getProject());
    }
    else {
      command.run();
    }
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
    return annotation[0];
  }

  private void handleSvnException(File ioFile,
                                  Info info,
                                  @NotNull SvnBindException e,
                                  @NotNull VirtualFile file,
                                  @NotNull SvnRevisionNumber revisionNumber,
                                  @NotNull FileAnnotation[] annotation,
                                  @NotNull VcsException[] exception) {
    // TODO: Check how this scenario could be reproduced by user and what changes needs to be done for command line client
    if (e.contains(ErrorCode.FS_NOT_FOUND)) {
      final CommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> provider = myVcs.getCommittedChangesProvider();
      try {
        final Pair<SvnChangeList, FilePath> pair = provider.getOneList(file, revisionNumber);
        if (pair != null && info != null && pair.getSecond() != null && !Comparing.equal(pair.getSecond().getIOFile(), ioFile)) {
          annotation[0] = annotateNonExisting(pair, revisionNumber, info, file.getCharset(), file);
        }
      }
      catch (VcsException e1) {
        exception[0] = e1;
      }
      catch (IOException e1) {
        exception[0] = new VcsException(e1);
      }
    }
    else {
      exception[0] = e;
    }
  }

  public static File getCommonAncestor(final File file1, final File file2) throws IOException {
    if (FileUtil.filesEqual(file1, file2)) return file1;
    final File can1 = file1.getCanonicalFile();
    final File can2 = file2.getCanonicalFile();
    final List<String> parts1 = StringUtil.split(can1.getPath(), File.separator, true);
    final List<String> parts2 = StringUtil.split(can2.getPath(), File.separator, true);
    int cnt = 0;
    while (parts1.size() > cnt && parts2.size() > cnt) {
      if (!parts1.get(cnt).equals(parts2.get(cnt))) {
        if (cnt > 0) {
          return new File(StringUtil.join(parts1.subList(0, cnt), File.separator));
        }
        else {
          return null;
        }
      }
      ++cnt;
    }
    //shorter one
    if (parts1.size() > parts2.size()) {
      return file2;
    }
    else {
      return file1;
    }
  }

  @NotNull
  private SvnRemoteFileAnnotation annotateNonExisting(@NotNull Pair<SvnChangeList, FilePath> pair,
                                                      @NotNull SvnRevisionNumber revisionNumber,
                                                      @NotNull Info info,
                                                      @NotNull Charset charset,
                                                      @NotNull VirtualFile current) throws VcsException, IOException {
    final File wasFile = pair.getSecond().getIOFile();
    final File root = getCommonAncestor(wasFile, info.getFile());

    if (root == null) {
      throw new VcsException("Can not find relative path for " + wasFile.getPath() + "@" + revisionNumber.asString());
    }

    final String relativePath = FileUtil.getRelativePath(root.getPath(), wasFile.getPath(), File.separatorChar);
    if (relativePath == null) {
      throw new VcsException("Can not find relative path for " + wasFile.getPath() + "@" + revisionNumber.asString());
    }

    Info wcRootInfo = myVcs.getInfo(root);
    if (wcRootInfo == null || wcRootInfo.getURL() == null) {
      throw new VcsException("Can not find relative path for " + wasFile.getPath() + "@" + revisionNumber.asString());
    }
    Url wasUrl = wcRootInfo.getURL();
    final String[] strings = relativePath.replace('\\', '/').split("/");
    for (String string : strings) {
      wasUrl = append(wasUrl, string, true);
    }

    final Revision revision = revisionNumber.getRevision();
    byte[] data = getFileContents(myVcs, Target.on(wasUrl), revision, revision);
    final String contents = getTextByBinaryPresentation(data, charset).toString();
    final SvnRemoteFileAnnotation result = new SvnRemoteFileAnnotation(myVcs, contents, revisionNumber, current);
    final AnnotationConsumer annotateHandler = createAnnotationHandler(ProgressManager.getInstance().getProgressIndicator(), result);

    boolean calculateMergeinfo = myVcs.getSvnConfiguration().isShowMergeSourcesInAnnotate() && checkRepositoryVersion15(myVcs, wasUrl);
    AnnotateClient client = myVcs.getFactory().createAnnotateClient();
    client
      .annotate(Target.on(wasUrl, revision), Revision.of(1), revision, calculateMergeinfo, getLogClientOptions(myVcs),
                annotateHandler);
    return result;
  }

  @NotNull
  private static AnnotationConsumer createAnnotationHandler(@Nullable final ProgressIndicator progress,
                                                            @NotNull final BaseSvnFileAnnotation result) {
    return (lineNumber, info, mergeInfo) -> {
      if (progress != null) {
        progress.checkCanceled();
      }

      result.setLineInfo(lineNumber, info, mergeInfo != null && info.getRevision() > mergeInfo.getRevision() ? mergeInfo : null);
    };
  }

  @Override
  public VcsAnnotation createCacheable(FileAnnotation fileAnnotation) {
    if (!(fileAnnotation instanceof SvnFileAnnotation)) return null;
    final SvnFileAnnotation svnFileAnnotation = (SvnFileAnnotation)fileAnnotation;
    final AnnotationSourceSwitcher annotationSourceSwitcher = svnFileAnnotation.getAnnotationSourceSwitcher();
    if (annotationSourceSwitcher != null) {
      annotationSourceSwitcher.switchTo(AnnotationSource.LOCAL);
    }
    final int size = svnFileAnnotation.getLineCount();

    final VcsUsualLineAnnotationData lineAnnotationData = new VcsUsualLineAnnotationData(size);
    for (int i = 0; i < size; i++) {
      final VcsRevisionNumber revisionNumber = svnFileAnnotation.getLineRevisionNumber(i);
      lineAnnotationData.put(i, revisionNumber);
    }

    final VcsAnnotation vcsAnnotation = new VcsAnnotation(VcsUtil.getFilePath(svnFileAnnotation.getFile()), lineAnnotationData,
                                                          svnFileAnnotation.getFirstRevisionNumber());

    if (annotationSourceSwitcher != null) {
      final VcsRareLineAnnotationData merged = new VcsRareLineAnnotationData(size);
      final Map<VcsRevisionNumber, VcsFileRevision> addMap = new HashMap<>();

      annotationSourceSwitcher.switchTo(AnnotationSource.MERGE);
      for (int i = 0; i < size; i++) {
        if (annotationSourceSwitcher.mergeSourceAvailable(i)) {
          final VcsRevisionNumber number = svnFileAnnotation.getLineRevisionNumber(i);
          if (number == null) continue;
          merged.put(i, number);
          addMap.put(number, svnFileAnnotation.getRevision(((SvnRevisionNumber)number).getRevision().getNumber()));
        }
      }
      if (!merged.isEmpty()) {
        vcsAnnotation.addAnnotation(MERGED_KEY, merged);
        vcsAnnotation.addCachedOtherRevisions(addMap);
      }
    }

    return vcsAnnotation;
  }

  @Nullable
  @Override
  public FileAnnotation restore(@NotNull VcsAnnotation vcsAnnotation,
                                @NotNull VcsAbstractHistorySession session,
                                @NotNull String annotatedContent,
                                boolean forCurrentRevision,
                                VcsRevisionNumber revisionNumber) {
    final SvnFileAnnotation annotation =
      new SvnFileAnnotation(myVcs, vcsAnnotation.getFilePath().getVirtualFile(), annotatedContent, revisionNumber);
    final VcsLineAnnotationData basicAnnotation = vcsAnnotation.getBasicAnnotation();
    final VcsLineAnnotationData data = vcsAnnotation.getAdditionalAnnotations().get(MERGED_KEY);
    final Map<VcsRevisionNumber, VcsFileRevision> historyAsMap = session.getHistoryAsMap();
    final Map<VcsRevisionNumber, VcsFileRevision> cachedOtherRevisions = vcsAnnotation.getCachedOtherRevisions();

    for (int i = 0; i < basicAnnotation.getNumLines(); i++) {
      final VcsRevisionNumber revision = basicAnnotation.getRevision(i);
      final VcsRevisionNumber mergedData = data == null ? null : data.getRevision(i);
      final SvnFileRevision fileRevision = (SvnFileRevision)historyAsMap.get(revision);
      if (fileRevision == null) return null;

      if (mergedData == null) {
        annotation.setLineInfo(i, fileRevision.getCommitInfo(), null);
      }
      else {
        final SvnFileRevision mergedRevision = (SvnFileRevision)cachedOtherRevisions.get(mergedData);
        if (mergedRevision == null) return null;
        annotation.setLineInfo(i, fileRevision.getCommitInfo(), mergedRevision.getCommitInfo());
      }
    }
    if (vcsAnnotation.getFirstRevision() != null) {
      annotation.setFirstRevision(((SvnRevisionNumber)vcsAnnotation.getFirstRevision()).getRevision());
    }
    for (VcsFileRevision revision : session.getRevisionList()) {
      annotation.setRevision(((SvnRevisionNumber)revision.getRevisionNumber()).getRevision().getNumber(), (SvnFileRevision)revision);
    }
    return annotation;
  }

  private static class MySteppedLogGetter {
    private final LinkedList<Revision> myRevisionPoints;
    private final SvnVcs myVcs;
    private final File myIoFile;
    private final ProgressIndicator myProgress;
    private final HistoryClient myClient;
    private final Revision myEndRevision;
    private final boolean myCalculateMergeinfo;
    private final SvnFileAnnotation myResult;
    private final Url myUrl;
    private final Charset myCharset;

    private MySteppedLogGetter(final SvnVcs vcs, final File ioFile, final ProgressIndicator progress, final HistoryClient client,
                               final Revision endRevision,
                               final SvnFileAnnotation result,
                               Url url,
                               final boolean calculateMergeinfo,
                               Charset charset) {
      myVcs = vcs;
      myIoFile = ioFile;
      myProgress = progress;
      myClient = client;
      myEndRevision = endRevision;
      myCalculateMergeinfo = calculateMergeinfo;
      myResult = result;
      myUrl = url;
      myCharset = charset;
      myRevisionPoints = new LinkedList<>();
    }

    public void go() throws VcsException {
      int maxAnnotateRevisions = myVcs.getSvnConfiguration().getMaxAnnotateRevisions();
      boolean longHistory = true;
      if (maxAnnotateRevisions == -1) {
        longHistory = false;
      }
      else {
        if (myEndRevision.getNumber() < maxAnnotateRevisions) {
          longHistory = false;
        }
      }

      if (!longHistory) {
        doLog(myCalculateMergeinfo, null, 0);
        putDefaultBounds();
      }
      else {
        doLog(false, null, 0);
        final List<VcsFileRevision> fileRevisionList = myResult.getRevisions();
        if (fileRevisionList.size() < maxAnnotateRevisions) {
          putDefaultBounds();
          if (myCalculateMergeinfo) {
            doLog(true, null, 0);
          }
          return;
        }

        myRevisionPoints.add(((SvnRevisionNumber)fileRevisionList.get(0).getRevisionNumber()).getRevision());
        final Revision truncateTo =
          ((SvnRevisionNumber)fileRevisionList.get(maxAnnotateRevisions - 1).getRevisionNumber()).getRevision();
        myRevisionPoints.add(truncateTo);

        // todo file history can be asked in parallel
        if (myCalculateMergeinfo) {
          doLog(true, truncateTo, maxAnnotateRevisions);
        }
      }
    }

    private void putDefaultBounds() {
      myRevisionPoints.add(myEndRevision);
      myRevisionPoints.add(Revision.of(0));
    }

    private void doLog(final boolean includeMerged, final Revision truncateTo, final int max) throws VcsException {
      myClient.doLog(Target.on(myIoFile), myEndRevision, truncateTo == null ? Revision.of(1L) : truncateTo,
                     false, false, includeMerged, max, null,
                     logEntry -> {
                       if (Revision.UNDEFINED.getNumber() == logEntry.getRevision()) {
                         return;
                       }

                       if (myProgress != null) {
                         myProgress.checkCanceled();
                         myProgress.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
                       }
                       myResult.setRevision(logEntry.getRevision(), new SvnFileRevision(myVcs, Revision.UNDEFINED, logEntry, myUrl, ""));
                     });
    }

    public LinkedList<Revision> getRevisionPoints() {
      return myRevisionPoints;
    }
  }

  @Nullable
  private static DiffOptions getLogClientOptions(@NotNull SvnVcs vcs) {
    return vcs.getSvnConfiguration().isIgnoreSpacesInAnnotate() ? new DiffOptions(true, true, true) : null;
  }
}
