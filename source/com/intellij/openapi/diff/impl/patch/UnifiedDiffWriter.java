/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.11.2006
 * Time: 15:29:45
 */
package com.intellij.openapi.diff.impl.patch;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

public class UnifiedDiffWriter {
  public static void write(Collection<FilePatch> patches, Writer writer) throws IOException {
    for(FilePatch patch: patches) {
      writeFileHeading(patch, writer);
      for(PatchHunk hunk: patch.getHunks()) {
        writeHunkStart(writer, hunk.getStartLineBefore(), hunk.getEndLineBefore(), hunk.getStartLineAfter(), hunk.getEndLineAfter());
        for(PatchLine line: hunk.getLines()) {
          char prefixChar = ' ';
          switch(line.getType()) {
            case ADD: prefixChar = '+'; break;
            case REMOVE: prefixChar = '-'; break;
            case CONTEXT: prefixChar = ' '; break;
          }
          writeLine(writer, line.getText(), prefixChar);
          if (line.isSuppressNewLine()) {
            writer.write("\n" + PatchReader.NO_NEWLINE_SIGNATURE + "\n");
          }
        }
      }
    }
  }

  private static void writeFileHeading(final FilePatch patch, final Writer writer) throws IOException {
    writeRevisionHeading(writer, "---", patch.getBeforeName(), patch.getBeforeVersionId());
    writeRevisionHeading(writer, "+++", patch.getAfterName(), patch.getAfterVersionId());
  }

  private static void writeRevisionHeading(final Writer writer, final String prefix, final String revisionPath, final String revisionName)
    throws IOException {
    writer.write(prefix + " ");
    writer.write(revisionPath);
    writer.write("\t");
    writer.write(revisionName);
    writer.write("\n");
  }

  private static void writeHunkStart(Writer writer, int startLine1, int endLine1, int startLine2, int endLine2)
    throws IOException {
    StringBuilder builder = new StringBuilder("@@ -");
    builder.append(startLine1+1).append(",").append(endLine1-startLine1);
    builder.append(" +").append(startLine2+1).append(",").append(endLine2-startLine2).append(" @@\n");
    writer.append(builder.toString());
  }

  private static void writeLine(final Writer writer, final String line, final char prefix) throws IOException {
    writer.write(prefix);
    writer.write(line);
  }
}