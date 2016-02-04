package com.intellij.updater;

import com.nothome.delta.Delta;
import com.nothome.delta.GDiffPatcher;
import ie.wombat.jbdiff.JBDiff;
import ie.wombat.jbdiff.JBPatch;

import java.io.*;

public abstract class DiffAlgorithm {
  public abstract void writeDiff(InputStream oldFileIn, InputStream newFileIn, OutputStream diffFileOut) throws IOException;
  public abstract void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException;

  public abstract void applyDiff(InputStream patchInput, File oldFileIn, OutputStream toFileOut) throws IOException;
  public abstract void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException;

  public abstract int getId();

  public static DiffAlgorithm getAlgorithmForId(int type) {
    switch (type) {
      case 0:
        return new DiffAlgorithm.NullDiffAlgorithm();
      case 1:
        return new DiffAlgorithm.JBDiffAlgorithm();
      case 2:
        return new DiffAlgorithm.XDeltaAlgorithm();
      default:
        return null;
    }
  }

  public static DiffAlgorithm determineDiffAlgorithm(File olderFile, boolean isCritical, long largeFileCutoff) {
    if (!isCritical && olderFile != null && olderFile.length() > largeFileCutoff) {
      return new DiffAlgorithm.XDeltaAlgorithm();
    }
    else if (isCritical) {
      return new DiffAlgorithm.NullDiffAlgorithm();
    }
    else {
      return new DiffAlgorithm.JBDiffAlgorithm();
    }
  }

  private static class JBDiffAlgorithm extends DiffAlgorithm {

    @Override
    public void writeDiff(InputStream oldFileIn, InputStream newFileIn, OutputStream diffFileOut) throws IOException {
      ByteArrayOutputStream diffOutput = new ByteArrayOutputStream();
      byte[] newerFileBuffer = JBDiff.bsdiff(oldFileIn, newFileIn, diffOutput);
      diffOutput.close();
      if (diffOutput.size() < newerFileBuffer.length) {
        diffFileOut.write(0);
        Utils.copyBytesToStream(diffOutput, diffFileOut);
      }
      else {
        // Actually use null algorithm
        diffFileOut.write(1);
        new NullDiffAlgorithm().writeDiff(oldFileIn, new ByteArrayInputStream(newerFileBuffer), diffFileOut);
      }
    }

    @Override
    public void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException {
      InputStream oldFileIn = null;
      BufferedInputStream newFileIn = null;
      try {
        oldFileIn = new BufferedInputStream(new FileInputStream(olderFile));
        newFileIn = new BufferedInputStream(new FileInputStream(newerFile));
        writeDiff(oldFileIn, newFileIn, patchOutput);
      }
      finally {
        if (oldFileIn != null) {
          oldFileIn.close();
        }
        if (newFileIn != null) {
          newFileIn.close();
        }
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
      int isNull = patchInput.read();
      if (isNull == 1) {
        new NullDiffAlgorithm().applyDiff(patchInput, oldFileIn, toFileOut);
      }
      else {
        JBPatch.bspatch(oldFileIn, toFileOut, patchInput);
      }
    }

    @Override
    public int getId() {
      return 1;
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
      throw new UnsupportedOperationException("XDelta can only apply diffs to files.");
    }

    @Override
    public int getId() {
      return 2;
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
      return 0;
    }
  }
}
