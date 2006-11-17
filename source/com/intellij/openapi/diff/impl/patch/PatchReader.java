/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 18:05:20
 */
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

public class PatchReader {
  private enum DiffFormat { CONTEXT, UNIFIED }

  private String[] myLines;
  private int myLineIndex = 0;
  private DiffFormat myDiffFormat = null;
  @NonNls private static final Pattern ourUnifiedHunkStartPattern = Pattern.compile("@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@");
  @NonNls private static final Pattern ourContextBeforeHunkStartPattern = Pattern.compile("\\*\\*\\* (\\d+),(\\d+) \\*\\*\\*\\*");
  @NonNls private static final Pattern ourContextAfterHunkStartPattern = Pattern.compile("--- (\\d+),(\\d+) ----");

  public PatchReader(VirtualFile virtualFile) throws IOException {
    byte[] patchContents = virtualFile.contentsToByteArray();
    CharSequence patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, virtualFile);
    myLines = LineTokenizer.tokenize(patchText, false);
  }

  @Nullable
  public FilePatch readNextPatch() throws PatchSyntaxException {
    while (myLineIndex < myLines.length) {
      String curLine = myLines [myLineIndex];
      if (curLine.startsWith("--- ") && (myDiffFormat == null || myDiffFormat == DiffFormat.UNIFIED)) {
        myDiffFormat = DiffFormat.UNIFIED;
        return readPatch(curLine);
      }
      else if (curLine.startsWith("***") && (myDiffFormat == null || myDiffFormat == DiffFormat.CONTEXT)) {
        myDiffFormat = DiffFormat.CONTEXT;
        return readPatch(curLine);
      }
      myLineIndex++;
    }
    return null;
  }

  private FilePatch readPatch(String curLine) throws PatchSyntaxException {
    final FilePatch curPatch;
    curPatch = new FilePatch();
    curPatch.setBeforeName(extractFileName(curLine));
    myLineIndex++;
    curLine = myLines [myLineIndex];
    String secondNamePrefix = myDiffFormat == DiffFormat.UNIFIED ? "+++ " : "--- ";
    if (!curLine.startsWith(secondNamePrefix)) {
      throw new PatchSyntaxException(myLineIndex, "Second file name expected");
    }
    curPatch.setAfterName(extractFileName(curLine));
    myLineIndex++;
    while(myLineIndex < myLines.length) {
      PatchHunk hunk;
      if (myDiffFormat == DiffFormat.UNIFIED) {
        hunk = readNextHunkUnified();
      }
      else {
        hunk = readNextHunkContext();
      }
      if (hunk == null) break;
      curPatch.addHunk(hunk);
    }
    return curPatch;
  }

  @Nullable
  private PatchHunk readNextHunkUnified() throws PatchSyntaxException {
    String curLine = myLines [myLineIndex];
    if (curLine.startsWith("--- ")) {
      return null;
    }
    if (!curLine.startsWith("@@ ")) {
      throw new PatchSyntaxException(myLineIndex, "Hunk start expected");
    }
    Matcher m = ourUnifiedHunkStartPattern.matcher(curLine);
    if (!m.matches()) {
      throw new PatchSyntaxException(myLineIndex, "Unknown hunk start syntax");
    }
    int startLineBefore = Integer.parseInt(m.group(1));
    int endLineBefore = Integer.parseInt(m.group(2));
    int startLineAfter = Integer.parseInt(m.group(3));
    int endLineAfter = Integer.parseInt(m.group(4));
    PatchHunk hunk = new PatchHunk(startLineBefore, endLineBefore, startLineAfter, endLineAfter);
    myLineIndex++;
    while(myLineIndex < myLines.length) {
      curLine = myLines [myLineIndex];
      if (curLine.startsWith("--- ") || curLine.startsWith("@@ ")) {
        break;
      }
      hunk.addLine(parsePatchLine(curLine, 1));
      myLineIndex++;
    }
    return hunk;
  }

  private PatchLine parsePatchLine(final String line, final int prefixLength) throws PatchSyntaxException {
    PatchLine.Type type;
    if (line.startsWith("+")) {
      type = PatchLine.Type.ADD;
    }
    else if (line.startsWith("-")) {
      type = PatchLine.Type.REMOVE;
    }
    else if (line.startsWith(" ")) {
      type = PatchLine.Type.CONTEXT;
    }
    else {
      throw new PatchSyntaxException(myLineIndex, "Unknown line prefix");
    }
    return new PatchLine(type, line.substring(prefixLength));
  }

  private PatchHunk readNextHunkContext() throws PatchSyntaxException {
    String curLine = myLines [myLineIndex];
    if (!curLine.startsWith("***************")) {
      throw new PatchSyntaxException(myLineIndex, "Hunk start expected");
    }
    myLineIndex++;
    Matcher beforeMatcher = ourContextBeforeHunkStartPattern.matcher(myLines [myLineIndex]);
    if (!beforeMatcher.matches()) {
      throw new PatchSyntaxException(myLineIndex, "Unknown before hunk start syntax");
    }
    myLineIndex++;
    List<String> beforeLines = readContextDiffLines("---");
    if (myLineIndex == myLines.length) {
      throw new PatchSyntaxException(myLineIndex, "Missing after hunk");
    }
    Matcher afterMatcher = ourContextAfterHunkStartPattern.matcher(myLines [myLineIndex]);
    if (!afterMatcher.matches()) {
      throw new PatchSyntaxException(myLineIndex, "Unknown before hunk start syntax");
    }
    myLineIndex++;
    List<String> afterLines = readContextDiffLines("***");
    int startLineBefore = Integer.parseInt(beforeMatcher.group(1));
    int endLineBefore = Integer.parseInt(beforeMatcher.group(2));
    int startLineAfter = Integer.parseInt(afterMatcher.group(1));
    int endLineAfter = Integer.parseInt(afterMatcher.group(2));
    PatchHunk hunk = new PatchHunk(startLineBefore, endLineBefore, startLineAfter, endLineAfter);

    int beforeLineIndex = 0;
    int afterLineIndex = 0;
    if (beforeLines.size() == 0) {
      for(String line: afterLines) {
        hunk.addLine(parsePatchLine(line, 2));
      }
    }
    else if (afterLines.size() == 0) {
      for(String line: beforeLines) {
        hunk.addLine(parsePatchLine(line, 2));
      }
    }
    else {
      while(beforeLineIndex < beforeLines.size() && afterLineIndex < afterLines.size()) {
        String beforeLine = beforeLines.get(beforeLineIndex);
        String afterLine = afterLines.get(afterLineIndex);
        if (beforeLine.startsWith(" ") && afterLine.startsWith(" ")) {
          hunk.addLine(new PatchLine(PatchLine.Type.CONTEXT, beforeLine.substring(2)));
          beforeLineIndex++;
          afterLineIndex++;
        }
        else if (beforeLine.startsWith("-")) {
          hunk.addLine(new PatchLine(PatchLine.Type.REMOVE, beforeLine.substring(2)));
          beforeLineIndex++;
        }
        else if (afterLine.startsWith("+")) {
          hunk.addLine(new PatchLine(PatchLine.Type.ADD, afterLine.substring(2)));
          afterLineIndex++;
        }
        else if (beforeLine.startsWith("!") && afterLine.startsWith("!")) {
          while(beforeLineIndex < beforeLines.size() && beforeLines.get(beforeLineIndex).startsWith("! ")) {
            hunk.addLine(new PatchLine(PatchLine.Type.REMOVE, beforeLines.get(beforeLineIndex).substring(2)));
            beforeLineIndex++;
          }

          while(afterLineIndex < afterLines.size() && afterLines.get(afterLineIndex).startsWith("! ")) {
            hunk.addLine(new PatchLine(PatchLine.Type.ADD, afterLines.get(afterLineIndex).substring(2)));
            afterLineIndex++;
          }
        }
        else {
          throw new PatchSyntaxException(-1, "Unknown line prefix");
        }
      }
    }
    return hunk;
  }

  private List<String> readContextDiffLines(final String terminator) {
    ArrayList<String> result = new ArrayList<String>();
    while(myLineIndex < myLines.length) {
      if (myLines [myLineIndex].startsWith(terminator)) {
        break;
      }
      result.add(myLines [myLineIndex]);
      myLineIndex++;
    }
    return result;
  }

  private static String extractFileName(final String curLine) {
    String fileName = curLine.substring(4);
    int pos = fileName.indexOf('\t');
    if (pos >= 0) {
      fileName = fileName.substring(0, pos);
    }
    return fileName;
  }
}
