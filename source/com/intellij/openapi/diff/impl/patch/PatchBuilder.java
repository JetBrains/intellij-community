/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.11.2006
 * Time: 16:25:44
 */
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.processing.DiffCorrection;
import com.intellij.openapi.diff.impl.processing.DiffFragmentsProcessor;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.VcsException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class PatchBuilder {
  private static final int CONTEXT_LINES = 3;

  public static List<FilePatch> buildPatch(final Collection<Change> changes, final String basePath) throws VcsException {
    List<FilePatch> result = new ArrayList<FilePatch>();
    for(Change c: changes) {
      final ContentRevision beforeRevision = c.getBeforeRevision();
      final ContentRevision afterRevision = c.getAfterRevision();
      if (beforeRevision != null && beforeRevision.getFile().isDirectory()) {
        continue;
      }
      if (afterRevision != null && afterRevision.getFile().isDirectory()) {
        continue;
      }
      if (beforeRevision == null) {
        result.add(buildAddedFile(basePath, afterRevision));
        continue;
      }
      if (afterRevision == null) {
        result.add(buildDeletedFile(basePath, beforeRevision));
        continue;
      }
      final String beforeContent = beforeRevision.getContent();
      final String afterContent = afterRevision.getContent();
      String[] beforeLines = DiffUtil.convertToLines(beforeContent);
      String[] afterLines = DiffUtil.convertToLines(afterContent);

      DiffFragment[] woFormattingBlocks = DiffPolicy.LINES_WO_FORMATTING.buildFragments(beforeContent, afterContent);
      DiffFragment[] step1lineFragments = new DiffCorrection.TrueLineBlocks(ComparisonPolicy.DEFAULT).correctAndNormalize(woFormattingBlocks);
      ArrayList<LineFragment> fragments = new DiffFragmentsProcessor().process(step1lineFragments);

      if (fragments.size() > 1) {
        FilePatch patch = buildPatchHeading(basePath, beforeRevision, afterRevision);
        result.add(patch);

        int lastLine1 = 0;
        int lastLine2 = 0;

        while(fragments.size() > 0) {
          List<LineFragment> adjacentFragments = getAdjacentFragments(fragments);
          if (adjacentFragments.size() > 0) {
            LineFragment first = adjacentFragments.get(0);
            LineFragment last = adjacentFragments.get(adjacentFragments.size()-1);

            final int start1 = first.getStartingLine1();
            final int start2 = first.getStartingLine2();
            final int end1 = last.getStartingLine1() + last.getModifiedLines1();
            final int end2 = last.getStartingLine2() + last.getModifiedLines2();
            int contextStart1 = Math.max(start1 - CONTEXT_LINES, lastLine1);
            int contextStart2 = Math.max(start2 - CONTEXT_LINES, lastLine2);
            int contextEnd1 = Math.min(end1 + CONTEXT_LINES, beforeLines.length);
            int contextEnd2 = Math.min(end2 + CONTEXT_LINES, afterLines.length);

            PatchHunk hunk = new PatchHunk(contextStart1, contextEnd1, contextStart2, contextEnd2);
            patch.addHunk(hunk);

            for(LineFragment fragment: adjacentFragments) {
              for(int i=contextStart1; i<fragment.getStartingLine1(); i++) {
                hunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, beforeLines [i]));
              }
              for(int i=fragment.getStartingLine1(); i<fragment.getStartingLine1()+fragment.getModifiedLines1(); i++) {
                addLineToHunk(hunk, beforeLines [i], PatchLine.Type.REMOVE);
              }
              for(int i=fragment.getStartingLine2(); i<fragment.getStartingLine2()+fragment.getModifiedLines2(); i++) {
                addLineToHunk(hunk, afterLines[i], PatchLine.Type.ADD);
              }
              contextStart1 = fragment.getStartingLine1()+fragment.getModifiedLines1();
            }
            for(int i=contextStart1; i<contextEnd1; i++) {
              hunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, beforeLines [i]));
            }
          }
        }
      }
    }
    return result;
  }

  private static void addLineToHunk(final PatchHunk hunk, final String line, final PatchLine.Type type) {
    final PatchLine patchLine = new PatchLine(type, line);
    if (!line.endsWith("\n")) {
      patchLine.setSuppressNewLine(true);
    }
    hunk.addLine(patchLine);
  }

  private static FilePatch buildAddedFile(final String basePath, final ContentRevision afterRevision) throws VcsException {
    String[] lines = DiffUtil.convertToLines(afterRevision.getContent());
    FilePatch result = buildPatchHeading(basePath, afterRevision, afterRevision);
    PatchHunk hunk = new PatchHunk(-1, -1, 0, lines.length);
    for(String line: lines) {
      addLineToHunk(hunk, line, PatchLine.Type.ADD);
    }
    result.addHunk(hunk);
    return result;
  }

  private static FilePatch buildDeletedFile(String basePath, ContentRevision beforeRevision) throws VcsException {
    String[] lines = DiffUtil.convertToLines(beforeRevision.getContent());
    FilePatch result = buildPatchHeading(basePath, beforeRevision, beforeRevision);
    PatchHunk hunk = new PatchHunk(0, lines.length, -1, -1);
    for(String line: lines) {
      addLineToHunk(hunk, line, PatchLine.Type.REMOVE);
    }
    result.addHunk(hunk);
    return result;
  }

  private static List<LineFragment> getAdjacentFragments(final ArrayList<LineFragment> fragments) {
    List<LineFragment> result = new ArrayList<LineFragment>();
    int endLine = -1;
    while(!fragments.isEmpty()) {
      LineFragment fragment = fragments.get(0);
      if (fragment.getType() == null || fragment.getType() == TextDiffType.NONE) {
        fragments.remove(0);
        continue;
      }

      if (result.isEmpty() || endLine + CONTEXT_LINES >= fragment.getStartingLine1()) {
        result.add(fragment);
        fragments.remove(0);
        endLine = fragment.getStartingLine1() + fragment.getModifiedLines1();
      }
      else {
        break;
      }
    }
    return result;
  }

  private static String getRelativePath(final String basePath, final File ioFile) {
    return FileUtil.getRelativePath(new File(basePath), ioFile).replace(File.separatorChar, '/');
  }

  private static String getRevisionName(final ContentRevision revision, final File ioFile) {
    String revisionName = revision.getRevisionNumber().asString();
    if (revisionName.length() == 0) {
      revisionName = new Date(ioFile.lastModified()).toString();
    }
    return revisionName;
  }

  private static FilePatch buildPatchHeading(final String basePath, final ContentRevision beforeRevision, final ContentRevision afterRevision) {
    FilePatch result = new FilePatch();
    File beforeFile = beforeRevision.getFile().getIOFile();
    result.setBeforeName(getRelativePath(basePath, beforeFile));
    result.setBeforeVersionId(getRevisionName(beforeRevision, beforeFile));

    File afterFile = afterRevision.getFile().getIOFile();
    result.setAfterName(getRelativePath(basePath, afterFile));
    result.setAfterVersionId(getRevisionName(afterRevision, afterFile));

    return result;
  }
}
