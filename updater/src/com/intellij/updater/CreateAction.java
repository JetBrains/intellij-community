package com.intellij.updater;

import java.io.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CreateAction extends PatchAction {
  public CreateAction(Patch patch, String path) {
    super(patch, path, Digester.INVALID);
  }

  public CreateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    Runner.logger.info("building PatchFile");
    ZipEntry entry = new ZipEntry(myPath);
    patchOutput.putNextEntry(entry);
    if (!newerFile.isDirectory()) {
      writeExecutableFlag(patchOutput, newerFile);
      writeSymlinkFlag(patchOutput, newerFile);
      if (isSymlink(newerFile)) {
        patchOutput.write(getSymlinkTarget(newerFile).getBytes());
      }
      else {
        Utils.copyFileToStream(newerFile, patchOutput);
      }
    }

    patchOutput.closeEntry();
  }

  private static String getSymlinkTarget(File file) throws IOException {
    String target = file.getCanonicalPath();
    String link = file.getAbsolutePath();
    String[] segments1 = link.split(Pattern.quote(File.separator));
    String[] segments2 = target.split(Pattern.quote(File.separator));

    int len1 = segments1.length;
    int len2 = segments2.length;
    int len = Math.min(len1, len2);
    int start = 0;
    for (; start < len; start++) {
      if (!segments1[start].equals(segments2[start])) {
        break;
      }
    }

    StringBuilder result = new StringBuilder();
    for (int i = start; i < len1 - 1; i++) {
      result.append("..").append(File.separator);
    }
    while (start < len2) {
      result.append(segments2[start]);
      if (++start < len2) {
        result.append(File.separator);
      }
    }

    return result.toString();
  }

  @Override
  public ValidationResult validate(File toDir) {
    File toFile = getFile(toDir);
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.CREATE);
    if (result != null) return result;

    if (toFile.exists()) {
      ValidationResult.Option[] options = myPatch.isStrict()
                                          ? new ValidationResult.Option[]{ValidationResult.Option.REPLACE}
                                          : new ValidationResult.Option[]{ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP};
      return new ValidationResult(ValidationResult.Kind.CONFLICT, myPath, toFile,
                                  ValidationResult.Action.CREATE,
                                  ValidationResult.ALREADY_EXISTS_MESSAGE, options);
    }
    return null;
  }

  @Override
  protected boolean isModified(File toFile) throws IOException {
    return false;
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    prepareToWriteFile(toFile);

    ZipEntry entry = Utils.getZipEntry(patchFile, myPath);
    if (entry.isDirectory()) {
      if (!toFile.mkdir()) {
        throw new IOException("Unable to create directory " + myPath);
      }
    } else {
      InputStream in = Utils.findEntryInputStreamForEntry(patchFile, entry);
      try {
        boolean executable = readFlag(in);
        boolean link = readFlag(in);
        if (link) {
          ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
          Utils.copyStream(in, bytesOut);
          Runtime.getRuntime().exec("ln -s " + bytesOut.toString() + " " + toFile.getAbsolutePath());
        }
        else {
          Utils.copyStreamToFile(in, toFile);
        }
        Utils.setExecutable(toFile, executable);
      }
      finally {
        in.close();
      }
    }
  }

  private static void prepareToWriteFile(File file) throws IOException {
    if (file.exists()) {
      Utils.delete(file);
      return;
    }

    while (file != null && !file.exists()) {
      file = file.getParentFile();
    }
    if (file != null && !file.isDirectory()) {
      Utils.delete(file);
    }
  }

  @Override
  protected void doBackup(File toFile, File backupFile) {
    // do nothing
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    Utils.delete(toFile);
  }
}
