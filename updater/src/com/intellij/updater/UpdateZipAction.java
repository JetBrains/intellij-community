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

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class UpdateZipAction extends BaseUpdateAction {
  private Set<String> myFilesToCreate;
  private Set<String> myFilesToUpdate;
  private Set<String> myFilesToDelete;

  public UpdateZipAction(Patch patch, String path, String source, long checksum) {
    super(patch, path, source, checksum, false);
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

  /* for tests */
  UpdateZipAction(Patch patch, String path, Collection<String> toCreate, Collection<String> toUpdate, Collection<String> toDelete, long checksum) {
    this(patch, path, path, toCreate, toUpdate, toDelete, checksum);
  }

  /* for tests */
  UpdateZipAction(Patch patch, String path, String source, Collection<String> toCreate, Collection<String> toUpdate, Collection<String> toDelete, long checksum) {
    super(patch, path, source, checksum, false);
    myFilesToCreate = new HashSet<>(toCreate);
    myFilesToUpdate = new HashSet<>(toUpdate);
    myFilesToDelete = new HashSet<>(toDelete);
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
    Map<String, Long> oldCheckSums = new HashMap<>(), newCheckSums = new HashMap<>();
    processZipFile(olderFile, (entry, in) -> oldCheckSums.put(entry.getName(), Digester.digestStream(in)));
    processZipFile(newerFile, (entry, in) -> newCheckSums.put(entry.getName(), Digester.digestStream(in)));

    DiffCalculator.Result diff = DiffCalculator.calculate(oldCheckSums, newCheckSums);
    myFilesToCreate = diff.filesToCreate.keySet();
    myFilesToUpdate = diff.filesToUpdate.keySet();
    myFilesToDelete = diff.filesToDelete.keySet();

    return !(myFilesToCreate.isEmpty() && myFilesToUpdate.isEmpty() && myFilesToDelete.isEmpty());
  }

  @Override
  public void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    int changes = myFilesToCreate.size() + myFilesToUpdate.size();
    if (changes == 0) return;
    Set<String> filesToProcess = new HashSet<>(changes);
    filesToProcess.addAll(myFilesToCreate);
    filesToProcess.addAll(myFilesToUpdate);

    try (ZipFile olderZip = new ZipFile(olderFile)) {
      processZipFile(newerFile, (newerEntry, newerEntryIn) -> {
        String name = newerEntry.getName();
        if (filesToProcess.contains(name)) {
          try {
            patchOutput.putNextEntry(new ZipEntry(getPath() + "/" + name));
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
            Runner.logger().error("Error building patch for .zip entry " + name);
            Runner.printStackTrace(e);
            throw new IOException("Error building patch for .zip entry " + name, e);
          }
        }
      });
    }
  }

  @Override
  protected void doApply(final ZipFile patchFile, File backupDir, File toFile) throws IOException {
    File temp = Utils.getTempFile(toFile.getName());
    //in case no backup is required
    File source = backupDir == null ? toFile : getSource(backupDir);

    try (ZipOutputWrapper out = new ZipOutputWrapper(new FileOutputStream(temp), 0)) {
      processZipFile(source, (entry, in) -> {
        String path = entry.getName();
        if (myFilesToUpdate.contains(path)) {
          try (OutputStream entryOut = out.zipStream(path)) {
            applyDiff(Utils.findEntryInputStream(patchFile, getPath() + "/" + path), in, entryOut);
          }
        }
        else if (!myFilesToDelete.contains(path)) {
          out.zipEntry(entry, in);
        }
      });

      for (String each : myFilesToCreate) {
        try (InputStream in = Utils.getEntryInputStream(patchFile, getPath() + "/" + each)) {
          out.zipEntry(each, in);
        }
      }

      out.finish();
    }

    replaceUpdated(temp, toFile);
  }

  private static void processZipFile(File file, Processor processor) throws IOException {
    ZipFile zip;
    try {
      zip = new ZipFile(file);
    }
    catch (IOException e) {
      throw new IOException("Corrupted file: " + file, e);
    }
    try {
      Set<String> processed = new HashSet<>();
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry inEntry = entries.nextElement();
        if (inEntry.isDirectory()) continue;
        if (processed.contains(inEntry.getName())) {
          throw new IOException("Duplicate entry '" + inEntry.getName() + "' in " + file.getPath());
        }
        try (InputStream in = new BufferedInputStream(zip.getInputStream(inEntry))) {
          processor.process(inEntry, new BufferedInputStream(in));
          processed.add(inEntry.getName());
        }
      }
    }
    finally {
      zip.close();
    }
  }

  @FunctionalInterface
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
    if (!super.equals(o)) return false;

    UpdateZipAction that = (UpdateZipAction)o;

    if (!Objects.equals(myFilesToCreate, that.myFilesToCreate)) return false;
    if (!Objects.equals(myFilesToUpdate, that.myFilesToUpdate)) return false;
    if (!Objects.equals(myFilesToDelete, that.myFilesToDelete)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(myFilesToCreate);
    result = 31 * result + Objects.hashCode(myFilesToDelete);
    result = 31 * result + Objects.hashCode(myFilesToUpdate);
    return result;
  }
}