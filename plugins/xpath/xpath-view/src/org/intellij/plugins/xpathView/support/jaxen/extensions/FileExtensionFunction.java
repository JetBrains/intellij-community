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
package org.intellij.plugins.xpathView.support.jaxen.extensions;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.Nullable;

class FileExtensionFunction extends BasicFileInfoFunction {
    FileExtensionFunction() {
        super("file-ext", XPathType.STRING, new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL));
    }

    @Override
    @Nullable
    protected Object extractInfo(PsiFile psiFile) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        return virtualFile != null ? virtualFile.getExtension() : null;
    }
}
