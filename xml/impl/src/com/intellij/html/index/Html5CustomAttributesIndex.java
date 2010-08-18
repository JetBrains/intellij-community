/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.html.index;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class Html5CustomAttributesIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> ID = new ID<String, Void>("html5.custom.attributes.index") {
  };

  private final DataIndexer<String, Void, FileContent> myIndexer = new DataIndexer<String, Void, FileContent>() {
    @NotNull
    public Map<String, Void> map(FileContent inputData) {
      PsiFile psiFile = inputData.getPsiFile();
      if (psiFile instanceof XmlFile) {
        XmlDocument document = ((XmlFile)psiFile).getDocument();
        if (document != null && HtmlUtil.isHtml5Document(document)) {
          MyCustomAttributesCollector collector = new MyCustomAttributesCollector();
          document.accept(collector);
          return collector.myResult;
        }
      }
      return Collections.emptyMap();
    }
  };

  private final FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      return (file.getFileSystem() == LocalFileSystem.getInstance() || file.getFileSystem() instanceof TempFileSystem) &&
             file.getFileType() == StdFileTypes.HTML;
    }
  };

  @Override
  public ID<String, Void> getName() {
    return ID;
  }

  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  private static class MyCustomAttributesCollector extends XmlRecursiveElementVisitor {
    private final Map<String, Void> myResult = new HashMap<String, Void>();

    @Override
    public void visitXmlAttribute(XmlAttribute attribute) {
      String attrName = attribute.getName();
      if (attrName.startsWith(HtmlUtil.HTML5_DATA_ATTR_PREFIX)) {
        myResult.put(attrName, null);
      }
    }
  }
}
