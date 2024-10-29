// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG.model.resolve;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.intellij.plugins.relaxNG.RelaxNgMetaDataContributor;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.model.CommonElement;
import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.Grammar;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RelaxSymbolIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> NAME = ID.create("RelaxSymbolIndex");

  @Override
  public @NotNull ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<String, Void> map(@NotNull FileContent inputData) {
        final HashMap<String, Void> map = new HashMap<>();
        if (inputData.getFileType() == XmlFileType.INSTANCE) {
          CharSequence inputDataContentAsText = inputData.getContentAsText();
          if (CharArrayUtil.indexOf(inputDataContentAsText, RelaxNgMetaDataContributor.RNG_NAMESPACE, 0) == -1) {
            return Collections.emptyMap();
          }
          NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlBuilder() {
            NanoXmlBuilder attributeHandler;
            int depth;

            @Override
            public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
              if (attributeHandler != null) {
                attributeHandler.addAttribute(key, nsPrefix, nsURI, value, type);
              }
            }

            @Override
            public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
              attributeHandler = null;
              if (depth == 1 && RelaxNgMetaDataContributor.RNG_NAMESPACE.equals(nsURI)) {
                if ("define".equals(name)) {
                  attributeHandler = new NanoXmlBuilder() {
                    @Override
                    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
                      if ("name".equals(key) && (nsURI == null || nsURI.length() == 0) && value != null) {
                        map.put(value, null);
                      }
                    }
                  };
                }
              }
              depth++;
            }

            @Override
            public void endElement(String name, String nsPrefix, String nsURI) {
              attributeHandler = null;
              depth--;
            }
          });
        }
        else if (inputData.getFileType() == RncFileType.getInstance()) {
          final PsiFile file = inputData.getPsiFile();
          if (file instanceof XmlFile) {
            final Grammar grammar = GrammarFactory.getGrammar((XmlFile)file);
            if (grammar != null) {
              grammar.acceptChildren(new CommonElement.Visitor() {
                @Override
                public void visitDefine(Define define) {
                  final String name = define.getName();
                  if (name != null) {
                    map.put(name, null);
                  }
                }
              });
            }
          }
        }
        return map;
      }
    };
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE, RncFileType.getInstance()) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return !(file.getFileSystem() instanceof JarFileSystem);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

}
