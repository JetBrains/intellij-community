// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.idea.svn.status.StatusType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

public interface TreeConflictData {
  interface FileToFile {
    Data MINE_UNV_THEIRS_ADD = new Data("Index: added.txt\n" +
                                        "===================================================================\n" +
                                        "--- added.txt\t(revision )\n" +
                                        "+++ added.txt\t(revision )\n" +
                                        "@@ -0,0 +1,1 @@\n" +
                                        "+added text\n" +
                                        "\\ No newline at end of file\n",
                                        "added.txt", new FileData("added.txt", "unversioned text", StatusType.STATUS_UNVERSIONED,
                                                                  StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                  false));

    Data MINE_EDIT_THEIRS_DELETE = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision 358)\n" +
                                            "@@ -1,1 +0,0 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n", "root/source/s1.txt",
                                            // Status after resolve mine-full - added + copied from previous revision
                                            new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                         StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false,
                                                         "root/source/s1.txt"));
    Data MINE_DELETE_THEIRS_EDIT = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision )\n" +
                                            "@@ -1,1 +1,1 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "+1*2*3\n" +
                                            "\\ No newline at end of file\n", "root/source/s1.txt",
                                            new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                                                         StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, false));

    Data MINE_EDIT_THEIRS_MOVE = new Data("Index: root/source/s1.txt\n" +
                                          "===================================================================\n" +
                                          "--- root/source/s1.txt\t(revision 358)\n" +
                                          "+++ root/source/s1renamed.txt\t(revision )\n" +
                                          "@@ -1,0 +1,0 @@\n", "root/source/s1.txt",
                                          // Status after resolve mine-full - added + copied from previous revision
                                          new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                       StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false, "root/source/s1.txt"));
    Data MINE_UNV_THEIRS_MOVE = new Data("Index: root/source/s1.txt\n" +
                                          "===================================================================\n" +
                                          "--- root/source/s1.txt\t(revision 358)\n" +
                                          "+++ root/source/s1renamed.txt\t(revision )\n" +
                                          "@@ -1,0 +1,0 @@\n", "root/source/s1renamed.txt",
                                         new FileData("root/source/s1renamed.txt", "1*2*3", StatusType.STATUS_UNVERSIONED,
                                                      StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                      false));
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
                                          new FileData("root/source/s1moved.txt", null, StatusType.STATUS_ADDED,
                                                       StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, false, "root/source/s1.txt", true),
                                          new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                                                       StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, false));
    Data MINE_MOVE_THEIRS_ADD = new Data("Index: root/source/s1moved.txt\n" +
                                         "===================================================================\n" +
                                         "--- root/source/s1moved.txt\t(revision )\n" +
                                         "+++ root/source/s1moved.txt\t(revision )\n" +
                                         "@@ -0,0 +1,1 @@\n" +
                                         "+added text\n" +
                                         "\\ No newline at end of file\n",
                                         "root/source/s1moved.txt",
                                         new FileData("root/source/s1moved.txt", null, StatusType.STATUS_ADDED,
                                                      StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, false, "root/source/s1.txt"),
                                         new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                                                      StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, false)) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root/source/s1.txt");
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
                                        "addedDir", new FileData("addedDir", null, StatusType.STATUS_UNVERSIONED,
                                                                 StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                 true),
                                        new FileData("addedDir/unv.txt", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                     StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                     false));

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
                                            new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                         StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false));
    Data MINE_DELETE_THEIRS_EDIT = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision )\n" +
                                            "@@ -1,1 +1,1 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "+1*2*3\n" +
                                            "\\ No newline at end of file\n", "root/source",
                                            new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                         StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true));

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
                                          new FileData("root/source/s1.txt", "1*2*3", StatusType.STATUS_NORMAL,
                                                       StatusType.STATUS_MODIFIED, StatusType.STATUS_NORMAL, false));

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
      new FileData("root/source1", null, StatusType.STATUS_UNVERSIONED,
                   StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                   true),
      new FileData("root/source1/unv.txt", "unversioned", StatusType.STATUS_UNVERSIONED,
                   StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                   false));

    Data MINE_MOVE_THEIRS_EDIT = new Data("Index: root/source/s1.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/source/s1.txt\t(revision 358)\n" +
                                            "+++ root/source/s1.txt\t(revision )\n" +
                                            "@@ -1,1 +1,1 @@\n" +
                                            "-123\n" +
                                            "\\ No newline at end of file\n" +
                                            "+1*2*3\n" +
                                            "\\ No newline at end of file\n", "root/source",
                                          new FileData("root/sourceNew", null, StatusType.STATUS_ADDED,
                                                       StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, true, "root/source"),
                                          new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                       StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true));
    Data MINE_MOVE_THEIRS_ADD = new Data("Index: root/sourceNew/added.txt\n" +
                                            "===================================================================\n" +
                                            "--- root/sourceNew/added.txt\t(revision )\n" +
                                            "+++ root/sourceNew/added.txt\t(revision )\n" +
                                            "@@ -0,0 +1,1 @@\n" +
                                            "+added text\n" +
                                            "\\ No newline at end of file\n", "root/sourceNew",
                                         new FileData("root/sourceNew", null, StatusType.STATUS_ADDED,
                                                      StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, true, "root/source"),
                                         new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                      StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true)) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root/source", "root/source/s1.txt", "root/source/s2.txt");
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
                                        "addedDir", new FileData("addedDir", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                                 StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                 false));

    Data MINE_ADD_THEIRS_ADD = new Data("Index: addedDir/added.txt\n" +
                                        "===================================================================\n" +
                                        "--- addedDir/added.txt\t(revision )\n" +
                                        "+++ addedDir/added.txt\t(revision )\n" +
                                        "@@ -0,0 +1,1 @@\n" +
                                        "+added text\n" +
                                        "\\ No newline at end of file\n",
                                        "addedDir", new FileData("addedDir", "unversioned", StatusType.STATUS_ADDED,
                                                                 StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                 false));

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
                                               "root/source1", new FileData("root/source1", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                                            StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                            false));

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
                                               "root/source1", new FileData("root/source1", "unversioned", StatusType.STATUS_ADDED,
                                                                            StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                            false));
    Data MINE_MOVE_THEIRS_ADD = new Data("Index: addedDir/added.txt\n" +
                                            "===================================================================\n" +
                                            "--- addedDir/added.txt\t(revision )\n" +
                                            "+++ addedDir/added.txt\t(revision )\n" +
                                            "@@ -0,0 +1,1 @@\n" +
                                            "+added text\n" +
                                            "\\ No newline at end of file\n",
                                         "addedDir", new FileData("addedDir", null, StatusType.STATUS_ADDED,
                                                                  StatusType.STATUS_ADDED, StatusType.STATUS_ADDED,
                                                                  false, "root/source/s1.txt"),
                                         new FileData("root/source/s1.txt", null, StatusType.STATUS_DELETED,
                                                      StatusType.STATUS_DELETED, StatusType.STATUS_DELETED,
                                                      false, null)) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root/source/s1.txt");
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
                                        "addedDir.txt", new FileData("addedDir.txt", null, StatusType.STATUS_UNVERSIONED,
                                                                     StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                     true),
                                        new FileData("addedDir.txt/unv.txt", "unversioned", StatusType.STATUS_UNVERSIONED,
                                                     StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                     false));

    Data MINE_ADD_THEIRS_ADD = new Data("Index: addedDir.txt\n" +
                                            "===================================================================\n" +
                                            "--- addedDir.txt\t(revision )\n" +
                                            "+++ addedDir.txt\t(revision )\n" +
                                            "@@ -0,0 +1,1 @@\n" +
                                            "+added text\n" +
                                            "\\ No newline at end of file\n",
                                        "addedDir.txt", new FileData("addedDir.txt", null, StatusType.STATUS_ADDED,
                                                                     StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                                     true),
                                        new FileData("addedDir.txt/unv.txt", "unversioned", StatusType.STATUS_ADDED,
                                                     StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                     false));

    Data MINE_UNV_THEIRS_MOVE = new Data(      "Index: root/source/s1.txt\n" +
          "===================================================================\n" +
          "--- root/source/s1.txt\t(revision 358)\n" +
          "+++ root/source/s1renamed.txt\t(revision )\n" +
          "@@ -1,0 +1,0 @@\n" +
                                               "\\ No newline at end of file\n",
                                               "root/source/s1renamed.txt", new FileData("root/source/s1renamed.txt", null,
                                                                                         StatusType.STATUS_UNVERSIONED,
                                                                                         StatusType.STATUS_UNVERSIONED,
                                                                                         StatusType.STATUS_UNVERSIONED,
                                                                                         true),
                                               new FileData("root/source/s1renamed.txt/file.txt", "unversioned",
                                                            StatusType.STATUS_UNVERSIONED,
                                                            StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                            false));

    Data MINE_ADD_THEIRS_MOVE = new Data(      "Index: root/source/s1.txt\n" +
                                               "===================================================================\n" +
                                               "--- root/source/s1.txt\t(revision 358)\n" +
                                               "+++ root/source/s1renamed.txt\t(revision )\n" +
                                               "@@ -1,0 +1,0 @@\n" +
                                               "\\ No newline at end of file\n",
                                               "root/source/s1renamed.txt", new FileData("root/source/s1renamed.txt", null,
                                                                                         StatusType.STATUS_ADDED,
                                                                                         StatusType.STATUS_UNVERSIONED,
                                                                                         StatusType.STATUS_UNVERSIONED,
                                                                                         true),
                                               new FileData("root/source/s1renamed.txt/file.txt", "unversioned",
                                                            StatusType.STATUS_ADDED,
                                                            StatusType.STATUS_UNVERSIONED, StatusType.STATUS_UNVERSIONED,
                                                            false));

    Data MINE_MOVE_THEIRS_ADD = new Data("Index: addedDir.txt\n" +
                                                "===================================================================\n" +
                                                "--- addedDir.txt\t(revision )\n" +
                                                "+++ addedDir.txt\t(revision )\n" +
                                                "@@ -0,0 +1,1 @@\n" +
                                                "+added text\n" +
                                                "\\ No newline at end of file\n",
                                         "addedDir.txt", new FileData("addedDir.txt", null, StatusType.STATUS_ADDED,
                                                                      StatusType.STATUS_ADDED, StatusType.STATUS_ADDED, true,
                                                                      "root/source"),
                                         new FileData("root/source", null, StatusType.STATUS_DELETED,
                                                      StatusType.STATUS_DELETED, StatusType.STATUS_DELETED, true)) {
      @Override
      protected void afterInit() {
        setExcludeFromToTheirsCheck("root/source", "root/source/s1.txt", "root/source/s2.txt");
      }
    };
  }

  class Data {
    private final Collection<FileData> myFileData;
    private final String myPatch;
    private final String myConflictFile;
    private final Set<@SystemIndependent String> myExcludeFromToTheirsCheck = new HashSet<>();

    public Data(String patch, String file, FileData... fileData) {
      myConflictFile = file;
      myFileData = new ArrayList<>(asList(fileData));
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

    public boolean isExcludedFromToTheirsCheck(@SystemIndependent String path) {
      return myExcludeFromToTheirsCheck.contains(path);
    }

    public void setExcludeFromToTheirsCheck(@SystemIndependent String @NotNull ... excludeFromToTheirsCheck) {
      myExcludeFromToTheirsCheck.clear();
      myExcludeFromToTheirsCheck.addAll(asList(excludeFromToTheirsCheck));
    }
  }

  class FileData {
    public final String myRelativePath;
    public final String myContents;
    public final String myCopyFrom;
    public final boolean myIsMove;
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
                    boolean isDir,
                    String copyFrom) {
      this(relativePath, contents, nodeStatus, contentsStatus, propertiesStatus, isDir, copyFrom, false);
    }

    public FileData(String relativePath,
                    String contents,
                    StatusType nodeStatus,
                    StatusType contentsStatus,
                    StatusType propertiesStatus,
                    boolean isDir,
                    final String copyFrom,
                    boolean isMove) {
      myRelativePath = relativePath;
      myContents = contents;
      myNodeStatus = nodeStatus;
      myContentsStatus = contentsStatus;
      myPropertiesStatus = propertiesStatus;
      myIsDir = isDir;
      myCopyFrom = copyFrom;
      myIsMove = isMove;
    }
  }
}
