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

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;

/**
 * @deprecated use {@link com.intellij.openapi.fileChooser.FileChooserDescriptorFactory}
 * along with {@link FileChooserDescriptor#withExtensionFilter(String, FileType...)}.
 */
@Deprecated(forRemoval = true)
public class AnyXMLDescriptor extends FileChooserDescriptor {
    public AnyXMLDescriptor(boolean chooseMultiple) {
        super(true, false, false, false, false, chooseMultiple);
        withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", "XML"), FileAssociationsManager.Holder.XML_FILES);
    }
}
