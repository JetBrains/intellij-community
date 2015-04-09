/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.associations.impl;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;

public class AnyXMLDescriptor extends FileChooserDescriptor {
    final FileTypeManager myFileTypeManager;

    public AnyXMLDescriptor(boolean chooseMultiple) {
        super(true, false, false, false, false, chooseMultiple);
        myFileTypeManager = FileTypeManager.getInstance();
    }

    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      final FileType fileType = file.getFileType();
        return file.isDirectory() || (super.isFileVisible(file, showHiddenFiles)
                && FileAssociationsManager.Holder.XML_FILES_LIST.contains(fileType));
    }
}
