/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import org.jetbrains.idea.svn.status.StatusType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/2/12
 * Time: 1:11 PM
 */
public interface TreeConflictData {
  Data[] ourAll = new Data[] {
    FileToFile.MINE_DELETE_THEIRS_EDIT, FileToFile.MINE_EDIT_THEIRS_DELETE, FileToFile.MINE_EDIT_THEIRS_MOVE,
    FileToFile.MINE_UNV_THEIRS_ADD, FileToFile.MINE_UNV_THEIRS_MOVE, FileToFile.MINE_MOVE_THEIRS_EDIT,
    FileToFile.MINE_MOVE_THEIRS_ADD,
    /*removed: DirToDir.MINE_EDIT_THEIRS_DELETE - no more a conflict since 1.7.7*/
    DirToDir.MINE_DELETE_THEIRS_EDIT, DirToDir.MINE_EDIT_THEIRS_MOVE,
    DirToDir.MINE_UNV_THEIRS_ADD, DirToDir.MINE_UNV_THEIRS_MOVE, DirToDir.MINE_MOVE_THEIRS_EDIT,
    DirToDir.MINE_MOVE_THEIRS_ADD,

    DirToFile.MINE_ADD_THEIRS_ADD, DirToFile.MINE_ADD_THEIRS_MOVE, DirToFile.MINE_UNV_THEIRS_ADD,
    DirToFile.MINE_UNV_THEIRS_MOVE, DirToFile.MINE_MOVE_THEIRS_ADD,

    FileToDir.MINE_ADD_THEIRS_ADD, FileToDir.MINE_ADD_THEIRS_MOVE, FileToDir.MINE_UNV_THEIRS_ADD,
    FileToDir.MINE_UNV_THEIRS_MOVE, FileToDir.MINE_MOVE_THEIRS_ADD};

  interface FileToFile {
    Data MINE_UNV_THEIRS_ADD = new Data("Index: added.txt\n" +
                                        "===================================================================\n" +
                                        "--- added.txt\t(revision )\n" +
                                        "+++ added.txt\t(revision )\n" +
                                        "@@ -0,0 +1,1 @@\n" +
                                        "+added text\n" +
                                        "\\ No newline at end of file\n",
                                        "added.txt", new FileData[]{new FileData("added.txt", "unversioned text", StatusType.STATUS_UNVERSIONED,
                                                                    StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                    false)});

