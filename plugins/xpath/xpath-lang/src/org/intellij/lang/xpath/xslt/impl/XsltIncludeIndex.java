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

import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIncludeManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Collections;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 15.12.2008
*/
public class XsltIncludeIndex implements PsiIncludeManager.PsiIncludeHandler {
    private static final Logger LOG = Logger.getInstance(XsltIncludeIndex.class.getName());

    public boolean shouldCheckFile(@NotNull VirtualFile virtualFile) {
        if (virtualFile.getFileType() != StdFileTypes.XML) {
            return false;
        }

        final boolean[] ref = new boolean[1];
        try {
            NanoXmlUtil.parse(virtualFile.getInputStream(), new NanoXmlUtil.IXMLBuilderAdapter() {
                @Override
                public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
                    if (XsltSupport.XSLT_NS.equals(nsURI) && ("stylesheet".equals(name) || "transform".equals(name))) {
                        ref[0] = true;
                        stop();
                    }
                }

                @Override
                public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
                    if ("version".equals(key) && nsURI.equals(XsltSupport.XSLT_NS)) {
                        ref[0] = true;
                        stop();
                    }
                }

                @Override
                public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
                    stop();  // the first element (or its attrs) decides - stop here
                }
            });
        } catch (IOException e) {
            LOG.info("Cannot parse " + virtualFile.getUrl(), e);
            return false;
        }
        return ref[0];
    }

    public PsiIncludeManager.IncludeInfo[] findIncludes(PsiFile psiFile) {
        if (!(psiFile instanceof XmlFile)) return PsiIncludeManager.IncludeInfo.EMPTY_ARRAY;

        final List<XmlTag> includes = new SmartList<XmlTag>();
        try {
            final XmlTag root = ((XmlFile)psiFile).getDocument().getRootTag();
            Collections.addAll(includes, root.findSubTags("include", XsltSupport.XSLT_NS));
            Collections.addAll(includes, root.findSubTags("import", XsltSupport.XSLT_NS));
        } catch (NullPointerException e) {
            // nothing
            return PsiIncludeManager.IncludeInfo.EMPTY_ARRAY;
        }
        final PsiIncludeManager.IncludeInfo[] info = new PsiIncludeManager.IncludeInfo[includes.size()];
        for (int i = 0; i < info.length; i++) {
            final XmlTag directive = includes.get(i);
            final PsiFile target = resolve(psiFile, directive);
            info[i] = new PsiIncludeManager.IncludeInfo(target, directive, new String[]{ target.getName() }, this);
        }
        return info;
    }

    @Nullable
    private static PsiFile resolve(PsiFile psiFile, XmlTag directive) {
        final XmlAttribute href = directive.getAttribute("href");
        return ResolveUtil.resolveFile(href, psiFile);
    }

    public static boolean isReachableFrom(XmlFile which, XmlFile from) {
        final Project project = which.getProject();
        if (project != from.getProject()) {
            return false;
        }
        return _isReachableFrom(project.getComponent(PsiIncludeManager.class), from, which);
    }

    private static boolean _isReachableFrom(PsiIncludeManager manager, XmlFile from, PsiFile... which) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < which.length; i++) {
            final PsiFile file = which[i];
            if (file == from) {
                return true;
            }
            if (_isReachableFrom(manager, from, manager.getIncludingFiles(file))) {
                return true;
            }
        }
        return false;
    }

    public void includeChanged(PsiElement includeDirective, PsiFile targetFile, PomModelEvent event) {
//        System.out.println("event = " + event);
    }

    public static boolean processBackwardDependencies(XmlFile file, Processor<XmlFile> processor) {
        final PsiIncludeManager manager = file.getProject().getComponent(PsiIncludeManager.class);
        return _processBackwardDependencies(manager, manager.getIncludingFiles(file), processor);
    }

    private static boolean _processBackwardDependencies(PsiIncludeManager manager, PsiFile[] files, Processor<XmlFile> processor) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < files.length; i++) {
            final PsiFile psiFile = files[i];
            if (XsltSupport.isXsltFile(psiFile)) {
                if (!processor.process((XmlFile)psiFile)) {
                    return false;
                }
                if (!_processBackwardDependencies(manager, manager.getIncludingFiles(psiFile), processor)) {
                    return false;
                }
            }
        }
        return true;
    }
}