/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import junit.framework.TestCase;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class RunnerAdditionalTest extends TestCase {
  private final LinkedList<File> myFiles = new LinkedList<File>();
  private final ArrayList<NamePair> myFileNames = new ArrayList<NamePair>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFiles.clear();
    myFileNames.clear();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();

    while (!myFiles.isEmpty()) {
      remove(myFiles.removeLast());
    }
  }

  public void testUpdater1() throws Exception {
    File dir1 = add(createTempDir("old", ".dir"));
    File dir2 = add(createTempDir("new", ".dir"));
    File f3 = add(createFileContent(dir1, "something.txt", "Content version 1"));
    File f4 = add(createFileContent(dir2, "something.txt", "Content version 2"));
    File inputJar5 = add(File.createTempFile("input", ".jar"));
    File tmpPatch6 = add(File.createTempFile("temp", ".patch"));
    final File patch7 = add(File.createTempFile("output", ".jar"));

    createEmptyJar(inputJar5);

    //noinspection ResultOfMethodCallIgnored
    patch7.delete();
    assertFalse(patch7.exists());

    MockUpdaterUI ui1 = new MockUpdaterUI();
    Runner.createImpl("1.2",                    //oldBuildDesc
                      "1.3",                    //newBuildDesc
                      dir1.getAbsolutePath(),   //oldFolder
                      dir2.getAbsolutePath(),   //newFolder
                      patch7.getAbsolutePath(), //outPatchJar
                      tmpPatch6,                //tempPatchFile
                      new ArrayList<String>(),  //ignoredFiles
                      new ArrayList<String>(),  //criticalFiles
                      new ArrayList<String>(),  //optionalFiles
                      ui1,                      //ui
                      inputJar5);               //resolvedJar

    assertEquals(
      "[Start   ] Calculating difference...\n" +
      "[Status  ] something.txt\n" +
      "[Status  ] something.txt\n" +
      "[Start   ] Preparing actions...\n" +
      "[Status  ] something.txt\n" +
      "[Start   ] Creating the patch file 'file-6_temp.patch'...\n" +
      "[Status  ] Packing something.txt\n" +
      "[Start   ] Packing jar file 'file-7_output.jar'...\n" +
      "[Start   ] Cleaning up...\n" +
      "[Indeterminate Progress]\n",
      ui1.toString());
    assertTrue(patch7.exists());

    File dir8 = add(createTempDir("extracted", ".dir"));
    File f9 = add(createFileContent(dir8, "something.txt", "Content version 1"));
    assertTrue(f9.exists());

    MockUpdaterUI ui2 = new MockUpdaterUI();
    Runner.doInstallImpl(ui2,
                         dir8.getAbsolutePath(),
                         new Runner.IJarResolver() {
                           @Override
                           public File resolveJar() throws IOException {
                             return patch7;
                           }
                         });
    assertEquals(
      "[Start   ] Extracting patch file...\n" +
      "[Indeterminate Progress]\n" +
      "[Start   ] Validating installation...\n" +
      "[Status  ] something.txt\n" +
      "[Progress] 100\n" +
      "[Start   ] Backing up files...\n" +
      "[Status  ] something.txt\n" +
      "[Progress] 100\n" +
      "[Start   ] Applying patch...\n" +
      "[Status  ] something.txt\n" +
      "[Progress] 100\n" +
      "[Start   ] Cleaning up...\n" +
      "[Indeterminate Progress]\n",
      ui2.toString());
    assertEquals("Content version 2", getFileContent(f9));
  }

  //---- utilities -----

  private File createTempDir(String prefix, String suffix) throws IOException {
    File d = File.createTempFile(prefix, suffix);
    if (!d.delete()) throw new IOException("Failed to delete directory " + d.getAbsolutePath());
    if (!d.mkdirs()) throw new IOException("Failed to mkdirs " + d.getAbsolutePath());
    return d;
  }

  private static void createEmptyJar(File jar) throws IOException {
    FileOutputStream outStream = new FileOutputStream(jar);
    try {
      ZipOutputWrapper out = new ZipOutputWrapper(outStream);

      // zip file can't be empty, add one dummy entry
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      baos.write("dummy entry".getBytes("UTF-8"));
      out.zipBytes("dummy.txt", baos);

      out.finish();
    } finally {
      outStream.close();
    }
  }

  private File add(File f) {
    myFiles.add(f);

    String unique = f.getName().replaceAll("[^a-zA-Z.]", "");
    NamePair fromTo = new NamePair(f.getAbsolutePath(),
                                   (f.isDirectory() ? "dir-" : "file-") + (myFileNames.size() + 1) + '_' + unique);
    myFileNames.add(fromTo);
    Collections.sort(myFileNames);
    return f;
  }

  private String getFileContent(File file) throws IOException {
    if (!file.exists()) throw new IOException("File not found, expected file: " + replaceFileNames(file.getAbsolutePath()));
    BufferedReader br = new BufferedReader(new FileReader(file));
    try {
      return br.readLine();
    } finally {
      br.close();
    }
  }

  private static File createFileContent(File parentDir, String fileName, String fileContent) throws IOException {
    File f = new File(parentDir, fileName);
    OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"));
    try {
      fw.write(fileContent);
    } finally {
      fw.close();
    }
    return f;
  }

  private static void remove(File... files) {
    for (File f : files) {
      if (f != null && f.exists()) {
        if (!f.delete()) {
          f.deleteOnExit();
        }
      }
    }
  }

  private String replaceFileNames(String str) {
    for (NamePair name : myFileNames) {
      str = str.replace(name.getFrom(), name.getTo());
    }
    return str;
  }

  /**
   * A list of from->to name pairs, ordered by descending from
   * (to get the longer ones first.)
   */
  private static class NamePair implements Comparable<NamePair> {
    private final String myFrom;
    private final String myTo;

    public NamePair(String from, String to) {
      myFrom = from;
      myTo = to;
    }

    public String getFrom() {
      return myFrom;
    }

    public String getTo() {
      return myTo;
    }

    @Override
    public int compareTo(NamePair n2) {
      return -1 * this.getFrom().compareTo(n2.getFrom());
    }
  }

  /**
   * Mock UpdaterUI that dumps all the text to a string buffer, which can be
   * grabbed using toString(). It also replaces all the filenames using the
   * provided name pair list.
   */
  private class MockUpdaterUI implements UpdaterUI {
    private final StringBuilder myOutput = new StringBuilder();

    private MockUpdaterUI() {
    }

    @Override
    public void startProcess(String title) {
      title = replaceFileNames(title);
      myOutput.append("[Start   ] ").append(title).append('\n');
    }

    @Override
    public void setProgress(int percentage) {
      myOutput.append("[Progress] ").append(percentage).append('\n');
    }

    @Override
    public void setProgressIndeterminate() {
      myOutput.append("[Indeterminate Progress]\n");
    }

    @Override
    public void setStatus(String status) {
      status = replaceFileNames(status);
      myOutput.append("[Status  ] ").append(status).append('\n');
    }

    @Override
    public void showError(Throwable e) {
      myOutput.append("[Error   ] ").append(e.toString()).append('\n');
    }

    @Override
    public void checkCancelled() throws OperationCancelledException {
      // no-op
    }

    @Override
    public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException {
      return Collections.emptyMap();
    }

    @Override
    public String toString() {
      return myOutput.toString();
    }
  }
}
