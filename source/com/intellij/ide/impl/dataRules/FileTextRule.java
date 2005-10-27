/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 16, 2002
 * Time: 1:49:57 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class FileTextRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    VirtualFile virtualFile = (VirtualFile)dataProvider.getData(DataConstants.VIRTUAL_FILE);
    if (virtualFile == null) return null;
    final FileType fileType = virtualFile.getFileType();
    if (fileType.isBinary() || fileType.isReadOnly()) return null;

    Project project = (Project)dataProvider.getData(DataConstants.PROJECT);
    if (project == null) return null;

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) return null;
    return document.getText();
  }
}
