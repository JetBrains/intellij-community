/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SearchScope implements JDOMExternalizable {

    public enum ScopeType {
        PROJECT,MODULE,DIRECTORY,CUSTOM
    }

    private ScopeType myScopeType;
    private String myModuleName;
    private String myPath;
    private boolean myRecursive;
    private String myScopeName;

    private com.intellij.psi.search.SearchScope myCustomScope;

    public SearchScope() {
        myScopeType = ScopeType.PROJECT;
        myRecursive = true;
    }

    public SearchScope(SearchScope scope) {
        myScopeType = scope.getScopeType();

        myModuleName = scope.getModuleName();
        myPath = scope.getPath();
        myRecursive = scope.isRecursive();
        myScopeName = scope.getScopeName();
    }

    public SearchScope(ScopeType scopeType, String directoryName, boolean recursive, String moduleName, String scopeName) {
        myScopeType = scopeType;
        myPath = directoryName;
        myRecursive = recursive;
        myModuleName = moduleName;
        myScopeName = scopeName;
    }

    public void setCustomScope(com.intellij.psi.search.SearchScope customScope) {
        myCustomScope = customScope;
    }

    public String getName() {
        switch (getScopeType()) {
            case PROJECT:
                return "Project";
            case MODULE:
                return "Module '" + getModuleName() + "'";
            case DIRECTORY:
                return "Directory '" + getPath() + "'";
            case CUSTOM:
                return "Scope '" + getScopeName() + "'";
        }
        assert false;
        return null;
    }

    @NotNull
    public ScopeType getScopeType() {
        return myScopeType;
    }

    public String getModuleName() {
        return myModuleName;
    }

    @Nullable
    public String getScopeName() {
        return myScopeName;
    }

    @Nullable
    public String getPath() {
        return myPath;
    }

    public boolean isRecursive() {
        return myRecursive;
    }

    public void readExternal(Element element) throws InvalidDataException {
        myScopeType = ScopeType.valueOf(element.getAttributeValue("type"));

        if (myScopeType == ScopeType.MODULE) {
            final Element m = element.getChild("module");
            if (m != null) {
                myModuleName = m.getTextTrim();
            }
        } else if (myScopeType == ScopeType.DIRECTORY) {
            final Element path = element.getChild("path");
            if (path != null) {
                myPath = path.getTextTrim();
                myRecursive = "true".equals(path.getAttributeValue("recursive"));
            }
        } else if (myScopeType == ScopeType.CUSTOM) {
            myScopeName = element.getAttributeValue("scope-name");
        }
    }

    public void writeExternal(Element element) throws WriteExternalException {

        final ScopeType scopeType = getScopeType();
        element.setAttribute("type", scopeType.toString());

        if (scopeType == ScopeType.MODULE) {
            if (myModuleName != null) {
                final Element m = new Element("module");
                element.addContent(m);
                m.setText(myModuleName);
            }
        } else if (scopeType == ScopeType.DIRECTORY) {
            final Element p = new Element("path");
            element.addContent(p);
            p.setAttribute("recursive", Boolean.toString(myRecursive));
            if (myPath != null) {
                p.setText(myPath);
            }
        } else if (scopeType == ScopeType.CUSTOM) {
            if (myScopeName != null) {
                element.setAttribute("scope-name", myScopeName);
            }
        }
    }

    public boolean isValid() {
        final String dirName = getPath();
        final String moduleName = getModuleName();

        switch (getScopeType()) {
            case MODULE:
                return moduleName != null && moduleName.length() > 0;
            case DIRECTORY:
                return dirName != null && dirName.length() > 0 && findFile(dirName) != null;
            case CUSTOM:
                return myCustomScope != null;
        }

        return true;
    }


    public void iterateContent(@NotNull final Project project, final Processor<VirtualFile> processor) {

        switch (getScopeType()) {
            case PROJECT:
                //noinspection unchecked
                ProjectRootManager.getInstance(project).getFileIndex().iterateContent(new MyFileIterator(processor, Condition.TRUE));
                break;
            case MODULE:
                final Module module = ModuleManager.getInstance(project).findModuleByName(getModuleName());
                //noinspection unchecked
                ModuleRootManager.getInstance(module).getFileIndex().iterateContent(new MyFileIterator(processor, Condition.TRUE));
                break;
            case DIRECTORY:
                final String dirName = getPath();
                assert dirName != null;

                final VirtualFile virtualFile = findFile(dirName);
                if (virtualFile != null) {
                    iterateRecursively(virtualFile, processor, isRecursive());
                }
                break;
            case CUSTOM:
                assert myCustomScope != null;

                final ContentIterator iterator;
                if (myCustomScope instanceof GlobalSearchScope) {
                    final GlobalSearchScope searchScope = (GlobalSearchScope)myCustomScope;
                    iterator = new MyFileIterator(processor, new Condition<VirtualFile>() {
                        public boolean value(VirtualFile virtualFile) {
                            return searchScope.contains(virtualFile);
                        }
                    });
                    if (searchScope.isSearchInLibraries()) {
                        final OrderEnumerator enumerator = OrderEnumerator.orderEntries(project).withoutModuleSourceEntries().withoutDepModules();
                        final Collection<VirtualFile> libraryFiles = new THashSet<VirtualFile>();
                        libraryFiles.addAll(enumerator.getClassesRoots());
                        libraryFiles.addAll(enumerator.getSourceRoots());
                        final Processor<VirtualFile> adapter = new Processor<VirtualFile>() {
                            public boolean process(VirtualFile virtualFile) {
                                return iterator.processFile(virtualFile);
                            }
                        };
                        for (final VirtualFile file : libraryFiles) {
                            iterateRecursively(file, adapter, true);
                        }
                    }
                } else {
                    final PsiManager manager = PsiManager.getInstance(project);
                    iterator = new MyFileIterator(processor, new Condition<VirtualFile>() {
                        public boolean value(VirtualFile virtualFile) {
                            final PsiFile element = manager.findFile(virtualFile);
                            return element != null && PsiSearchScopeUtil.isInScope(myCustomScope, element);
                        }
                    });
                }

                ProjectRootManager.getInstance(project).getFileIndex().iterateContent(iterator);
        }
    }

    @Nullable
    private static VirtualFile findFile(String dirName) {
        return LocalFileSystem.getInstance().findFileByPath(dirName.replace('\\', '/'));
    }

    private static void iterateRecursively(VirtualFile virtualFile, Processor<VirtualFile> processor, boolean recursive) {
        final VirtualFile[] children = virtualFile.getChildren();
        for (VirtualFile file : children) {
            if (file.isDirectory()) {
                if (recursive) {
                    iterateRecursively(file, processor, recursive);
                }
            } else {
                processor.process(file);
            }
        }
        if (!virtualFile.isDirectory()) {
            processor.process(virtualFile);
        }
    }

    private static class MyFileIterator implements ContentIterator {
        private final Processor<VirtualFile> myProcessor;
        private final Condition<VirtualFile> myCondition;

        public MyFileIterator(Processor<VirtualFile> processor, Condition<VirtualFile> condition) {
            myCondition = condition;
            myProcessor = processor;
        }

        public boolean processFile(VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory() && myCondition.value(fileOrDir)) {
                myProcessor.process(fileOrDir);
            }
            return true;
        }
    }
}
