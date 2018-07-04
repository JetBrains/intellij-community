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

import com.intellij.find.FindBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageSearcher;
import com.intellij.util.Processor;
import org.intellij.plugins.xpathView.HistoryElement;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.util.CachedVariableContext;
import org.jaxen.Context;
import org.jaxen.ContextSupport;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jaxen.pattern.Pattern;
import org.jaxen.pattern.PatternParser;
import org.jaxen.saxpath.SAXPathException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class XPathUsageSearcher implements UsageSearcher {
    private final ProgressIndicator myIndicator;
    private final PsiManager myManager;
    private final HistoryElement myExpression;
    private final Project myProject;
    private final SearchScope myScope;
    private final boolean myMatchRecursively;
    private final XPathSupport mySupport;

    public XPathUsageSearcher(Project project, HistoryElement expression, SearchScope scope, boolean matchRecursively) {
        myExpression = expression;
        myProject = project;
        myScope = scope;
        myMatchRecursively = matchRecursively && !expression.expression.trim().startsWith("//");
        mySupport = XPathSupport.getInstance();
        myIndicator = ProgressManager.getInstance().getProgressIndicator();
        myManager = PsiManager.getInstance(myProject);
    }

    @Override
    public void generate(@NotNull final Processor<Usage> processor) {
        Runnable runnable = () -> {
            myIndicator.setIndeterminate(true);
            myIndicator.setText2(findBundleMessage("find.searching.for.string.in.file.occurrences.progress", 0));
            final CountProcessor counter = new CountProcessor();
            myScope.iterateContent(myProject, counter);

            myIndicator.setIndeterminate(false);
            myIndicator.setFraction(0);
            myScope.iterateContent(myProject, new MyProcessor(processor, counter.getFileCount()));
        };
        ApplicationManager.getApplication().runReadAction(runnable);
    }

    private class MyProcessor extends BaseProcessor {
        private final Processor<Usage> myProcessor;
        private final int myTotalFileCount;

        private int myFileCount;
        private int myMatchCount;

        public MyProcessor(Processor<Usage> processor, int fileCount) {
            myProcessor = processor;
            myTotalFileCount = fileCount;
        }

        protected void processXmlFile(VirtualFile t) {
            myIndicator.setText(findBundleMessage("find.searching.for.string.in.file.progress", myExpression.expression, t.getPresentableUrl()));

            final PsiFile psiFile = myManager.findFile(t);
            if (psiFile instanceof XmlFile) {
                final XmlFile t1 = (XmlFile)psiFile;
                final XmlDocument document;
                FileViewProvider fileViewProvider = t1.getViewProvider();

                if (fileViewProvider instanceof TemplateLanguageFileViewProvider) {
                    final PsiFile root = fileViewProvider.getPsi(((TemplateLanguageFileViewProvider)fileViewProvider).getTemplateDataLanguage());

                    if (root instanceof XmlFile) {
                        document = ((XmlFile)root).getDocument();
                    } else {
                        document = null;
                    }
                } else {
                    document = t1.getDocument();
                }
                if (document != null) {
                    process(document);
                }
            }

            myIndicator.setFraction(++myFileCount / (double)myTotalFileCount);
        }

        private void process(XmlDocument t) {
            try {
                final XmlFile psiFile = (XmlFile)t.getContainingFile();

                final XPath searchPath;
                final Pattern pattern;
                final Context context;
                if (myMatchRecursively) {
                    searchPath = mySupport.createXPath(psiFile, "//*");
                    searchPath.setVariableContext(new CachedVariableContext(myExpression.variables, searchPath, t));
                    pattern = PatternParser.parse(myExpression.expression);

                    final ContextSupport support = new ContextSupport(searchPath.getNamespaceContext(), searchPath.getFunctionContext(), searchPath.getVariableContext(), searchPath.getNavigator());
                    context = new Context(support);
                } else {
                    searchPath = mySupport.createXPath(psiFile, myExpression.expression, myExpression.namespaces);
                    searchPath.setVariableContext(new CachedVariableContext(myExpression.variables, searchPath, t));

                    pattern = null;
                    context = null;
                }

                final Object o = searchPath.evaluate(t);

                if (o instanceof List) {
                    //noinspection unchecked
                    final List<PsiElement> list = ((List<PsiElement>)o);
                    for (PsiElement psiElement : list) {
                        myIndicator.checkCanceled();
                        if (myMatchRecursively) {
                            if (pattern.matches(psiElement, context)) {
                                matchFound();
                                myProcessor.process(new UsageInfo2UsageAdapter(new UsageInfo(psiElement)));
                            }
                        } else {
                            matchFound();
                            myProcessor.process(new UsageInfo2UsageAdapter(new UsageInfo(psiElement)));
                        }
                    }
                } else if (Boolean.TRUE.equals(o)) {
                    matchFound();
                    myProcessor.process(new UsageInfo2UsageAdapter(new UsageInfo(psiFile)));
                } else if (o instanceof Number) {
                    if (((Number)o).intValue() != 0) {
                        matchFound();
                        myProcessor.process(new UsageInfo2UsageAdapter(new UsageInfo(psiFile)));
                    }
                } else if (o instanceof String) {
                    if (((String)o).length() > 0) {
                        matchFound();
                        myProcessor.process(new UsageInfo2UsageAdapter(new UsageInfo(psiFile)));
                    }
                }
            } catch (JaxenException e) {
                Messages.showErrorDialog(myProject, "Error while evaluating XPath:\n" + e.getMessage(), "XPath Error");
            } catch (SAXPathException e) {
                Logger.getInstance(getClass().getName()).error(e);
            }
        }

        private void matchFound() {
            myIndicator.setText2(findBundleMessage("find.searching.for.string.in.file.occurrences.progress", ++myMatchCount));
        }
    }

    private static String findBundleMessage(String s, Object... args) {
        return FindBundle.message(s, args);
    }

    static class CountProcessor extends BaseProcessor {
        private int myFileCount;

        protected void processXmlFile(VirtualFile t) {
            myFileCount++;
        }

        public int getFileCount() {
            return myFileCount;
        }
    }
}
