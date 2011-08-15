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

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlElementDescriptor;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.util.UriOrFile;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.auto.AutoSchemaReader;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.thaiopensource.validate.rng.RngProperty;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.model.descriptors.RngElementDescriptor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Future;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 19.11.2007
 */
@SuppressWarnings({ "ComponentNotRegistered" })
public class ValidateAction extends AnAction {
  private static final String CONTENT_NAME = "Validate RELAX NG";
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("VALIDATING");
  private static final Key<Boolean> IN_PROGRESS_KEY = Key.create("VALIDATION IN PROGRESS");

  private final AnAction myOrigAction;

  public ValidateAction(AnAction origAction) {
    myOrigAction = origAction;
    copyFrom(origAction);
    setEnabledInModalContext(origAction.isEnabledInModalContext());
  }

  public void actionPerformed(AnActionEvent e) {
    if (!actionPerformedImpl(e)) {
      myOrigAction.actionPerformed(e);
    }
  }

  public final void update(AnActionEvent e) {
    super.update(e);
    myOrigAction.update(e);

    final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (file != null) {
      if (file.getUserData(IN_PROGRESS_KEY) == Boolean.TRUE) {
        e.getPresentation().setEnabled(false);
      }
    }
  }

  private boolean actionPerformedImpl(AnActionEvent e) {
    final PsiFile file = e.getData(LangDataKeys.PSI_FILE);
    if (file == null) {
      return false;
    }
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return false;
    }

    final RngElementDescriptor descriptor = getRootDescriptor(file);
    if (descriptor == null) return false;

    final PsiElement element = descriptor.getDeclaration();
    final XmlFile xmlFile = PsiTreeUtil.getParentOfType(element, XmlFile.class);
    if (xmlFile == null) return false;

    final VirtualFile instanceFile = file.getVirtualFile();
    final VirtualFile schemaFile = xmlFile.getVirtualFile();
    if (instanceFile == null || schemaFile == null) {
      return true;
    }

    doRun(project, instanceFile, schemaFile);

    return true;
  }

  private static void doRun(final Project project, final VirtualFile instanceFile, final VirtualFile schemaFile) {
    saveFiles(instanceFile, schemaFile);

    final MessageViewHelper helper = new MessageViewHelper(project, CONTENT_NAME, KEY);

    helper.openMessageView(new Runnable() {
      public void run() {
        doRun(project, instanceFile, schemaFile);
      }
    });

    final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final MessageViewHelper.ErrorHandler eh = helper.new ErrorHandler();

            instanceFile.putUserData(IN_PROGRESS_KEY, Boolean.TRUE);
            try {
              doValidation(instanceFile, schemaFile, eh);
            } finally {
              instanceFile.putUserData(IN_PROGRESS_KEY, null);
            }

            SwingUtilities.invokeLater(
              new Runnable() {
                  public void run() {
                    if (!eh.hadErrorOrWarning()) {
                      SwingUtilities.invokeLater(
                          new Runnable() {
                            public void run() {
                              helper.close();
                              WindowManager.getInstance().getStatusBar(project).setInfo("No errors detected");
                            }
                          }
                      );
                    }
                  }
                }
            );
          }
        });
      }
    });

    helper.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
      public void stopProcess() {
        future.cancel(true);
      }

      public boolean isProcessStopped() {
        return future.isDone();
      }
    });
  }

  @SuppressWarnings({ "ThrowableInstanceNeverThrown" })
  private static void doValidation(VirtualFile instanceFile, VirtualFile schemaFile, org.xml.sax.ErrorHandler eh) {
    final SchemaReader sr = schemaFile.getFileType() == RncFileType.getInstance() ?
            CompactSchemaReader.getInstance() :
            new AutoSchemaReader();

    final PropertyMapBuilder properties = new PropertyMapBuilder();
    ValidateProperty.ERROR_HANDLER.put(properties, eh);

    // TODO: should some options dialog displayed before validating?
    RngProperty.CHECK_ID_IDREF.add(properties);

    try {
      final String schemaPath = RngParser.reallyFixIDEAUrl(schemaFile.getUrl());
      try {
        final ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), sr);
        final InputSource in = ValidationDriver.uriOrFileInputSource(schemaPath);
        in.setEncoding(schemaFile.getCharset().name());

        if (driver.loadSchema(in)) {
          final String path = RngParser.reallyFixIDEAUrl(instanceFile.getUrl());
          try {
            driver.validate(ValidationDriver.uriOrFileInputSource(path));
          } catch (IOException e1) {
            eh.fatalError(new SAXParseException(e1.getMessage(), null, UriOrFile.fileToUri(path), -1, -1, e1));
          }
        }
      } catch (SAXParseException e1) {
        eh.fatalError(e1);
      } catch (IOException e1) {
        eh.fatalError(new SAXParseException(e1.getMessage(), null, UriOrFile.fileToUri(schemaPath), -1, -1, e1));
      }
    } catch (SAXException e1) {
      // huh?
      Logger.getInstance(ValidateAction.class.getName()).error(e1);
    } catch (MalformedURLException e1) {
      Logger.getInstance(ValidateAction.class.getName()).error(e1);
    }
  }

  private static RngElementDescriptor getRootDescriptor(PsiFile file) {
    try {
      if (file instanceof XmlFile) {
        final XmlElementDescriptor descriptor = ((XmlFile)file).getDocument().getRootTag().getDescriptor();
        if (descriptor instanceof RngElementDescriptor) {
          return (RngElementDescriptor)descriptor;
        }
      }
    } catch (NullPointerException e1) {
      // OK
    }
    return null;
  }

  public boolean displayTextInToolbar() {
    return myOrigAction.displayTextInToolbar();
  }

  public void setDefaultIcon(boolean b) {
    myOrigAction.setDefaultIcon(b);
  }

  public boolean isDefaultIcon() {
    return myOrigAction.isDefaultIcon();
  }

  public void setInjectedContext(boolean worksInInjected) {
    myOrigAction.setInjectedContext(worksInInjected);
  }

  public boolean isInInjectedContext() {
    return myOrigAction.isInInjectedContext();
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
