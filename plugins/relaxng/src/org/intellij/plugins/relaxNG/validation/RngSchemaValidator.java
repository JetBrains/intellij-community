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

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import org.intellij.plugins.relaxNG.ProjectLoader;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.model.resolve.RelaxIncludeIndex;
import org.jetbrains.annotations.NonNls;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.07.2007
 */
public class RngSchemaValidator implements ExternalAnnotator {
  private static final Logger LOG = Logger.getInstance(RngSchemaValidator.class.getName());

  public void annotate(final PsiFile file, final AnnotationHolder holder) {
    final FileType type = file.getFileType();
    if (type != StdFileTypes.XML && type != RncFileType.getInstance()) {
      return;
    }
    final XmlFile xmlfile = (XmlFile)file;
    final XmlDocument document = xmlfile.getDocument();
    if (document == null) {
      return;
    }
    if (type == StdFileTypes.XML) {
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) {
        return;
      }
      if (!ProjectLoader.RNG_NAMESPACE.equals(rootTag.getNamespace())) {
        return;
      }
    } else {
      if (!ApplicationManager.getApplication().isUnitTestMode() && MyErrorFinder.hasError(xmlfile)) {
        return;
      }
    }
    final Document doc = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);

    ErrorHandler eh = new DefaultHandler() {
      public void warning(SAXParseException e) throws SAXException {
        handleError(e, file, doc, new WarningMessageConsumer(holder));
      }

      public void error(SAXParseException e) throws SAXException {
        handleError(e, file, doc, new ErrorMessageConsumer(holder));
      }
    };

    RngParser.parsePattern(file, eh, true);
  }

  public static void handleError(SAXParseException ex, PsiFile file, Document document, ValidationMessageConsumer consumer) {
    final String systemId = ex.getSystemId();

    if (systemId != null) {
      final VirtualFile virtualFile = findVirtualFile(systemId);
      if (virtualFile != file.getVirtualFile()) {
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
      consumer.onMessage(host, ex.getMessage());
    } else {
      consumer.onMessage(file, ex.getMessage());
    }
  }

  public static VirtualFile findVirtualFile(String systemId) {
    try {
      return VfsUtil.findFileByURL(new URL(systemId));
    } catch (Exception e) {
      LOG.warn("Failed to build file from uri <" + systemId + ">", e);
      return VirtualFileManager.getInstance().findFileByUrl(VfsUtil.fixURLforIDEA(systemId));
    }
  }

  public interface ValidationMessageConsumer {
    void onMessage(PsiElement context, String message);
  }

  private static abstract class MessageConsumerImpl implements ValidationMessageConsumer {
    protected final AnnotationHolder myHolder;

    public MessageConsumerImpl(AnnotationHolder holder) {
      myHolder = holder;
    }

    public void onMessage(PsiElement host, String message) {
      final ASTNode node = host.getNode();
      assert node != null;

      if (host instanceof XmlAttribute) {
        final ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
        createAnnotation(nameNode, message);
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

    protected abstract void createAnnotation(ASTNode node, String message);
  }

  private static class ErrorMessageConsumer extends MessageConsumerImpl {
    @NonNls
    private static final String MISSING_START_ELEMENT = "missing \"start\" element";
    private static final String UNDEFINED_PATTERN = "reference to undefined pattern ";

    public ErrorMessageConsumer(AnnotationHolder holder) {
      super(holder);
    }

    protected void createAnnotation(ASTNode node, String message) {
      if (MISSING_START_ELEMENT.equals(message)) {
        final PsiFile psiFile = node.getPsi().getContainingFile();
        if (psiFile instanceof XmlFile) {
          final PsiElementProcessor.FindElement<XmlFile> processor = new PsiElementProcessor.FindElement<XmlFile>();
          RelaxIncludeIndex.processBackwardDependencies((XmlFile)psiFile, processor);
          if (processor.isFound()) {
            // files that are included from other files do not need a <start> element.
            myHolder.createInformationAnnotation(node, message);
            return;
          }
        }
      } else if (message != null && message.startsWith(UNDEFINED_PATTERN)) {
        // we've got our own validation for that
        return;
      }
      myHolder.createErrorAnnotation(node, message);
    }
  }

  private static class WarningMessageConsumer extends MessageConsumerImpl {

    public WarningMessageConsumer(AnnotationHolder holder) {
      super(holder);
    }

    protected void createAnnotation(ASTNode node, String message) {
      myHolder.createWarningAnnotation(node, message);
    }
  }

  private static class MyErrorFinder extends PsiRecursiveElementVisitor {
    private static final MyErrorFinder INSTANCE = new MyErrorFinder();

    private static final class HasError extends RuntimeException {
    }
    private static final HasError FOUND = new HasError();

    public void visitErrorElement(PsiErrorElement element) {
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
