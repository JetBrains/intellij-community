package com.intellij.updater;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public abstract class PatchAction {
  protected String myPath;
  protected long myChecksum;
  private boolean isCritical;
  private boolean isOptional;
  protected transient Patch myPatch;

  public PatchAction(Patch patch, String path, long checksum) {
    myPatch = patch;
    myChecksum = checksum;
    myPath = path;
  }

  public PatchAction(Patch patch, DataInputStream in) throws IOException {
    myPatch = patch;
    myPath = in.readUTF();
    myChecksum = in.readLong();
    isCritical = in.readBoolean();
    isOptional = in.readBoolean();
  }

  public void write(DataOutputStream out) throws IOException {
    out.writeUTF(myPath);
    out.writeLong(myChecksum);
    out.writeBoolean(isCritical);
    out.writeBoolean(isOptional);
  }

  public String getPath() {
    return myPath;
  }

  protected static void writeExecutableFlag(OutputStream out, File file) throws IOException {
    out.write(file.canExecute() ? 1 : 0);
  }

  protected static boolean readExecutableFlag(InputStream in) throws IOException {
    return in.read() == 1;
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
      for (NativeFileManager.Process process : NativeFileManager.getProcessesUsing(file)) {
        process.terminate();
      }
    }
    return doShouldApply(toDir);
  }

  protected boolean doShouldApply(File toDir) {
    return true;
  }

  protected abstract ValidationResult validate(File toDir) throws IOException;

  protected ValidationResult doValidateAccess(File toFile, ValidationResult.Action action) {
    if (!toFile.exists()) return null;
    if (toFile.isDirectory()) return null;
    ValidationResult result = validateProcessLock(toFile, action);
    if (result != null) {
      return result;
    }
    if (toFile.canRead() && toFile.canWrite() && isWritable(toFile)) return null;
    return new ValidationResult(ValidationResult.Kind.ERROR,
                                myPath,
                                action,
                                ValidationResult.ACCESS_DENIED_MESSAGE,
                                myPatch.isStrict() ? ValidationResult.Option.NONE : ValidationResult.Option.IGNORE);
  }

  private boolean isWritable(File toFile) {
    try {
      FileOutputStream s = new FileOutputStream(toFile, true);
      FileChannel ch = s.getChannel();
      try {
        FileLock lock = ch.tryLock();
        if (lock == null) return false;
        lock.release();
      }
      finally {
        ch.close();
        s.close();
      }
      return true;
    }
    catch (OverlappingFileLockException e) {
      Runner.printStackTrace(e);
      return false;
    }
    catch (IOException e) {
      Runner.printStackTrace(e);
      return false;
    }
  }

  private ValidationResult validateProcessLock(File toFile, ValidationResult.Action action) {
    List<NativeFileManager.Process> processes = NativeFileManager.getProcessesUsing(toFile);
    if (processes.size() > 0) {
      Iterator<NativeFileManager.Process> it = processes.iterator();
      String message = "Locked by: " + it.next().name;
      while (it.hasNext()) {
        message += ", " + it.next().name;
      }
      return new ValidationResult(ValidationResult.Kind.ERROR,
                                  myPath,
                                  action,
                                  message,
                                  ValidationResult.Option.KILL_PROCESS);
    }
    return null;
  }

  protected ValidationResult doValidateNotChanged(File toFile, ValidationResult.Kind kind, ValidationResult.Action action)
    throws IOException {
    if (toFile.exists()) {
      if (isModified(toFile)) {
        ValidationResult.Option[] options;
        if (myPatch.isStrict()) {
          if (isCritical) {
            options = new ValidationResult.Option[]{ ValidationResult.Option.REPLACE };
          }
          else {
            options = new ValidationResult.Option[]{ ValidationResult.Option.NONE };
          }
        } else {
          if (isCritical) {
            options = new ValidationResult.Option[]{ ValidationResult.Option.REPLACE, ValidationResult.Option.IGNORE };
          }
          else {
            options = new ValidationResult.Option[]{ ValidationResult.Option.IGNORE };
          }
        }
        return new ValidationResult(kind,
                                    myPath,
                                    action,
                                    ValidationResult.MODIFIED_MESSAGE,
                                    options);
      }
    }
    else if (!isOptional) {
      return new ValidationResult(kind,
                                  myPath,
                                  action,
                                  ValidationResult.ABSENT_MESSAGE,
                                  myPatch.isStrict() ? ValidationResult.Option.NONE : ValidationResult.Option.IGNORE);
    }
    return null;
  }

  protected boolean isModified(File toFile) throws IOException {
    return myChecksum == Digester.INVALID || myChecksum != myPatch.digestFile(toFile, myPatch.isNormalized());
  }

  public void apply(ZipFile patchFile, File backupDir, File toDir) throws IOException {
    doApply(patchFile, backupDir, getFile(toDir));
  }

  protected abstract void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException;

  public void backup(File toDir, File backupDir) throws IOException {
    doBackup(getFile(toDir), getFile(backupDir));
  }

  protected abstract void doBackup(File toFile, File backupFile) throws IOException;

  public void revert(File toDir, File backupDir) throws IOException {
    doRevert(getFile(toDir), getFile(backupDir));
  }

  protected abstract void doRevert(File toFile, File backupFile) throws IOException;

  protected File getFile(File baseDir) {
    return new File(baseDir, myPath);
  }

  public boolean isCritical() {
    return isCritical;
  }

  public void setCritical(boolean critical) {
    isCritical = critical;
  }

  public boolean isOptional() {
    return isOptional;
  }

  public void setOptional(boolean optional) {
    isOptional = optional;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myPath + ", " + myChecksum + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PatchAction that = (PatchAction)o;

    if (isCritical != that.isCritical) return false;
    if (isOptional != that.isOptional) return false;
    if (myChecksum != that.myChecksum) return false;
    if (myPath != null ? !myPath.equals(that.myPath) : that.myPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPath != null ? myPath.hashCode() : 0;
    result = 31 * result + (int)(myChecksum ^ (myChecksum >>> 32));
    result = 31 * result + (isCritical ? 1 : 0);
    result = 31 * result + (isOptional ? 1 : 0);
    return result;
  }
}
