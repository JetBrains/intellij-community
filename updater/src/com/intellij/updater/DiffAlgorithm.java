/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import com.intellij.updater.Utils.OpenByteArrayOutputStream;
import com.nothome.delta.Delta;
import com.nothome.delta.GDiffPatcher;
import ie.wombat.jbdiff.JBDiff;
import ie.wombat.jbdiff.JBPatch;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public abstract class DiffAlgorithm {
  private static final byte RAW = 0;
  private static final byte COMPRESSED = 1;
  private static final byte X_DELTA = 2;

  public abstract void writeDiff(InputStream oldFileIn, InputStream newFileIn, OutputStream diffFileOut) throws IOException;
  public abstract void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException;

  public abstract void applyDiff(InputStream patchInput, File oldFileIn, OutputStream toFileOut) throws IOException;
  public abstract void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException;

  public abstract int getId();

  public static DiffAlgorithm getAlgorithmForId(int type) {
    switch (type) {
      case RAW:
        return new DiffAlgorithm.NullDiffAlgorithm();
      case COMPRESSED:
        return new DiffAlgorithm.JBDiffAlgorithm();
      case X_DELTA:
        return new DiffAlgorithm.XDeltaAlgorithm();
      default:
        return null;
    }
  }

  public static DiffAlgorithm determineDiffAlgorithm(File olderFile, boolean isCritical, long largeFileCutoff) {
      return determineDiffAlgorithm(olderFile, null, isCritical, largeFileCutoff);
  }

  public static DiffAlgorithm determineDiffAlgorithm(File olderFile, File newerFile, boolean isCritical, long largeFileCutoff) {
    if (isCritical) {
      return new DiffAlgorithm.NullDiffAlgorithm();
    }
    else if ((olderFile != null && olderFile.length() > largeFileCutoff) ||
             (newerFile != null && newerFile.length() > largeFileCutoff)) {
      return new DiffAlgorithm.XDeltaAlgorithm();
    }
    else {
      return new DiffAlgorithm.JBDiffAlgorithm();
    }
  }

  private static class JBDiffAlgorithm extends DiffAlgorithm {

    @Override
    public void writeDiff(InputStream oldFileIn, InputStream newFileIn, OutputStream diffFileOut) throws IOException {
      ByteArrayOutputStream diffOutput = new OpenByteArrayOutputStream();
      byte[] newerFileBuffer = JBDiff.bsdiff(oldFileIn, newFileIn, diffOutput);
      diffOutput.close();

      if (diffOutput.size() < newerFileBuffer.length) {
        diffFileOut.write(COMPRESSED);
        diffOutput.writeTo(diffFileOut);
      }
      else {
        diffFileOut.write(RAW);
        Utils.writeBytes(newerFileBuffer, newerFileBuffer.length, diffFileOut);
      }
    }

    @Override
    public void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException {
      try (InputStream oldFileIn = new BufferedInputStream(new FileInputStream(olderFile));
           InputStream newFileIn = new BufferedInputStream(new FileInputStream(newerFile))) {
        writeDiff(oldFileIn, newFileIn, patchOutput);
      }
    }

    @Override
    public void applyDiff(InputStream patchInput, File olderFile, OutputStream toFileOut) throws IOException {
      InputStream oldFileIn = null;
      try {
        oldFileIn = new BufferedInputStream(new FileInputStream(olderFile));
        applyDiff(patchInput, oldFileIn, toFileOut);
      }
      finally {
        if (oldFileIn != null) {
          oldFileIn.close();
        }
      }
    }

    @Override
    public void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException {
      int type = patchInput.read();
      if (type == COMPRESSED) {
        JBPatch.bspatch(oldFileIn, toFileOut, patchInput);
      }
      else if (type == RAW) {
        new NullDiffAlgorithm().applyDiff(patchInput, oldFileIn, toFileOut);
      }
      else {
        throw new IOException("Corrupted patch");
      }
    }

    @Override
    public int getId() {
      return COMPRESSED;
    }
  }

  private static class XDeltaAlgorithm extends DiffAlgorithm {

    @Override
    public void writeDiff(InputStream oldFileIn, InputStream newFileIn, OutputStream diffFileOut) throws IOException {
      throw new UnsupportedOperationException("XDelta can only diff files.");
    }

    @Override
    public void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException {
      Delta delta = new Delta();
      delta.compute(olderFile, newerFile, patchOutput);
    }

    @Override
    public void applyDiff(InputStream patchInput, File oldFileIn, OutputStream toFileOut) throws IOException {
      GDiffPatcher patcher = new GDiffPatcher();
      patcher.patch(oldFileIn, patchInput, toFileOut);
    }

    @Override
    public void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException {
      Path temp = null;
      try {
        temp = Files.createTempFile("updater", "diff");
        Files.copy(oldFileIn, temp, StandardCopyOption.REPLACE_EXISTING);
        applyDiff(patchInput, temp.toFile(), toFileOut);
      }
      finally {
        Files.delete(temp);
      }
    }

    @Override
    public int getId() {
      return X_DELTA;
    }
  }

  private static class NullDiffAlgorithm extends DiffAlgorithm {
    @Override
    public void writeDiff(InputStream oldFileIn, InputStream newFileIn, OutputStream diffFileOut) throws IOException {
      Utils.copyStream(newFileIn, diffFileOut);
    }

    @Override
    public void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException {
      BufferedInputStream newFileIn = null;
      try {
        newFileIn = new BufferedInputStream(new FileInputStream(newerFile));
        writeDiff(null, newFileIn, patchOutput);
      }
      finally {
        if (newFileIn != null) {
          newFileIn.close();
        }
      }
    }

    @Override
    public void applyDiff(InputStream patchInput, File oldFileIn, OutputStream toFileOut) throws IOException {
      applyDiff(patchInput, (InputStream)null, toFileOut);
    }

    @Override
    public void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException {
      Utils.copyStream(patchInput, toFileOut);
    }

    @Override
    public int getId() {
      return RAW;
    }
  }
}
