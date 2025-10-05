// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.intellij.updater.Runner.LOG;

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

  /**
   * See javadoc for {@link Runner#printUsage()} for details of this flag
   * <p>
   * If the file is critical, we store the full file in the patch instead of calculating the diff.
   */
  public boolean isCritical() {
    return (myFlags & CRITICAL) != 0;
  }

  public void setCritical(boolean critical) {
    if (critical) myFlags |= CRITICAL; else myFlags &= ~CRITICAL;
  }

  /** See javadoc for {@link Runner#printUsage()} for details of this flag */
  public boolean isOptional() {
    return (myFlags & OPTIONAL) != 0;
  }

  public void setOptional(boolean optional) {
    if (optional) myFlags |= OPTIONAL; else myFlags &= ~OPTIONAL;
  }

  /** See javadoc for {@link Runner#printUsage()} for details of this flag */
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
    if (checkWriteable) {
      String problem = isWritable(toFile.toPath());
      if (problem != null) {
        ValidationResult.Option[] options = {myPatch.isStrict() ? ValidationResult.Option.NONE : ValidationResult.Option.IGNORE};
        return new ValidationResult(ValidationResult.Kind.ERROR, getReportPath(), action, UpdaterUI.message("access.denied"), problem, options);
      }
    }
    return null;
  }

  private static String isWritable(Path path) {
    if (!Files.isReadable(path)) {
      return "not readable";
    }
    if (!Files.isWritable(path)) {
      return "not writable";
    }
    try (FileChannel ch = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND); FileLock lock = ch.tryLock()) {
      if (lock == null) {
        return "locked by another process";
      }
    }
    catch (OverlappingFileLockException | IOException e) {
      LOG.log(Level.WARNING, path.toString(), e);
      return "cannot lock: " + e.getMessage();
    }
    return null;
  }

  private ValidationResult validateProcessLock(File toFile, ValidationResult.Action action) {
    List<NativeFileManager.Process> processes = NativeFileManager.getProcessesUsing(toFile);
    if (processes.isEmpty()) return null;
    var message = UpdaterUI.message("file.locked", processes.stream().map(p -> "[" + p.pid + "] " + p.name).collect(Collectors.joining(", ")));
    return new ValidationResult(ValidationResult.Kind.ERROR, getReportPath(), action, message, ValidationResult.Option.KILL_PROCESS);
  }

  protected ValidationResult doValidateNotChanged(File toFile, ValidationResult.Action action) throws IOException {
    if (!isOptional()) {
      if (!toFile.exists()) {
        ValidationResult.Option[] options = calculateOptions();
        ValidationResult.Kind kind = isCritical() ? ValidationResult.Kind.CONFLICT : ValidationResult.Kind.ERROR;
        return new ValidationResult(kind, getReportPath(), action, UpdaterUI.message("file.absent"), options);
      }
      else if (isModified(toFile)) {
        ValidationResult.Option[] options = calculateOptions();
        String details = "expected 0x" + Long.toHexString(myChecksum) + ", actual 0x" + Long.toHexString(myPatch.digestFile(toFile));
        ValidationResult.Kind kind = isCritical() ? ValidationResult.Kind.CONFLICT : ValidationResult.Kind.ERROR;
        return new ValidationResult(kind, getReportPath(), action, UpdaterUI.message("file.modified"), details, options);
      }
    }

    return null;
  }

  private ValidationResult.Option[] calculateOptions() {
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
        options = new ValidationResult.Option[]{ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP};
      }
      else {
        options = new ValidationResult.Option[]{ValidationResult.Option.IGNORE};
      }
    }
    return options;
  }

  protected boolean isModified(File toFile) throws IOException {
    return myChecksum == Digester.INVALID || myChecksum != myPatch.digestFile(toFile);
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
    return myChecksum == that.myChecksum && Objects.equals(myPath, that.myPath);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(myPath);
    result = 31 * result + Long.hashCode(myChecksum);
    return result;
  }
}
