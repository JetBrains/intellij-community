/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package myVFS;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

public class VfsSamplePlugin implements ProjectComponent {
    private final Project myProject;

    private static int ourJavaFilesCount;

    public VfsSamplePlugin(Project project) {
        myProject = project;
    }

    public void projectOpened() {
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
        VirtualFile[] sourceRoots = projectRootManager.getContentSourceRoots();

        ourJavaFilesCount = 0;
         // Count the current number of Java files in the opened project.
        for (VirtualFile sourceRoot : sourceRoots) {
            countJavaFiles(sourceRoot);
        }
         // Add the Virtual File listener
        MyVfsListener vfsListener = new MyVfsListener();
        VirtualFileManager.getInstance().addVirtualFileListener(vfsListener, myProject);
    }

    public void projectClosed() {
    }

    public void initComponent() {
        // empty
    }

    public void disposeComponent() {
        // empty
    }

    @NotNull
    public String getComponentName() {
        return "VfsSample.VfsSamplePlugin";
    }
         // Increase or decrease the current number of Java files. Return true when a Java file has been added or removed from the opened project.
    private static boolean updateCount(VirtualFile file, int increase) {

        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        if (!fileTypeManager.isFileIgnored(file.getName())
                && fileTypeManager.getFileTypeByFile(file) == StdFileTypes.JAVA) {
            ourJavaFilesCount += increase;
            return true;
            //System.out.println("ourJavaFilesCount = " + ourJavaFilesCount);
        }
        return false;
    }
         // Count current number of Java file in the project.
    private static void countJavaFiles(VirtualFile virtualFile) {
        VirtualFile[] children = virtualFile.getChildren();
        if (children == null) return;

        for (VirtualFile child : children) {
            updateCount(child, +1);
            countJavaFiles(child);
        }

    }

    // -------------------------------------------------------------------------
    // MyVfsListener
    // -------------------------------------------------------------------------

    private static class MyVfsListener extends VirtualFileAdapter {
        public void fileCreated(VirtualFileEvent event) {
            if (updateCount(event.getFile(), +1)) {
                Messages.showMessageDialog("A new Java file added. Now " + String.valueOf(ourJavaFilesCount) +
                        " Java files in this project.", "Notification", Messages.getInformationIcon());
            }
        }

        public void fileDeleted(VirtualFileEvent event) {
            if (updateCount(event.getFile(), -1)) {
                Messages.showMessageDialog("A Java file deleted. Now " + String.valueOf(ourJavaFilesCount) +
                        " Java files in this project.", "Notification", Messages.getInformationIcon());
            }
        }
    }
}
