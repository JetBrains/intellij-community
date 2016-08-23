package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class UpdateZipAction extends BaseUpdateAction {
  Set<String> myFilesToCreate;
  Set<String> myFilesToUpdate;
  Set<String> myFilesToDelete;

  public UpdateZipAction(Patch patch, String path, String source, long checksum, boolean move) {
    super(patch, path, source, checksum, move);
  }

  // test support
  public UpdateZipAction(Patch patch, String path,
                         Collection<String> filesToCreate,
                         Collection<String> filesToUpdate,
                         Collection<String> filesToDelete,
                         long checksum) {
    super(patch, path, path, checksum, false);
    myFilesToCreate = new HashSet<>(filesToCreate);
    myFilesToUpdate = new HashSet<>(filesToUpdate);
    myFilesToDelete = new HashSet<>(filesToDelete);
  }

  public UpdateZipAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);

    int count = in.readInt();
    myFilesToCreate = new HashSet<>(count);
    while (count-- > 0) {
      myFilesToCreate.add(in.readUTF());
    }

    count = in.readInt();
    myFilesToUpdate = new HashSet<>(count);
    while (count-- > 0) {
      myFilesToUpdate.add(in.readUTF());
    }

    count = in.readInt();
    myFilesToDelete = new HashSet<>(count);
    while (count-- > 0) {
      myFilesToDelete.add(in.readUTF());
    }
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    super.write(out);

    out.writeInt(myFilesToCreate.size());
    for (String each : myFilesToCreate) {
      out.writeUTF(each);
    }

    out.writeInt(myFilesToUpdate.size());
    for (String each : myFilesToUpdate) {
      out.writeUTF(each);
    }

    out.writeInt(myFilesToDelete.size());
    for (String each : myFilesToDelete) {
      out.writeUTF(each);
    }
  }

  @Override
  protected boolean doCalculate(File olderFile, File newerFile) throws IOException {
    final Map<String, Long> oldCheckSums = new HashMap<>();
    final Map<String, Long> newCheckSums = new HashMap<>();

    processZipFile(olderFile, new Processor() {
      public void process(ZipEntry entry, InputStream in) throws IOException {
        oldCheckSums.put(entry.getName(), Digester.digestStream(in));
      }
    });

    processZipFile(newerFile, new Processor() {
      public void process(ZipEntry entry, InputStream in) throws IOException {
        newCheckSums.put(entry.getName(), Digester.digestStream(in));
      }
    });

    DiffCalculator.Result diff = DiffCalculator.calculate(oldCheckSums, newCheckSums, new LinkedList<>(), false);

    myFilesToCreate = diff.filesToCreate.keySet();
    myFilesToUpdate = diff.filesToUpdate.keySet();
    myFilesToDelete = diff.filesToDelete.keySet();

    return !(myFilesToCreate.isEmpty() && myFilesToUpdate.isEmpty() && myFilesToDelete.isEmpty());
  }

  @Override
  public void doBuildPatchFile(final File olderFile, final File newerFile, final ZipOutputStream patchOutput) throws IOException {
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      new ZipFile(newerFile).close();
    }
    catch (IOException e) {
      Runner.logger.error("Corrupted target file: " + newerFile);
      Runner.printStackTrace(e);
      throw new IOException("Corrupted target file: " + newerFile, e);
    }

    final Set<String> filesToProcess = new HashSet<>(myFilesToCreate);
    filesToProcess.addAll(myFilesToUpdate);
    if (filesToProcess.isEmpty()) return;

    final ZipFile olderZip;
    try {
      olderZip = new ZipFile(olderFile);
    }
    catch (IOException e) {
      Runner.logger.error("Corrupted source file: " + olderFile);
      Runner.printStackTrace(e);
      throw new IOException("Corrupted source file: " + olderFile, e);
    }

    try {
      processZipFile(newerFile, new Processor() {
        public void process(ZipEntry newerEntry, InputStream newerEntryIn) throws IOException {
          if (newerEntry.isDirectory()) return;
          String name = newerEntry.getName();
          if (!filesToProcess.contains(name)) return;

          try {
            patchOutput.putNextEntry(new ZipEntry(myPath + "/" + name));
            InputStream olderEntryIn = Utils.findEntryInputStream(olderZip, name);
            if (olderEntryIn == null) {
              Utils.copyStream(newerEntryIn, patchOutput);
            }
            else {
              writeDiff(olderEntryIn, newerEntryIn, patchOutput);
            }
            patchOutput.closeEntry();
          }
          catch (IOException e) {
            Runner.logger.error("Error building patch for .zip entry " + name);
            Runner.printStackTrace(e);
            throw new IOException("Error building patch for .zip entry " + name, e);
          }
        }
      });
    }
    finally {
      olderZip.close();
    }
  }

  @Override
  protected void doApply(final ZipFile patchFile, File backupDir, File toFile) throws IOException {
    File temp = Utils.createTempFile();
    FileOutputStream fileOut = new FileOutputStream(temp);
    try {
      final ZipOutputWrapper out = new ZipOutputWrapper(fileOut);
      out.setCompressionLevel(0);

      processZipFile(getSource(backupDir), new Processor() {
        @Override
        public void process(ZipEntry entry, InputStream in) throws IOException {
          String path = entry.getName();
          if (myFilesToDelete.contains(path)) return;

          if (myFilesToUpdate.contains(path)) {
            OutputStream entryOut = out.zipStream(path);
            try {
              applyDiff(Utils.findEntryInputStream(patchFile, myPath + "/" + path), in, entryOut);
            }
            finally {
              entryOut.close();
            }
          }
          else {
            out.zipEntry(entry, in);
          }
        }
      });

      for (String each : myFilesToCreate) {
        InputStream in = Utils.getEntryInputStream(patchFile, myPath + "/" + each);
        try {
          out.zipEntry(each, in);
        }
        finally {
          in.close();
        }
      }

      out.finish();
    }
    finally {
      fileOut.close();
    }

    replaceUpdated(temp, toFile);
  }

  private static void processZipFile(File file, Processor processor) throws IOException {
    ZipInputStream in = new ZipInputStream(new FileInputStream(file));
    try {
      ZipEntry inEntry;
      Set<String> processed = new HashSet<>();
      while ((inEntry = in.getNextEntry()) != null) {
        if (inEntry.isDirectory()) continue;
        if (processed.contains(inEntry.getName())) {
          throw new IOException("Duplicate entry '" + inEntry.getName() + "' in " + file.getPath());
        }
        //noinspection IOResourceOpenedButNotSafelyClosed
        processor.process(inEntry, new BufferedInputStream(in));
        processed.add(inEntry.getName());
      }
    }
    finally {
      in.close();
    }
  }

  private interface Processor {
    void process(ZipEntry entry, InputStream in) throws IOException;
  }

  @Override
  public String toString() {
    return super.toString() + myFilesToCreate + " " + myFilesToUpdate + " " + myFilesToDelete;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    UpdateZipAction that = (UpdateZipAction)o;

    if (myFilesToCreate != null ? !myFilesToCreate.equals(that.myFilesToCreate) : that.myFilesToCreate != null) return false;
    if (myFilesToUpdate != null ? !myFilesToUpdate.equals(that.myFilesToUpdate) : that.myFilesToUpdate != null) return false;
    if (myFilesToDelete != null ? !myFilesToDelete.equals(that.myFilesToDelete) : that.myFilesToDelete != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myFilesToCreate != null ? myFilesToCreate.hashCode() : 0);
    result = 31 * result + (myFilesToUpdate != null ? myFilesToUpdate.hashCode() : 0);
    result = 31 * result + (myFilesToDelete != null ? myFilesToDelete.hashCode() : 0);
    return result;
  }
}
