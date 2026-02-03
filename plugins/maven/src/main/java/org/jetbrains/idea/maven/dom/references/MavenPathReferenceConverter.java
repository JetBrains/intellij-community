// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.converters.PathReferenceConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class MavenPathReferenceConverter extends PathReferenceConverter {

  private final Condition<PsiFileSystemItem> myCondition;

  public MavenPathReferenceConverter() {
    this(Conditions.alwaysTrue());
  }

  public MavenPathReferenceConverter(@NotNull Condition<PsiFileSystemItem> condition) {
    myCondition = condition;
  }

  public static PsiReference[] createReferences(final DomElement genericDomValue,
                                                PsiElement element,
                                                final @NotNull Condition<PsiFileSystemItem> fileFilter) {
    return createReferences(genericDomValue, element, fileFilter, false);
  }

  public static PsiReference[] createReferences(final DomElement genericDomValue,
                                                PsiElement element,
                                                final @NotNull Condition<PsiFileSystemItem> fileFilter, boolean isAbsolutePath) {
    TextRange range = ElementManipulators.getValueTextRange(element);
    String text = range.substring(element.getText());

    FileReferenceSet set = new FileReferenceSet(text, element, range.getStartOffset(), null, element.getContainingFile().getViewProvider().getVirtualFile().isCaseSensitive(), false) {

      private MavenDomProjectModel model;

      @Override
      protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
        return fileFilter;
      }

      @Override
      protected boolean isSoft() {
        return true;
      }

      @Override
      public FileReference createFileReference(TextRange range, int index, String text) {
        return new FileReference(this, range, index, text) {
          @Override
          protected void innerResolveInContext(@NotNull String text,
                                               @NotNull PsiFileSystemItem context,
                                               @NotNull Collection<? super ResolveResult> result,
                                               boolean caseSensitive) {
            if (model == null) {
              DomElement rootElement = DomUtil.getFileElement(genericDomValue).getRootElement();
              if (rootElement instanceof MavenDomProjectModel) {
                model = (MavenDomProjectModel)rootElement;
              }
            }

            String resolvedText = model == null ? text : MavenPropertyResolver.resolve(text, model);

            if (resolvedText.equals(text)) {
              if (getIndex() == 0 && resolvedText.length() == 2 && OSAgnosticPathUtil.startsWithWindowsDrive(resolvedText)) {
                // it's root on windows, e.g. "C:"
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(resolvedText + '/');
                if (file != null) {
                  PsiDirectory psiDirectory = context.getManager().findDirectory(file);
                  if (psiDirectory != null) {
                    result.add(new PsiElementResolveResult(psiDirectory));
                  }
                }
              }
              else if (getIndex() == getAllReferences().length - 1 &&
                       Objects.equals("relativePath", genericDomValue.getXmlElementName()) &&
                       context.getVirtualFile() != null) {
                // it is a last context and should be resolved to pom.xml

                VirtualFile parentFile = context.getVirtualFile().findChild(text);
                if (parentFile != null) {
                  VirtualFile parentPom = parentFile.isDirectory() ? parentFile.findChild("pom.xml") : parentFile;
                  if (parentPom != null) {
                    PsiFile psiFile = context.getManager().findFile(parentPom);
                    if (psiFile != null) {
                      result.add(new PsiElementResolveResult(psiFile));
                    }
                  }
                }
              }
              else if ("..".equals(resolvedText)) {
                PsiFileSystemItem resolved = context.getParent();
                if (resolved != null) {
                  if (context instanceof XmlFileImpl) {
                    resolved = resolved.getParent();  // calculated regarding parent directory, not the pom itself
                  }
                  if (resolved != null) {
                  result.add(new PsiElementResolveResult(resolved));
                  }
                }
              }
              else {
                var systemIndependentResolvedText = FileUtil.toSystemIndependentName(resolvedText);
                super.innerResolveInContext(systemIndependentResolvedText, context, result, caseSensitive);
              }
            }
            else {
              VirtualFile contextFile = context.getVirtualFile();
              if (contextFile == null) return;

              VirtualFile file = null;

              if (getIndex() == 0) {
                file = LocalFileSystem.getInstance().findFileByPath(resolvedText);
              }

              if (file == null) {
                file = LocalFileSystem.getInstance().findFileByPath(contextFile.getPath() + '/' + resolvedText);
              }

              if (file != null) {
                PsiFileSystemItem res = file.isDirectory() ? context.getManager().findDirectory(file) : context.getManager().findFile(file);

                if (res != null) {
                  result.add(new PsiElementResolveResult(res));
                }
              }
            }
          }
        };
      }
    };

    if (isAbsolutePath) {
      set.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, file -> {
        VirtualFile virtualFile = file.getVirtualFile();

        if (virtualFile == null) {
          return FileReferenceSet.ABSOLUTE_TOP_LEVEL.fun(file);
        }

        virtualFile = VfsUtil.getRootFile(virtualFile);
        PsiDirectory root = file.getManager().findDirectory(virtualFile);

        if (root == null) {
          return FileReferenceSet.ABSOLUTE_TOP_LEVEL.fun(file);
        }

        return Collections.singletonList(root);
      });
    }

    return set.getAllReferences();
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue genericDomValue, PsiElement element, ConvertContext context) {
    return createReferences(genericDomValue, element, myCondition);
  }

  @Override
  public PsiReference @NotNull [] createReferences(@NotNull PsiElement psiElement, boolean soft) {
    throw new UnsupportedOperationException();
  }
}
