/*
 * Copyright 2005-2008 Sascha Weinreuter
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

package org.intellij.lang.xpath.xslt.impl;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumDataDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 15.12.2008
*/
public class XsltSymbolIndex extends FileBasedIndexExtension<String, XsltSymbolIndex.Kind> {
    @NonNls
    public static final ID<String, Kind> NAME = ID.create("XsltSymbolIndex");

    @SuppressWarnings({ "UnusedDeclaration" })
    public static Collection<String> getSymbolNames(Project project) {
        return FileBasedIndex.getInstance().getAllKeys(NAME, project);
    }

    public static NavigationItem[] getSymbolsByName(final String name, Project project, boolean includeNonProjectItems) {
        final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
        final SymbolCollector collector = new SymbolCollector(name, project, scope);
        FileBasedIndex.getInstance().processValues(NAME, name, null, collector, scope);
        return collector.getResult();
    }

    @Override
    @NotNull
    public ID<String, Kind> getName() {
        return NAME;
    }

    @Override
    @NotNull
    public DataIndexer<String, Kind, FileContent> getIndexer() {
        return new DataIndexer<String, Kind, FileContent>() {
            @Override
            @NotNull
            public Map<String, Kind> map(@NotNull FileContent inputData) {
                CharSequence inputDataContentAsText = inputData.getContentAsText();
                if (CharArrayUtil.indexOf(inputDataContentAsText, XsltSupport.XSLT_NS, 0) == -1) {
                  return Collections.emptyMap();
                }
                final HashMap<String, Kind> map = new HashMap<>();
                NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlUtil.IXMLBuilderAdapter() {
                    NanoXmlUtil.IXMLBuilderAdapter attributeHandler;
                    int depth;

                    @Override
                    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
                        if (attributeHandler != null) {
                            attributeHandler.addAttribute(key, nsPrefix, nsURI, value, type);
                        }
                    }

                    @Override
                    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
                        attributeHandler = null;
                        if (depth == 1 && XsltSupport.XSLT_NS.equals(nsURI)) {
                            if ("template".equals(name)) {
                                attributeHandler = new MyAttributeHandler(map, Kind.TEMPLATE);
                            } else if ("variable".equals(name)) {
                                attributeHandler = new MyAttributeHandler(map, Kind.VARIABLE);
                            } else if ("param".equals(name)) {
                                attributeHandler = new MyAttributeHandler(map, Kind.PARAM);
                            }
                        }
                        depth++;
                    }

                    @Override
                    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
                        attributeHandler = null;
                        depth--;
                    }
                });
                return map;
            }
        };
    }

    @NotNull
    @Override
    public DataExternalizer<Kind> getValueExternalizer() {
        return new EnumDataDescriptor<>(Kind.class);
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML) {
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

    enum Kind {
        PARAM(XsltParameter.class), VARIABLE(XsltVariable.class), TEMPLATE(XsltTemplate.class), ANYTHING(null);

        final Class<? extends XsltElement> myClazz;

        Kind(Class<? extends XsltElement> clazz) {
            myClazz = clazz;
        }

        @Nullable
        public XsltElement wrap(XmlTag tag) {
            final Class<? extends XsltElement> clazz;
            if (myClazz != null) {
                if (!name().toLowerCase().equals(tag.getLocalName())) {
                    return null;
                }
                clazz = myClazz;
            } else {
                try {
                    clazz = valueOf(tag.getLocalName().toUpperCase()).myClazz;
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            return XsltElementFactory.getInstance().wrapElement(tag, clazz);
        }
    }

    private static class MyAttributeHandler extends NanoXmlUtil.IXMLBuilderAdapter {
        private final HashMap<String, Kind> myMap;
        private final Kind myKind;

        public MyAttributeHandler(HashMap<String, Kind> map, Kind k) {
            myMap = map;
            myKind = k;
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
            if (key.equals("name") && (nsURI == null || nsURI.length() == 0) && value != null) {
                if (myMap.put(value, myKind) != null) {
                    myMap.put(value, Kind.ANYTHING);
                }
            }
        }
    }

    private static class SymbolCollector implements FileBasedIndex.ValueProcessor<Kind> {
        private final GlobalSearchScope myScope;
        private final PsiManager myMgr;
        private final String myName;

        private final Collection<NavigationItem> myResult = new ArrayList<>();

        public SymbolCollector(String name, Project project, GlobalSearchScope scope) {
            myMgr = PsiManager.getInstance(project);
            myScope = scope;
            myName = name;
        }

        @Override
        public boolean process(VirtualFile file, Kind kind) {
            if (myScope.contains(file)) {
                final PsiFile psiFile = myMgr.findFile(file);
                if (psiFile != null && XsltSupport.isXsltFile(psiFile)) {
                    final XmlTag[] tags;
                    try {
                        final XmlTag root = ((XmlFile)psiFile).getRootTag();
                        assert root != null;
                        if (kind == Kind.ANYTHING) {
                          final XmlTag[] v = root.findSubTags("variable", XsltSupport.XSLT_NS);
                            final XmlTag[] p = root.findSubTags("param", XsltSupport.XSLT_NS);
                            final XmlTag[] t = root.findSubTags("template", XsltSupport.XSLT_NS);
                            tags = ArrayUtil.mergeArrays(ArrayUtil.mergeArrays(v, p), t);
                        } else {
                            tags = root.findSubTags(kind.name().toLowerCase(), XsltSupport.XSLT_NS);
                        }
                    } catch (NullPointerException e) {
                        // something is null, don't bother
                        return true;
                    }

                    for (XmlTag tag : tags) {
                        assert XsltSupport.isXsltTag(tag);

                        final XsltElement el = kind.wrap(tag);
                        if (el instanceof PsiNamedElement && el instanceof NavigationItem) {
                            if (myName.equals(((PsiNamedElement)el).getName())) {
                                myResult.add((NavigationItem)el);
                            }
                        }
                    }
                }
            }
          return true;
        }

        public NavigationItem[] getResult() {
            return myResult.toArray(new NavigationItem[myResult.size()]);
        }
    }
}
