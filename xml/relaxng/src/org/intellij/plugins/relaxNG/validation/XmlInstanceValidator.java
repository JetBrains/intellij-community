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

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.validate.ValidateProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public final class XmlInstanceValidator {
  private static final Logger LOG = Logger.getInstance(XmlInstanceValidator.class);

  private XmlInstanceValidator() {
  }

  public static void doValidation(@NotNull final XmlDocument doc, final Validator.ValidationHost host, final XmlFile descriptorFile) {
    try {
      final Schema schema = RngParser.getCachedSchema(descriptorFile);
      if (schema == null) {
        // did not manage to get a compiled schema. no validation...
        return;
      }

      final ErrorHandler eh = MyErrorHandler.create(doc, host);
      if (eh == null) {
        return;
      }

      final PropertyMapBuilder builder = new PropertyMapBuilder();
      builder.put(ValidateProperty.ERROR_HANDLER, eh);

      final ContentHandler handler = schema.createValidator(builder.toPropertyMap()).getContentHandler();
      doc.accept(new Psi2SaxAdapter(handler));

    } catch (ProcessCanceledException e) {
      throw e;
    } catch (RuntimeException e) {
      LOG.error(e);
    } catch (Exception e) {
      LOG.info(e);
    }
  }

  private static final class MyErrorHandler implements ErrorHandler {
    private final Validator.ValidationHost myHost;
    private final Document myDocument;
    private final PsiFile myFile;

    private MyErrorHandler(XmlDocument doc, Validator.ValidationHost host) {
      myHost = host;
      myFile = doc.getContainingFile();
      myDocument = PsiDocumentManager.getInstance(myFile.getProject()).getDocument(myFile);
    }

    @Override
    public void warning(SAXParseException exception) {
      RngSchemaValidator.handleError(exception, myFile, myDocument, new RngSchemaValidator.ValidationMessageConsumer() {
        @Override
        public void onMessage(@NotNull PsiElement context, @NotNull String message) {
          myHost.addMessage(context, message, Validator.ValidationHost.ErrorType.WARNING);
        }
      });
    }

    @Override
    public void error(SAXParseException exception) {
      RngSchemaValidator.handleError(exception, myFile, myDocument, new RngSchemaValidator.ValidationMessageConsumer() {
        @Override
        public void onMessage(@NotNull PsiElement context, @NotNull String message) {
          myHost.addMessage(context, message, Validator.ValidationHost.ErrorType.ERROR);
        }
      });
    }

    @Override
    public void fatalError(SAXParseException exception) {
      RngSchemaValidator.handleError(exception, myFile, myDocument, new RngSchemaValidator.ValidationMessageConsumer() {
        @Override
        public void onMessage(@NotNull PsiElement context, @NotNull String message) {
          myHost.addMessage(context, message, Validator.ValidationHost.ErrorType.ERROR);
        }
      });
    }

    @Nullable
    public static ErrorHandler create(XmlDocument doc, Validator.ValidationHost host) {
      final XmlTag rootTag = doc.getRootTag();
      if (rootTag == null) {
        return null;
      }
      return new MyErrorHandler(doc, host);
    }
  }
}
