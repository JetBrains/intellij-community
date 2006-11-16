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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;

public class PatchReader {
  private String[] myLines;
  private int myLineIndex = 0;
  @NonNls private Pattern myHunkStartPattern = Pattern.compile("@@ -(\\d+),\\d+ \\+(\\d+),\\d+ @@");

  public PatchReader(VirtualFile virtualFile) throws IOException {
    byte[] patchContents = virtualFile.contentsToByteArray();
    CharSequence patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, virtualFile);
    myLines = LineTokenizer.tokenize(patchText, false);
  }

  @Nullable
  public FilePatch readNextPatch() throws PatchSyntaxException {
    FilePatch curPatch = null;
    while (myLineIndex < myLines.length) {
      String curLine = myLines [myLineIndex];
      if (curLine.startsWith("--- ")) {
        if (curPatch != null) {
          return curPatch;
        }
        curPatch = new FilePatch();
        curPatch.setBeforeName(extractFileName(curLine));
        myLineIndex++;
        curLine = myLines [myLineIndex];
        if (!curLine.startsWith("+++ ")) {
          throw new PatchSyntaxException(myLineIndex, "Second file name expected");
        }
        curPatch.setAfterName(extractFileName(curLine));
        myLineIndex++;
        while(true) {
          PatchHunk hunk = readNextHunk();
          if (hunk == null) break;
          curPatch.addHunk(hunk);
        }
      }
    }
    return curPatch;
  }

  @Nullable
  private PatchHunk readNextHunk() throws PatchSyntaxException {
    if (myLineIndex == myLines.length) {
      // EOF
      return null;
    }
    String curLine = myLines [myLineIndex];
    if (curLine.startsWith("--- ")) {
      return null;
    }
    if (!curLine.startsWith("@@ ")) {
      throw new PatchSyntaxException(myLineIndex, "Hunk start expected");
    }
    Matcher m = myHunkStartPattern.matcher(curLine);
    if (!m.matches()) {
      throw new PatchSyntaxException(myLineIndex, "Unknown hunk start syntax");
    }
    int lineBefore = Integer.parseInt(m.group(1));
    int lineAfter = Integer.parseInt(m.group(2));
    PatchHunk hunk = new PatchHunk(lineBefore, lineAfter);
    myLineIndex++;
    while(myLineIndex < myLines.length) {
      curLine = myLines [myLineIndex];
      if (curLine.startsWith("--- ") || curLine.startsWith("@@ ")) {
        break;
      }
      hunk.addLine(parsePatchLine(curLine));
      myLineIndex++;
    }
    return hunk;
  }

  private PatchLine parsePatchLine(final String line) throws PatchSyntaxException {
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
    return new PatchLine(type, line.substring(1));
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
