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

import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.text.MessageFormat;

public class UnifiedDiffWriter {
  @NonNls private static final String INDEX_SIGNATURE = "Index: {0}{1}";
  private static final String HEADER_SEPARATOR = "===================================================================";

  private UnifiedDiffWriter() {
  }

  public static void write(Collection<FilePatch> patches, Writer writer, final String lineSeparator) throws IOException {
    for(FilePatch patch: patches) {
      writeFileHeading(patch, writer, lineSeparator);
      for(PatchHunk hunk: patch.getHunks()) {
        writeHunkStart(writer, hunk.getStartLineBefore(), hunk.getEndLineBefore(), hunk.getStartLineAfter(), hunk.getEndLineAfter(),
                       lineSeparator);
        for(PatchLine line: hunk.getLines()) {
          char prefixChar = ' ';
          switch(line.getType()) {
            case ADD: prefixChar = '+'; break;
            case REMOVE: prefixChar = '-'; break;
            case CONTEXT: prefixChar = ' '; break;
          }
          String text = line.getText();
          if (text.endsWith("\n")) {
            text = text.substring(0, text.length()-1);
          }
          writeLine(writer, text, prefixChar);
          if (line.isSuppressNewLine()) {
            writer.write(lineSeparator + PatchReader.NO_NEWLINE_SIGNATURE + lineSeparator);
          }
          else {
            writer.write(lineSeparator);
          }
        }
      }
    }
  }

  private static void writeFileHeading(final FilePatch patch, final Writer writer, final String lineSeparator) throws IOException {
    writer.write(MessageFormat.format(INDEX_SIGNATURE, patch.getBeforeName(), lineSeparator));
    writer.write(HEADER_SEPARATOR + lineSeparator);
    writeRevisionHeading(writer, "---", patch.getBeforeName(), patch.getBeforeVersionId(), lineSeparator);
    writeRevisionHeading(writer, "+++", patch.getAfterName(), patch.getAfterVersionId(), lineSeparator);
  }

  private static void writeRevisionHeading(final Writer writer, final String prefix,
                                           final String revisionPath, final String revisionName,
                                           final String lineSeparator)
    throws IOException {
    writer.write(prefix + " ");
    writer.write(revisionPath);
    writer.write("\t");
    writer.write(revisionName);
    writer.write(lineSeparator);
  }

  private static void writeHunkStart(Writer writer, int startLine1, int endLine1, int startLine2, int endLine2,
                                     final String lineSeparator)
    throws IOException {
    StringBuilder builder = new StringBuilder("@@ -");
    builder.append(startLine1+1).append(",").append(endLine1-startLine1);
    builder.append(" +").append(startLine2+1).append(",").append(endLine2-startLine2).append(" @@").append(lineSeparator);
    writer.append(builder.toString());
  }

  private static void writeLine(final Writer writer, final String line, final char prefix) throws IOException {
    writer.write(prefix);
    writer.write(line);
  }
}