/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.validation;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import org.intellij.plugins.relaxNG.RelaxNgMetaDataContributor;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.model.resolve.RelaxIncludeIndex;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class RngSchemaValidator extends ExternalAnnotator<RngSchemaValidator.MyValidationMessageConsumer,RngSchemaValidator.MyValidationMessageConsumer> {
  private static final Logger LOG = Logger.getInstance(RngSchemaValidator.class.getName());

  @Override
  public @Nullable MyValidationMessageConsumer collectInformation(final @NotNull PsiFile psiFile) {
    final FileType type = psiFile.getFileType();
    if (type != XmlFileType.INSTANCE && type != RncFileType.getInstance()) {
      return null;
    }
    final XmlFile xmlfile = (XmlFile)psiFile;
    final XmlDocument document = xmlfile.getDocument();
    if (document == null) {
      return null;
    }
    if (type == XmlFileType.INSTANCE) {
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) {
        return null;
      }
      if (!RelaxNgMetaDataContributor.RNG_NAMESPACE.equals(rootTag.getNamespace())) {
        return null;
      }
    } else {
      if (!ApplicationManager.getApplication().isUnitTestMode() && MyErrorFinder.hasError(xmlfile)) {
        return null;
      }
    }
    final Document doc = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);

    final MyValidationMessageConsumer consumer = new MyValidationMessageConsumer();
    ErrorHandler eh = new DefaultHandler() {
      @Override
      public void warning(SAXParseException e) {
        handleError(e, psiFile, doc, consumer.warning());
      }

      @Override
      public void error(SAXParseException e) {
        handleError(e, psiFile, doc, consumer.error());
      }
    };

    RngParser.parsePattern(psiFile, eh, true);
    return consumer;
  }

  @Override
  public @Nullable MyValidationMessageConsumer doAnnotate(MyValidationMessageConsumer collectedInfo) {
    return collectedInfo;
  }

  @Override
  public void apply(@NotNull PsiFile psiFile,
                    MyValidationMessageConsumer annotationResult,
                    @NotNull AnnotationHolder holder) {
    annotationResult.apply(holder);
  }

  static class MyValidationMessageConsumer  {
    final List<Pair<PsiElement, @InspectionMessage String >> errors = new ArrayList<>();
    final List<Pair<PsiElement, @InspectionMessage String >> warnings = new ArrayList<>();
    ValidationMessageConsumer error() {
      return new ValidationMessageConsumer() {
        @Override
        public void onMessage(@NotNull PsiElement context, @NotNull String message) {
          errors.add(Pair.create(context, message));
        }
      };
    }
    ValidationMessageConsumer warning() {
      return new ValidationMessageConsumer() {
        @Override
        public void onMessage(@NotNull PsiElement context, @NotNull String message) {
          warnings.add(Pair.create(context, message));
        }
      };
    }
    void apply(AnnotationHolder holder) {
      MessageConsumerImpl errorc = new ErrorMessageConsumer(holder);
      MessageConsumerImpl warningc = new WarningMessageConsumer(holder);
      for (Pair<PsiElement, @InspectionMessage String> error : errors) {
        errorc.onMessage(error.first, error.second);
      }
      for (Pair<PsiElement, @InspectionMessage String> warning : warnings) {
        warningc.onMessage(warning.first, warning.second);
      }
    }
  }

  public static void handleError(SAXParseException ex, PsiFile file, Document document, ValidationMessageConsumer consumer) {
    final String systemId = ex.getSystemId();
    if (LOG.isDebugEnabled()) {
      LOG.debug("RNG Schema error: " + ex.getMessage() + " [" + systemId + "]");
    }

    if (systemId != null) {
      final VirtualFile virtualFile = findVirtualFile(systemId);
      if (!Comparing.equal(virtualFile, file.getVirtualFile())) {
        return;
      }
    }

    final PsiElement at;
    final int line = ex.getLineNumber();
    if (line > 0) {
      final int column = ex.getColumnNumber();
      final int startOffset = document.getLineStartOffset(line - 1);

      if (column > 0) {
        if (file.getFileType() == RncFileType.getInstance()) {
          final PsiElement e = file.findElementAt(startOffset + column);
          if (e == null) {
            at = e;
          } else {
            at = file.findElementAt(startOffset + column - 1);
          }
        } else {
          at = file.findElementAt(startOffset + column - 2);
        }
      } else {
        final PsiElement e = file.findElementAt(startOffset);
        at = e != null ? PsiTreeUtil.nextLeaf(e) : null;
      }
    } else {
      final XmlDocument d = ((XmlFile)file).getDocument();
      assert d != null;
      final XmlTag rootTag = d.getRootTag();
      assert rootTag != null;
      at = rootTag.getFirstChild();
    }

    final PsiElement host;
    if (file instanceof RncFile) {
      host = at;
    } else {
      host = PsiTreeUtil.getParentOfType(at, XmlAttribute.class, XmlTag.class);
    }
    if (at != null && host != null) {
      consumer.onMessage(host, ex.getLocalizedMessage());
    } else {
      consumer.onMessage(file, ex.getLocalizedMessage());
    }
  }

  public static VirtualFile findVirtualFile(String systemId) {
    try {
      return VfsUtil.findFileByURL(new URL(systemId));
    } catch (Exception e) {
      LOG.warn("Failed to build file from uri <" + systemId + ">", e);
      return VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.fixURLforIDEA(systemId));
    }
  }

  public interface ValidationMessageConsumer {
    void onMessage(@NotNull PsiElement context, @InspectionMessage @NotNull String message);
  }

  private abstract static class MessageConsumerImpl implements ValidationMessageConsumer {
    protected final AnnotationHolder myHolder;

    MessageConsumerImpl(AnnotationHolder holder) {
      myHolder = holder;
    }

    @Override
    public void onMessage(@NotNull PsiElement host, @NotNull String message) {
      final ASTNode node = host.getNode();
      assert node != null;

      if (host instanceof XmlAttribute) {
        final ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
        if (nameNode != null) {
          createAnnotation(nameNode, message);
        }
      } else if (host instanceof XmlTag) {
        final ASTNode start = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
        if (start != null) {
          createAnnotation(start, message);
        }

        final ASTNode end = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(node);
        if (end != null) {
          createAnnotation(end, message);
        }
      } else {
        createAnnotation(node, message);
      }
    }

    protected abstract void createAnnotation(@NotNull ASTNode node, @NotNull @InspectionMessage String message);
  }

  private static class ErrorMessageConsumer extends MessageConsumerImpl {
    private static final @NonNls String MISSING_START_ELEMENT = "missing \"start\" element";
    private static final String UNDEFINED_PATTERN = "reference to undefined pattern ";

    ErrorMessageConsumer(AnnotationHolder holder) {
      super(holder);
    }

    @Override
    protected void createAnnotation(@NotNull ASTNode node, @NotNull String message) {
      if (MISSING_START_ELEMENT.equals(message)) {
        final PsiFile psiFile = node.getPsi().getContainingFile();
        if (psiFile instanceof XmlFile) {
          final PsiElementProcessor.FindElement<XmlFile> processor = new PsiElementProcessor.FindElement<>();
          RelaxIncludeIndex.processBackwardDependencies((XmlFile)psiFile, processor);
          if (processor.isFound()) {
            // files that are included from other files do not need a <start> element.
            myHolder.newAnnotation(HighlightSeverity.WEAK_WARNING, message).range(node).create();
            return;
          }
        }
      } else if (message.startsWith(UNDEFINED_PATTERN)) {
        // we've got our own validation for that
        return;
      }
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(node).create();
    }
  }

  private static class WarningMessageConsumer extends MessageConsumerImpl {

    WarningMessageConsumer(AnnotationHolder holder) {
      super(holder);
    }

    @Override
    protected void createAnnotation(@NotNull ASTNode node, @NotNull String message) {
      myHolder.newAnnotation(HighlightSeverity.WARNING, message).range(node).create();
    }
  }

  private static final class MyErrorFinder extends PsiRecursiveElementVisitor {
    private static final MyErrorFinder INSTANCE = new MyErrorFinder();

    private static final class HasError extends RuntimeException {
    }
    private static final HasError FOUND = new HasError();

    @Override
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      throw FOUND;
    }

    public static boolean hasError(PsiElement element) {
      try {
        element.accept(INSTANCE);
        return false;
      } catch (HasError e) {
        return true;
      }
    }
  }

}
