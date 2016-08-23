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
package org.intellij.lang.xpath.psi.impl;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.xslt.impl.XsltIncludeIndex;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ResolveUtil {

    private final Set<PsiElement> myHistory = ContainerUtil.newIdentityTroveSet();

    private ResolveUtil() {
    }

    @Nullable
    public static Collection<XmlFile> getDependencies(XmlFile element) {
      final CommonProcessors.CollectUniquesProcessor<XmlFile> processor = new CommonProcessors.CollectUniquesProcessor<XmlFile>() {
        @Override
        public boolean process(XmlFile file) {
          if (!getResults().contains(file)) {
            XsltIncludeIndex.processForwardDependencies(file, this);
          }
          return super.process(file);
        }
      };
      XsltIncludeIndex.processForwardDependencies(element, processor);
      return processor.getResults();
    }

    @Nullable
    public static PsiFile resolveFile(String name, PsiFile baseFile) {
        if (baseFile == null) return null;

        final VirtualFile virtualFile = VfsUtil.findRelativeFile(name, baseFile.getVirtualFile());
        if (virtualFile != null) {
            final PsiFile file = baseFile.getManager().findFile(virtualFile);
            if (file != baseFile && file instanceof XmlFile) {
                return file;
            }
        }
        return null;
    }

    @Nullable
    public static PsiFile resolveFile(XmlAttribute location, PsiFile baseFile) {
        if (location == null) return null;
        final XmlAttributeValue valueElement = location.getValueElement();
        if (valueElement == null) return null;

        // prefer direct relative path
        final String value = valueElement.getValue();
        final PsiFile file = resolveFile(value, baseFile);
        if (file != baseFile && file instanceof XmlFile) {
            return file;
        }

        final PsiReference[] references = valueElement.getReferences();
        for (PsiReference reference : references) {
            final PsiElement target = reference.resolve();
            if (target == null && reference instanceof PsiPolyVariantReference) {
                final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
                for (ResolveResult result : results) {
                    if (result.isValidResult()) {
                        // TODO: how to weigh/prioritize the results? 
                        final PsiElement element = result.getElement();
                        if (element != baseFile && element instanceof XmlFile) {
                            return (PsiFile)target;
                        }
                    }
                }
            } else if (target != baseFile && target instanceof XmlFile) {
                return (PsiFile)target;
            }
        }
        return null;
    }

    public interface Matcher {
        @Nullable
        XmlTag getRoot();

        boolean isRecursive();

        @Nullable
        Result match(XmlTag element);

        Matcher variantMatcher();

        class Result {
            final PsiElement result;
            final Matcher chain;

            public Result(PsiElement element) { result = element; chain = null; }
            public Result(Matcher matcher) { chain = matcher; result = null; }
            public static Result create(PsiElement element) { return new Result(element); }
            public static Result create(Matcher matcher) { return new Result(matcher); }
        }
    }

    @Nullable
    public static PsiElement resolve(final Matcher matcher) {
        if (matcher == null) return null;
        final List<PsiElement> found = process(matcher, true);
        return found.size() > 0 ? found.get(0) : null;
    }

    public static PsiElement[] collect(final Matcher matcher) {
        if (matcher == null) return PsiElement.EMPTY_ARRAY;
        final List<PsiElement> found = process(matcher, false);
      return PsiUtilCore.toPsiElementArray(found);
    }

    private static class Stop extends RuntimeException {
        public static final Stop DONE = new Stop();
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static List<PsiElement> process(final Matcher matcher, final boolean resolve) {
        return new ResolveUtil()._process(matcher, resolve);
    }

    private List<PsiElement> _process(final Matcher matcher, final boolean resolve) {
        final XmlTag root = matcher.getRoot();
        if (root == null || myHistory.contains(root)) {
            return Collections.emptyList();
        }
        myHistory.add(root);
        final List<PsiElement> found = new ArrayList<>();

        try {
            if (matcher.isRecursive()) {
                root.accept(new XmlRecursiveElementVisitor(){
                    @Override
                    public void visitXmlTag(XmlTag tag) {
                        final Matcher.Result match = matcher.match(tag);
                        if (match != null) {
                            if (match.chain != null) {
                                found.addAll(_process(match.chain, resolve));
                            } else {
                                assert match.result != null;
                                found.add(match.result);
                                if (resolve) throw Stop.DONE;
                            }
                        }
                        super.visitXmlTag(tag);
                    }
                });
            } else {
                root.acceptChildren(new XmlElementVisitor() {

                    @Override
                    public void visitXmlTag(XmlTag tag) {
                        final Matcher.Result match = matcher.match(tag);
                        if (match != null) {
                            if (match.chain != null) {
                                found.addAll(_process(match.chain, resolve));
                            } else {
                                assert match.result != null;
                                found.add(match.result);
                                if (resolve) throw Stop.DONE;
                            }
                        }
                    }
                });
            }
        } catch (Stop e) {
            /* processing stopped */
        }
        return found;
    }

    public interface XmlProcessor extends Processor<XmlTag> {
        boolean process(XmlTag tag);
    }

    public interface ResolveProcessor extends XmlProcessor {
        PsiElement getResult();
    }

    @Nullable
    public static PsiElement treeWalkUp(final XmlProcessor processor, PsiElement elt) {
        if (elt == null) return null;

        PsiElement cur = elt;
        do {
            if (cur instanceof XmlTag) {
                final XmlTag tag = (XmlTag)cur;
                if (!processor.process(tag)) {
                    if (processor instanceof ResolveProcessor) {
                        return ((ResolveProcessor)processor).getResult();
                    }
                    return null;
                }
            }

            if (cur instanceof PsiFile) break;
            cur = PsiTreeUtil.getPrevSiblingOfType(cur, XmlTag.class);
        } while (cur != null);

        return treeWalkUp(processor, elt.getContext());
    }
}
