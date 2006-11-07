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

import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.processing.TextCompareProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class PatchBuilder {
  private static final int CONTEXT_LINES = 3;

  public static void buildPatch(final ChangeList changeList, final String basePath, final Writer writer) throws IOException {
    Collection<Change> changes = changeList.getChanges();
    TextCompareProcessor textCompareProcessor = new TextCompareProcessor(ComparisonPolicy.DEFAULT);
    for(Change c: changes) {
      final ContentRevision beforeRevision = c.getBeforeRevision();
      final ContentRevision afterRevision = c.getAfterRevision();
      if (beforeRevision == null) {
        writeAddedFile(writer, basePath, afterRevision);
        continue;
      }
      if (afterRevision == null) {
        writeDeletedFile(writer, basePath, beforeRevision);
        continue;
      }
      final String beforeContent = beforeRevision.getContent();
      final String afterContent = afterRevision.getContent();
      String[] beforeLines = DiffUtil.convertToLines(beforeContent);
      String[] afterLines = DiffUtil.convertToLines(afterContent);
      ArrayList<LineFragment> fragments = textCompareProcessor.process(beforeContent, afterContent);
      if (fragments.size() > 1) {
        writeFileHeading(writer, basePath, beforeRevision, afterRevision);

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

            writeDiffFragmentStart(writer, contextStart1, contextEnd1, contextStart2, contextEnd2);

            for(LineFragment fragment: adjacentFragments) {
              for(int i=contextStart1; i<fragment.getStartingLine1(); i++) {
                writeLine(writer, beforeLines [i], ' ');
              }
              for(int i=fragment.getStartingLine1(); i<fragment.getStartingLine1()+fragment.getModifiedLines1(); i++) {
                writeLine(writer, beforeLines [i], '-');
              }
              for(int i=fragment.getStartingLine2(); i<fragment.getStartingLine2()+fragment.getModifiedLines2(); i++) {
                writeLine(writer, afterLines [i], '+');
              }
              contextStart1 = fragment.getStartingLine1()+fragment.getModifiedLines1();
            }
            for(int i=contextStart1; i<contextEnd1; i++) {
              writeLine(writer, beforeLines [i], ' ');
            }
          }
        }
      }
    }
  }

  private static void writeAddedFile(final Writer writer, final String basePath, final ContentRevision afterRevision) throws IOException {
    String[] lines = DiffUtil.convertToLines(afterRevision.getContent());
    writeFileHeading(writer, basePath, afterRevision, afterRevision);
    writeDiffFragmentStart(writer, -1, -1, 0, lines.length);
    for(String line: lines) {
      writeLine(writer, line, '+');
    }
  }

  private static void writeDeletedFile(Writer writer, String basePath, ContentRevision beforeRevision) throws IOException {
    String[] lines = DiffUtil.convertToLines(beforeRevision.getContent());
    writeFileHeading(writer, basePath, beforeRevision, beforeRevision);
    writeDiffFragmentStart(writer, 0, lines.length, -1, -1);
    for(String line: lines) {
      writeLine(writer, line, '-');
    }
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

  private static void writeLine(final Writer writer, final String line, final char prefix) throws IOException {
    writer.write(prefix);
    writer.write(line);
  }

  private static void writeFileHeading(final Writer writer, final String basePath, final ContentRevision beforeRevision, final ContentRevision afterRevision)
    throws IOException {
    writeRevisionHeading(writer, "---", basePath, beforeRevision);
    writeRevisionHeading(writer, "+++", basePath, afterRevision);
  }

  private static void writeRevisionHeading(final Writer writer, final String prefix, final String basePath, final ContentRevision revision)
    throws IOException {
    writer.write(prefix + " ");
    File ioFile = revision.getFile().getIOFile();
    String relativePath = FileUtil.getRelativePath(new File(basePath), ioFile).replace(File.separatorChar, '/');
    writer.write(relativePath);
    writer.write("\t");
    String revisionName = revision.getRevisionNumber().asString();
    if (revisionName.length() == 0) {
      revisionName = new Date(ioFile.lastModified()).toString();
    }
    writer.write(revisionName);
    writer.write("\n");
  }

  private static void writeDiffFragmentStart(Writer writer, int startLine1, int endLine1, int startLine2, int endLine2)
    throws IOException {
    StringBuilder builder = new StringBuilder("@@ -");
    builder.append(startLine1+1).append(",").append(endLine1-startLine1);
    builder.append(" +").append(startLine2+1).append(",").append(endLine2-startLine2).append(" @@\n");
    writer.append(builder.toString());
  }
}