    Data MINE_EDIT_THEIRS_DELETE = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision 358)\n" +
                                            "@@ -1,1 +0,0 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n", "root/source/s1.txt",
                                            new FileData[] {new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                                         StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false)});
    Data MINE_DELETE_THEIRS_EDIT = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision )\n" +
                                            "@@ -1,1 +1,1 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "+1*2*3\n" +
                                            "\\ No newline at end of file\n", "root/source/s1.txt",
                                            new FileData[] {new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                                                                         StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, false)});

    Data MINE_EDIT_THEIRS_MOVE = new Data("Index: root/source/s1.txt\n" +
                                          "===================================================================\n" +
                                          "--- root/source/s1.txt\t(revision 358)\n" +
                                          "+++ root/source/s1renamed.txt\t(revision )\n" +
                                          "@@ -1,0 +1,0 @@\n", "root/source/s1.txt",
                                          new FileData[] {new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                                       StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false)});

    Data MINE_UNV_THEIRS_MOVE = new Data("Index: root/source/s1.txt\n" +
                                          "===================================================================\n" +
                                          "--- root/source/s1.txt\t(revision 358)\n" +
                                          "+++ root/source/s1renamed.txt\t(revision )\n" +
                                          "@@ -1,0 +1,0 @@\n", "root/source/s1renamed.txt",
                                          new FileData[] {new FileData("root/source/s1renamed.txt", "1*2*3", StatusType.STATUS_UNVERSIONED,
                                                                       StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                       false)});
    Data MINE_MOVE_THEIRS_EDIT = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision )\n" +
                                            "@@ -1,1 +1,1 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "+1*2*3\n" +
                                            // conflict would be marked by svn on s1.txt, but here we put s1moved.txt, for change list manager to find the change
                                            "\\ No newline at end of file\n", "root/source/s1moved.txt",
                                            new FileData[] {new FileData("root/source/s1moved.txt", null, StatusType.STATUS_ADDED,
                                                                         StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, false, "root/source/s1.txt"),
                                              new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                                              StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, false)});
    Data MINE_MOVE_THEIRS_ADD = new Data("Index: root/source/s1moved.txt\n" +
                                         "===================================================================\n" +
                                         "--- root/source/s1moved.txt\t(revision )\n" +
                                         "+++ root/source/s1moved.txt\t(revision )\n" +
                                         "@@ -0,0 +1,1 @@\n" +
                                         "+added text\n" +
                                         "\\ No newline at end of file\n",
                                         "root/source/s1moved.txt",
                                         new FileData[] {new FileData("root/source/s1moved.txt", null, StatusType.STATUS_ADDED,
                                                  StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, false, "root/source/s1.txt"),
      new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                   StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, false)}) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root\\source\\s1.txt");
      }
    };
  }

  interface DirToDir {
    Data MINE_UNV_THEIRS_ADD = new Data("Index: addedDir/added.txt\n" +
                                        "===================================================================\n" +
                                        "--- addedDir/added.txt\t(revision )\n" +
                                        "+++ addedDir/added.txt\t(revision )\n" +
                                        "@@ -0,0 +1,1 @@\n" +
                                        "+added text\n" +
                                        "\\ No newline at end of file\n",
                                        "addedDir", new FileData[]{new FileData("addedDir", null, StatusType.STATUS_UNVERSIONED,
                                                                    StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                    true),
                                                                   new FileData("addedDir/unv.txt", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                                          StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                          false)});

    Data MINE_EDIT_THEIRS_DELETE = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision 358)\n" +
                                            "@@ -1,1 +0,0 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "Index: root/source/s2.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s2.txt\t(revision 358)\n" +
                                            "+++ root/source/s2.txt\t(revision 358)\n" +
                                            "@@ -1,1 +0,0 @@\n" +
                                            "-abc\n" +
                                            "\\ No newline at end of file\n", "root/source",
                                            new FileData[] {new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                                         StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false)});
    Data MINE_DELETE_THEIRS_EDIT = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision )\n" +
                                            "@@ -1,1 +1,1 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "+1*2*3\n" +
                                            "\\ No newline at end of file\n", "root/source",
                                            new FileData[] {new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                                         StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true)});

    Data MINE_EDIT_THEIRS_MOVE = new Data(
                                          "Index: root/source/s1.txt\n" +
                                          "===================================================================\n" +
                                          "--- root/source/s1.txt\t(revision 358)\n" +
                                          "+++ root/source1/s1.txt\t(revision )\n" +
                                          "@@ -1,0 +1,0 @@\n" +
                                          "Index: root/source/s2.txt\n" +
                                          "===================================================================\n" +
                                          "--- root/source/s2.txt\t(revision 358)\n" +
                                          "+++ root/source1/s2.txt\t(revision )\n" +
                                          "@@ -1,0 +1,0 @@\n",
                                          "root/source",
                                          new FileData[] {new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                                       StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false)});

    Data MINE_UNV_THEIRS_MOVE = new Data(
      "Index: root/source/s1.txt\n" +
      "===================================================================\n" +
      "--- root/source/s1.txt\t(revision 358)\n" +
      "+++ root/source1/s1.txt\t(revision )\n" +
      "@@ -1,0 +1,0 @@\n" +
      "Index: root/source/s2.txt\n" +
      "===================================================================\n" +
      "--- root/source/s2.txt\t(revision 358)\n" +
      "+++ root/source1/s2.txt\t(revision )\n" +
      "@@ -1,0 +1,0 @@\n", "root/source1",
                                          new FileData[] {new FileData("root/source1", null, StatusType.STATUS_UNVERSIONED,
                                                                       StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                       true),
                                          new FileData("root/source1/unv.txt", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                                                                                   StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                                                                   false)});

    Data MINE_MOVE_THEIRS_EDIT = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision )\n" +
                                            "@@ -1,1 +1,1 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "+1*2*3\n" +
                                            "\\ No newline at end of file\n", "root/source",
                                            new FileData[] {
                                              new FileData("root/sourceNew", null, StatusType.STATUS_ADDED,
                                                StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, true, "root/source"),
                                              new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                                         StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true)});
    Data MINE_MOVE_THEIRS_ADD = new Data("Index: root/sourceNew/added.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/sourceNew/added.txt\t(revision )\n" +
                                            "+++ root/sourceNew/added.txt\t(revision )\n" +
                                            "@@ -0,0 +1,1 @@\n" +
                                            "+added text\n" +
                                            "\\ No newline at end of file\n", "root/sourceNew",
                                            new FileData[] {
                                              new FileData("root/sourceNew", null, StatusType.STATUS_ADDED,
                                                StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, true, "root/source"),
                                              new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                                         StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true)}) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root\\source", "root\\source\\s1.txt", "root\\source\\s2.txt");
      }
    };
  }

  // mine -> theirs
  interface FileToDir {
    Data MINE_UNV_THEIRS_ADD = new Data("Index: addedDir/added.txt\n" +
                                        "===================================================================\n" +
                                        "--- addedDir/added.txt\t(revision )\n" +
                                        "+++ addedDir/added.txt\t(revision )\n" +
                                        "@@ -0,0 +1,1 @@\n" +
                                        "+added text\n" +
                                        "\\ No newline at end of file\n",
                                        "addedDir", new FileData[]{new FileData("addedDir", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                                    StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                    false)});

    Data MINE_ADD_THEIRS_ADD = new Data("Index: addedDir/added.txt\n" +
                                        "===================================================================\n" +
                                        "--- addedDir/added.txt\t(revision )\n" +
                                        "+++ addedDir/added.txt\t(revision )\n" +
                                        "@@ -0,0 +1,1 @@\n" +
                                        "+added text\n" +
                                        "\\ No newline at end of file\n",
                                        "addedDir", new FileData[]{new FileData("addedDir", "unversioned", StatusType.STATUS_ADDED,
                                                                    StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                    false)});

    Data MINE_UNV_THEIRS_MOVE = new Data(      "Index: root/source/s1.txt\n" +
          "===================================================================\n" +
          "--- root/source/s1.txt\t(revision 358)\n" +
          "+++ root/source1/s1.txt\t(revision )\n" +
          "@@ -1,0 +1,0 @@\n" +
          "Index: root/source/s2.txt\n" +
          "===================================================================\n" +
          "--- root/source/s2.txt\t(revision 358)\n" +
          "+++ root/source1/s2.txt\t(revision )\n" +
          "@@ -1,0 +1,0 @@\n",
                                        "root/source1", new FileData[]{new FileData("root/source1", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                                    StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                    false)});

    Data MINE_ADD_THEIRS_MOVE = new Data(      "Index: root/source/s1.txt\n" +
          "===================================================================\n" +
          "--- root/source/s1.txt\t(revision 358)\n" +
          "+++ root/source1/s1.txt\t(revision )\n" +
          "@@ -1,0 +1,0 @@\n" +
          "Index: root/source/s2.txt\n" +
          "===================================================================\n" +
          "--- root/source/s2.txt\t(revision 358)\n" +
          "+++ root/source1/s2.txt\t(revision )\n" +
          "@@ -1,0 +1,0 @@\n",
                                        "root/source1", new FileData[]{new FileData("root/source1", "unversioned", StatusType.STATUS_ADDED,
                                                                    StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                    false)});
    Data MINE_MOVE_THEIRS_ADD = new Data("Index: addedDir/added.txt\n" +
                                            "===================================================================\n" +
                                            "--- addedDir/added.txt\t(revision )\n" +
                                            "+++ addedDir/added.txt\t(revision )\n" +
                                            "@@ -0,0 +1,1 @@\n" +
                                            "+added text\n" +
                                            "\\ No newline at end of file\n",
                                            "addedDir", new FileData[]{new FileData("addedDir", null, StatusType.STATUS_ADDED,
                                                                        StatusType.STATUS_ADDED, StatusType.STATUS_ADDED,
                                                                        false, "root/source/s1.txt"),
                                                        new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                                                                              StatusType.STATUS_DELETED, StatusType.STATUS_DELETED,
                                                                              false, null)}) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root\\source\\s1.txt");
      }
    };
  }

  // mine -> theirs
  interface DirToFile {
    Data MINE_UNV_THEIRS_ADD = new Data("Index: addedDir.txt\n" +
                                        "===================================================================\n" +
                                        "--- addedDir.txt\t(revision )\n" +
                                        "+++ addedDir.txt\t(revision )\n" +
                                        "@@ -0,0 +1,1 @@\n" +
                                        "+added text\n" +
                                        "\\ No newline at end of file\n",
                                        "addedDir.txt", new FileData[]{new FileData("addedDir.txt", null, StatusType.STATUS_UNVERSIONED,
                                                                                StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                                true),
      new FileData("addedDir.txt/unv.txt", "unversioned", StatusType.STATUS_UNVERSIONED,
                   StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                   false)});

    Data MINE_ADD_THEIRS_ADD = new Data("Index: addedDir.txt\n" +
                                            "===================================================================\n" +
                                            "--- addedDir.txt\t(revision )\n" +
                                            "+++ addedDir.txt\t(revision )\n" +
                                            "@@ -0,0 +1,1 @@\n" +
                                            "+added text\n" +
                                            "\\ No newline at end of file\n",
                                        "addedDir.txt", new FileData[]{new FileData("addedDir.txt", null, StatusType.STATUS_ADDED,
                                                                                                                        StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                                                                        true),
                                              new FileData("addedDir.txt/unv.txt", "unversioned", StatusType.STATUS_ADDED,
                                                           StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                           false)});

    Data MINE_UNV_THEIRS_MOVE = new Data(      "Index: root/source/s1.txt\n" +
          "===================================================================\n" +
          "--- root/source/s1.txt\t(revision 358)\n" +
          "+++ root/source/s1renamed.txt\t(revision )\n" +
          "@@ -1,0 +1,0 @@\n" +
                                               "\\ No newline at end of file\n",
                                        "root/source/s1renamed.txt", new FileData[]{new FileData("root/source/s1renamed.txt", null,
                                                                                                 StatusType.STATUS_UNVERSIONED,
                                                                                                 StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                                                 true),
      new FileData("root/source/s1renamed.txt/file.txt", "unversioned",
                   StatusType.STATUS_UNVERSIONED,
                   StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                   false)});

    Data MINE_ADD_THEIRS_MOVE = new Data(      "Index: root/source/s1.txt\n" +
                                               "===================================================================\n" +
                                               "--- root/source/s1.txt\t(revision 358)\n" +
                                               "+++ root/source/s1renamed.txt\t(revision )\n" +
                                               "@@ -1,0 +1,0 @@\n" +
                                               "\\ No newline at end of file\n",
                                               "root/source/s1renamed.txt", new FileData[]{new FileData("root/source/s1renamed.txt", null,
                                                                                                        StatusType.STATUS_ADDED,
                                                                                                        StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                                                        true),
    new FileData("root/source/s1renamed.txt/file.txt", "unversioned",
                 StatusType.STATUS_ADDED,
                 StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                 false)});

    Data MINE_MOVE_THEIRS_ADD = new Data("Index: addedDir.txt\n" +
                                                "===================================================================\n" +
                                                "--- addedDir.txt\t(revision )\n" +
                                                "+++ addedDir.txt\t(revision )\n" +
                                                "@@ -0,0 +1,1 @@\n" +
                                                "+added text\n" +
                                                "\\ No newline at end of file\n",
                                            "addedDir.txt", new FileData[]{new FileData("addedDir.txt", null, StatusType.STATUS_ADDED,
                                              StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, true, "root/source"),
                                                  new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                  StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true)}) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root\\source", "root\\source\\s1.txt", "root\\source\\s2.txt");
      }
    };
  }

  class Data {
    private final Collection<FileData> myFileData;
    private final String myPatch;
    private final String myConflictFile;
    private String[] myExcludeFromToTheirsCheck;

    public Data(String patch, String file, FileData... fileData) {
      myConflictFile = file;
      myFileData = new ArrayList<>(Arrays.asList(fileData));
      myPatch = patch;
      afterInit();
    }

    protected void afterInit() {
    }

    Collection<FileData> getLeftFiles() {
      return myFileData;
    }

    String getTheirsPatch() {
      return myPatch;
    }

    public String getConflictFile() {
      return myConflictFile;
    }

    public String[] getExcludeFromToTheirsCheck() {
      return myExcludeFromToTheirsCheck;
    }

    public void setExcludeFromToTheirsCheck(String... excludeFromToTheirsCheck) {
      myExcludeFromToTheirsCheck = excludeFromToTheirsCheck;
    }
  }

  class FileData {
    public final String myRelativePath;
    public final String myContents;
    public final String myCopyFrom;
    public final StatusType myNodeStatus;
    public final StatusType myContentsStatus;
    // not used for now
    public final StatusType myPropertiesStatus;
    public boolean myIsDir;

    public FileData(String relativePath,
                    String contents,
                    StatusType nodeStatus,
                    StatusType contentsStatus,
                    StatusType propertiesStatus,
                    boolean isDir) {
      this(relativePath, contents, nodeStatus, contentsStatus, propertiesStatus, isDir, null);
    }

    public FileData(String relativePath,
                    String contents,
                    StatusType nodeStatus,
                    StatusType contentsStatus,
                    StatusType propertiesStatus,
                    boolean isDir, final String copyFrom) {
      myRelativePath = relativePath;
      myContents = contents;
      myNodeStatus = nodeStatus;
      myContentsStatus = contentsStatus;
      myPropertiesStatus = propertiesStatus;
      myIsDir = isDir;
      myCopyFrom = copyFrom;
    }
  }
}
