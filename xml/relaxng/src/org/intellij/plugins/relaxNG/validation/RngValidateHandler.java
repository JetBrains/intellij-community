// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG.validation;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.actions.validate.ValidateXmlHandler;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.util.UriOrFile;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.auto.AutoSchemaReader;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.thaiopensource.validate.rng.RngProperty;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.model.descriptors.RngElementDescriptor;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Future;

public class RngValidateHandler implements ValidateXmlHandler {
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("VALIDATING");

  @Override
  public void doValidate(XmlFile file) {
    final XmlFile schema = getRngSchema(file);
    if (schema == null) return;

    final VirtualFile instanceFile = file.getVirtualFile();
    final VirtualFile schemaFile = schema.getVirtualFile();
    if (instanceFile == null || schemaFile == null) {
      return;
    }

    doRun(file.getProject(), instanceFile, schemaFile);
  }

  @Override
  public boolean isAvailable(XmlFile file) {
    return getRngSchema(file) != null;
  }

  private static @Nullable XmlFile getRngSchema(XmlFile file) {
    final RngElementDescriptor descriptor = getRootDescriptor(file);
    if (descriptor == null) return null;

    final PsiElement element = descriptor.getDeclaration();
    final XmlFile schema = PsiTreeUtil.getParentOfType(element, XmlFile.class);
    if (schema == null) return null;
    return schema;
  }

  private static RngElementDescriptor getRootDescriptor(PsiFile file) {
    try {
      if (file instanceof XmlFile) {
        final XmlElementDescriptor descriptor = ((XmlFile)file).getDocument().getRootTag().getDescriptor();
        if (descriptor instanceof RngElementDescriptor) {
          return (RngElementDescriptor)descriptor;
        }
      }
    }
    catch (NullPointerException e1) {
      // OK
    }
    return null;
  }

  private static void doRun(final Project project, final VirtualFile instanceFile, final VirtualFile schemaFile) {
    saveFiles(instanceFile, schemaFile);

    final MessageViewHelper helper = new MessageViewHelper(
      project, RelaxngBundle.message("relaxng.message-viewer.tab-title.validate-relax-ng"), KEY);

    helper.openMessageView(() -> doRun(project, instanceFile, schemaFile));

    final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(
      () -> ApplicationManager.getApplication().runReadAction(() -> {
        final MessageViewHelper.ErrorHandler eh = helper.new ErrorHandler();

        doValidation(instanceFile, schemaFile, eh);

        SwingUtilities.invokeLater(
          () -> {
            if (!eh.hadErrorOrWarning()) {
              SwingUtilities.invokeLater(
                () -> {
                  helper.close();
                  WindowManager.getInstance().getStatusBar(project).setInfo(
                    XmlBundle.message("xml.validate.no.errors.detected.status.message"));
                }
              );
            }
          }
        );
      }));

    helper.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
      @Override
      public void stopProcess() {
        future.cancel(true);
      }

      @Override
      public boolean isProcessStopped() {
        return future.isDone();
      }
    });
  }

  private static void doValidation(VirtualFile instanceFile, VirtualFile schemaFile, org.xml.sax.ErrorHandler eh) {
    final SchemaReader sr = FileTypeRegistry.getInstance().isFileOfType(schemaFile, RncFileType.getInstance()) ?
                            CompactSchemaReader.getInstance() :
                            new AutoSchemaReader();

    final PropertyMapBuilder properties = new PropertyMapBuilder();
    ValidateProperty.ERROR_HANDLER.put(properties, eh);

    // TODO: should some options dialog displayed before validating?
    RngProperty.CHECK_ID_IDREF.add(properties);

    try {
      final String schemaPath = VfsUtilCore.fixIDEAUrl(schemaFile.getUrl());
      try {
        final ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), sr);
        final InputSource in = ValidationDriver.uriOrFileInputSource(schemaPath);
        in.setEncoding(schemaFile.getCharset().name());

        if (driver.loadSchema(in)) {
          final String path = VfsUtilCore.fixIDEAUrl(instanceFile.getUrl());
          try {
            driver.validate(ValidationDriver.uriOrFileInputSource(path));
          }
          catch (IOException e1) {
            eh.fatalError(new SAXParseException(e1.getMessage(), null, UriOrFile.fileToUri(path), -1, -1, e1));
          }
        }
      }
      catch (SAXParseException e1) {
        eh.fatalError(e1);
      }
      catch (IOException e1) {
        eh.fatalError(new SAXParseException(e1.getMessage(), null, UriOrFile.fileToUri(schemaPath), -1, -1, e1));
      }
    }
    catch (SAXException | MalformedURLException e1) {
      // huh?
      Logger.getInstance(RngValidateHandler.class.getName()).error(e1);
    }
  }

  public static void saveFiles(VirtualFile... files) {
    // ensure the validation/conversion runs on the current content
    final FileDocumentManager mgr = FileDocumentManager.getInstance();
    for (VirtualFile f : files) {
      final Document document = mgr.getDocument(f);
      if (document != null) {
        mgr.saveDocument(document);
      }
    }
  }
}
