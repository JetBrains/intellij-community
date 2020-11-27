// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public abstract class PatchAction {
  public enum FileType {
    REGULAR_FILE, EXECUTABLE_FILE, SYMLINK
  }

  private static final byte CRITICAL = 0x1;
  private static final byte OPTIONAL = 0x2;
  private static final byte STRICT = 0x4;

  protected final transient Patch myPatch;
  private final String myPath;
  private final long myChecksum;
  private byte myFlags;

  public PatchAction(Patch patch, String path, long checksum) {
    this(patch, path, checksum, (byte)0);
  }

  public PatchAction(Patch patch, DataInputStream in) throws IOException {
    this(patch, in.readUTF(), in.readLong(), in.readByte());
  }

  private PatchAction(Patch patch, String path, long checksum, byte flags) {
    myPatch = patch;
    myPath = path;
    myChecksum = checksum;
    myFlags = flags;
  }

  public void write(DataOutputStream out) throws IOException {
    out.writeUTF(myPath);
    out.writeLong(myChecksum);
    out.writeByte(myFlags);
  }

  public String getPath() {
    return myPath;
  }

  protected String getReportPath() {
    return myPath;
  }

  protected File getFile(File baseDir) {
    return new File(baseDir, myPath);
  }

  public long getChecksum() {
    return myChecksum;
  }

  public boolean isCritical() {
    return (myFlags & CRITICAL) != 0;
  }

  public void setCritical(boolean critical) {
    if (critical) myFlags |= CRITICAL; else myFlags &= ~CRITICAL;
  }

  public boolean isOptional() {
    return (myFlags & OPTIONAL) != 0;
  }

  public void setOptional(boolean optional) {
    if (optional) myFlags |= OPTIONAL; else myFlags &= ~OPTIONAL;
  }

  public boolean isStrict() {
    return (myFlags & STRICT) != 0;
  }

  public void setStrict(boolean strict) {
    if (strict) myFlags |= STRICT; else myFlags &= ~STRICT;
  }

  protected static FileType getFileType(File file) {
    if (Utils.isLink(file)) return FileType.SYMLINK;
    if (Utils.isExecutable(file)) return FileType.EXECUTABLE_FILE;
    return FileType.REGULAR_FILE;
  }

  protected static void writeFileType(OutputStream out, FileType type) throws IOException {
    out.write(type.ordinal());
  }

  protected static FileType readFileType(InputStream in) throws IOException {
    int value = in.read();
    FileType[] types = FileType.values();
    if (value < 0 || value >= types.length) throw new IOException("Stream format error");
    return types[value];
  }

  public boolean calculate(File olderDir, File newerDir) throws IOException {
    return doCalculate(getFile(olderDir), getFile(newerDir));
  }

  protected boolean doCalculate(File olderFile, File newerFile) throws IOException {
    return true;
  }

  public void buildPatchFile(File olderDir, File newerDir, ZipOutputStream patchOutput) throws IOException {
    doBuildPatchFile(getFile(olderDir), getFile(newerDir), patchOutput);
  }

  protected abstract void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException;

  public boolean shouldApply(File toDir, Map<String, ValidationResult.Option> options) {
    File file = getFile(toDir);
    ValidationResult.Option option = options.get(myPath);
    if (option == ValidationResult.Option.KEEP || option == ValidationResult.Option.IGNORE) return false;
    if (option == ValidationResult.Option.KILL_PROCESS) {
      NativeFileManager.getProcessesUsing(file).forEach(p -> p.terminate());
    }
    return doShouldApply(toDir);
  }

  protected boolean doShouldApply(File toDir) {
    return true;
  }

  protected abstract ValidationResult validate(File toDir) throws IOException;

  protected ValidationResult doValidateAccess(File toFile, ValidationResult.Action action, boolean checkWriteable) {
    if (!toFile.exists() || toFile.isDirectory()) return null;
    ValidationResult result = validateProcessLock(toFile, action);
    if (result != null) return result;
    if (!checkWriteable || isWritable(toFile.toPath())) return null;
    ValidationResult.Option[] options = {myPatch.isStrict() ? ValidationResult.Option.NONE : ValidationResult.Option.IGNORE};
    return new ValidationResult(ValidationResult.Kind.ERROR, getReportPath(), action, ValidationResult.ACCESS_DENIED_MESSAGE, options);
  }

  private static boolean isWritable(Path path) {
    if (!Files.isReadable(path)) {
      Runner.logger().warn("unreadable: " + path);
      return false;
    }
    if (!Files.isWritable(path)) {
      Runner.logger().warn("read-only: " + path);
      return false;
    }
    try (FileChannel ch = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND); FileLock lock = ch.tryLock()) {
      if (lock == null) {
        Runner.logger().warn("cannot lock: " + path);
        return false;
      }
    }
    catch (OverlappingFileLockException | IOException e) {
      Runner.logger().warn(path, e);
      return false;
    }
    return true;
  }

  private ValidationResult validateProcessLock(File toFile, ValidationResult.Action action) {
    List<NativeFileManager.Process> processes = NativeFileManager.getProcessesUsing(toFile);
    if (processes.isEmpty()) return null;
    String message = "Locked by: " + processes.stream().map(p -> p.name).collect(Collectors.joining(", "));
    return new ValidationResult(ValidationResult.Kind.ERROR, getReportPath(), action, message, ValidationResult.Option.KILL_PROCESS);
  }

  protected ValidationResult doValidateNotChanged(File toFile, ValidationResult.Action action) throws IOException {
    if (toFile.exists()) {
      if (isModified(toFile)) {
        ValidationResult.Option[] options;
        if (myPatch.isStrict() || isStrict()) {
          if (isCritical()) {
            options = new ValidationResult.Option[]{ValidationResult.Option.REPLACE};
          }
          else {
            options = new ValidationResult.Option[]{ValidationResult.Option.NONE};
          }
        }
        else {
          if (isCritical()) {
            options = new ValidationResult.Option[]{ValidationResult.Option.REPLACE, ValidationResult.Option.IGNORE};
          }
          else {
            options = new ValidationResult.Option[]{ValidationResult.Option.IGNORE};
          }
        }
        return new ValidationResult(ValidationResult.Kind.ERROR, getReportPath(), action, ValidationResult.MODIFIED_MESSAGE, options);
      }
    }
    else if (!isOptional()) {
      ValidationResult.Option[] options = {myPatch.isStrict() || isStrict() ? ValidationResult.Option.NONE : ValidationResult.Option.IGNORE};
      return new ValidationResult(ValidationResult.Kind.ERROR, getReportPath(), action, ValidationResult.ABSENT_MESSAGE, options);
    }

    return null;
  }

  protected boolean isModified(File toFile) throws IOException {
    return myChecksum == Digester.INVALID || myChecksum != myPatch.digestFile(toFile, myPatch.isNormalized());
  }

  public boolean mandatoryBackup() {
    return false;
  }

  public void backup(File toDir, File backupDir) throws IOException {
    doBackup(getFile(toDir), getFile(backupDir));
  }

  public void apply(ZipFile patchFile, File backupDir, File toDir) throws IOException {
    doApply(patchFile, backupDir, getFile(toDir));
  }

  public void revert(File toDir, File backupDir) throws IOException {
    doRevert(getFile(toDir), getFile(backupDir));
  }

  protected void doBackup(File toFile, File backupFile) throws IOException { }
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException { }
  protected void doRevert(File toFile, File backupFile) throws IOException { }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myPath + ", " + myChecksum + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PatchAction that = (PatchAction)o;

    if (myChecksum != that.myChecksum) return false;
    if (!Objects.equals(myPath, that.myPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(myPath);
    result = 31 * result + (int)(myChecksum ^ (myChecksum >>> 32));
    return result;
  }
}