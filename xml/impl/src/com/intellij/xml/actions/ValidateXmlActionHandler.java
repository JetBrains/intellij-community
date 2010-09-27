/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.actions;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.javaee.UriUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.*;
import com.intellij.ui.content.*;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.XmlResourceResolver;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.jaxp.JAXPConstants;
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.apache.xerces.util.XMLGrammarPoolImpl;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * @author Mike
 */
public class ValidateXmlActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.actions.ValidateXmlAction");
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("ValidateXmlAction.KEY");
  @NonNls private static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";
  private static final String GRAMMAR_FEATURE_ID = Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;
  private static final Key<XMLGrammarPool> GRAMMAR_POOL_KEY = Key.create("GrammarPoolKey");
  private static final Key<Long> GRAMMAR_POOL_TIME_STAMP_KEY = Key.create("GrammarPoolTimeStampKey");
  private static final Key<VirtualFile[]> DEPENDENT_FILES_KEY = Key.create("GrammarPoolFilesKey");

  private Project myProject;
  private XmlFile myFile;
  private ErrorReporter myErrorReporter;
  private SAXParser myParser;
  private XmlResourceResolver myXmlResourceResolver;
  private final boolean myForceChecking;
  @NonNls
  private static final String ENTITY_RESOLVER_PROPERTY_NAME = "http://apache.org/xml/properties/internal/entity-resolver";

  public ValidateXmlActionHandler(boolean _forceChecking) {
    myForceChecking = _forceChecking;
  }

  public void setErrorReporter(ErrorReporter errorReporter) {
    myErrorReporter = errorReporter;
  }

  public VirtualFile getFile(String publicId, String systemId) {
    if (publicId == null) {
      if (systemId != null) {
        final String path = myXmlResourceResolver.getPathByPublicId(systemId);
        if (path != null) return UriUtil.findRelativeFile(path,null);
        final PsiFile file = myXmlResourceResolver.resolve(null, systemId);
        if (file != null) return file.getVirtualFile();
      }
      return myFile.getVirtualFile();
    }
    final String path = myXmlResourceResolver.getPathByPublicId(publicId);
    if (path != null) return UriUtil.findRelativeFile(path,null);
    return null;
  }

  public abstract class ErrorReporter {
    protected final Set<String> ourErrorsSet = new HashSet<String>();
    public abstract void processError(SAXParseException ex,boolean warning);

    public boolean filterValidationException(Exception ex) {
      if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
      if (ex instanceof XmlResourceResolver.IgnoredResourceException) throw (XmlResourceResolver.IgnoredResourceException)ex;

      if (ex instanceof FileNotFoundException ||
            ex instanceof MalformedURLException ||
            ex instanceof NoRouteToHostException ||
            ex instanceof SocketTimeoutException ||
            ex instanceof UnknownHostException ||
            ex instanceof ConnectException
            ) {
        // do not log problems caused by malformed and/or ignored external resources
        return true;
      }

      if (ex instanceof NullPointerException) {
        return true; // workaround for NPE at org.apache.xerces.impl.dtd.XMLDTDProcessor.checkDeclaredElements
      }

      return false;
    }

    public void startProcessing() {
      doParse();
    }

    public boolean isStopOnUndeclaredResource() {
      return false;
    }

    public boolean isUniqueProblem(final SAXParseException e) {
      String error = buildMessageString(e);
      if (ourErrorsSet.contains(error)) return false;
      ourErrorsSet.add(error);
      return true;
    }
  }

  private String buildMessageString(SAXParseException ex) {
    String msg = "(" + ex.getLineNumber() + ":" + ex.getColumnNumber() + ") " + ex.getMessage();
    final VirtualFile file = getFile(ex.getPublicId(), ex.getSystemId());

    if ( file != null && !file.equals(myFile.getVirtualFile())) {
      msg = file.getName() + ":" + msg;
    }
    return msg;
  }

  public class TestErrorReporter extends ErrorReporter {
    private final ArrayList<String> errors = new ArrayList<String>(3);

    public boolean isStopOnUndeclaredResource() {
      return true;
    }

    public boolean filterValidationException(final Exception ex) {
      if (ex instanceof XmlResourceResolver.IgnoredResourceException) throw (XmlResourceResolver.IgnoredResourceException)ex;
      return errors.add(ex.getMessage());
    }

    public void processError(SAXParseException ex, boolean warning) {
      errors.add(buildMessageString(ex));
    }

    public List<String> getErrors() {
      return errors;
    }
  }

  class StdErrorReporter extends ErrorReporter {
    private final NewErrorTreeViewPanel myErrorsView;
    private final String CONTENT_NAME = XmlBundle.message("xml.validate.tab.content.title");
    private boolean myErrorsDetected = false;

    StdErrorReporter(Project project, Runnable rerunAction) {
      myErrorsView = new NewErrorTreeViewPanel(project, null, true, true, rerunAction);
    }

    public void startProcessing() {
      final Runnable task = new Runnable() {
        public void run() {
          try {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                StdErrorReporter.super.startProcessing();
              }
            });

            SwingUtilities.invokeLater(
              new Runnable() {
                  public void run() {
                    if (!myErrorsDetected) {
                      SwingUtilities.invokeLater(
                          new Runnable() {
                            public void run() {
                              removeCompileContents(null);
                              WindowManager.getInstance().getStatusBar(myProject).setInfo(
                                XmlBundle.message("xml.validate.no.errors.detected.status.message"));
                            }
                          }
                      );
                    }
                  }
                }
            );
          }
          finally {
            boolean b = Thread.interrupted(); // reset interrupted
          }
        }
      };

      final MyProcessController processController = new MyProcessController();
      myErrorsView.setProcessController(processController);
      openMessageView();
      processController.setFuture( ApplicationManager.getApplication().executeOnPooledThread(task) );

      ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
    }

    private void openMessageView() {
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.executeCommand(
          myProject, new Runnable() {
            public void run() {
              MessageView messageView = MessageView.SERVICE.getInstance(myProject);
              final Content content = ContentFactory.SERVICE.getInstance().createContent(myErrorsView.getComponent(), CONTENT_NAME, true);
              content.putUserData(KEY, myErrorsView);
              messageView.getContentManager().addContent(content);
              messageView.getContentManager().setSelectedContent(content);
              messageView.getContentManager().addContentManagerListener(new CloseListener(content, messageView.getContentManager()));
              removeCompileContents(content);
              messageView.getContentManager().addContentManagerListener(new MyContentDisposer(content, messageView));
            }
          },
          XmlBundle.message("validate.xml.open.message.view.command.name"),
          null
      );
    }
    private void removeCompileContents(Content notToRemove) {
      MessageView messageView = MessageView.SERVICE.getInstance(myProject);

      for (Content content : messageView.getContentManager().getContents()) {
        if (content.isPinned()) continue;
        if (CONTENT_NAME.equals(content.getDisplayName()) && content != notToRemove) {
          ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
          if (listErrorView != null) {
            if (messageView.getContentManager().removeContent(content, true)) {
              content.release();
            }
          }
        }
      }
    }

    public void processError(final SAXParseException ex, final boolean warning) {
      if (LOG.isDebugEnabled()) {
        String error = buildMessageString(ex);
        LOG.debug("enter: processError(error='" + error + "')");
      }

      myErrorsDetected = true;

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        SwingUtilities.invokeLater(
            new Runnable() {
              public void run() {
                final VirtualFile file = getFile(ex.getPublicId(), ex.getSystemId());
                myErrorsView.addMessage(
                    warning ? MessageCategory.WARNING : MessageCategory.ERROR,
                    new String[]{ex.getLocalizedMessage()},
                    file,
                    ex.getLineNumber() - 1,
                    ex.getColumnNumber() - 1,
                    null
                );
              }
            }
        );
      }
    }

    private class CloseListener extends ContentManagerAdapter {
      private Content myContent;
      private final ContentManager myContentManager;

      public CloseListener(Content content, ContentManager contentManager) {
        myContent = content;
        myContentManager = contentManager;
      }

      public void contentRemoved(ContentManagerEvent event) {
        if (event.getContent() == myContent) {
          myErrorsView.stopProcess();

          myContentManager.removeContentManagerListener(this);
          myContent.release();
          myContent = null;
        }
      }

      public void contentRemoveQuery(ContentManagerEvent event) {
        if (event.getContent() == myContent) {
          if (!myErrorsView.isProcessStopped()) {
            int result = Messages.showYesNoDialog(
              XmlBundle.message("xml.validate.validation.is.running.terminate.confirmation.text"),
              XmlBundle.message("xml.validate.validation.is.running.terminate.confirmation.title"),
                Messages.getQuestionIcon()
            );
            if (result != 0) {
              event.consume();
            }
          }
        }
      }
    }

    private class MyProcessController implements NewErrorTreeViewPanel.ProcessController {
      private Future<?> myFuture;

      public void setFuture(Future<?> future) {
        myFuture = future;
      }

      public void stopProcess() {
        if (myFuture != null) {
          myFuture.cancel(true);
        }
      }

      public boolean isProcessStopped() {
        return myFuture != null && myFuture.isDone();
      }
    }
  }

  public void doValidate(XmlFile file) {
    myProject = file.getProject();
    myFile = file;

    myXmlResourceResolver = new XmlResourceResolver(myFile, myProject, myErrorReporter);
    myXmlResourceResolver.setStopOnUnDeclaredResource( myErrorReporter.isStopOnUndeclaredResource() );

    try {
      try {
        myParser = createParser();
      }
      catch (Exception e) {
        filterAppException(e);
      }

      if (myParser == null) return;

      myErrorReporter.startProcessing();
    }
    catch (XmlResourceResolver.IgnoredResourceException ignore) {
    }
    catch (Exception exception) {
      filterAppException(exception);
    }
  }

  private void filterAppException(Exception exception) {
    if (!myErrorReporter.filterValidationException(exception)) {
      LOG.error(exception);
    }
  }

  private void doParse() {
    try {
      myParser.parse(new InputSource(new StringReader(myFile.getText())), new DefaultHandler() {
        public void warning(SAXParseException e) {
          if (myErrorReporter.isUniqueProblem(e)) myErrorReporter.processError(e, true);
        }

        public void error(SAXParseException e) {
          if (myErrorReporter.isUniqueProblem(e)) myErrorReporter.processError(e, false);
        }

        public void fatalError(SAXParseException e) {
          if (myErrorReporter.isUniqueProblem(e)) myErrorReporter.processError(e, false);
        }

        public InputSource resolveEntity(String publicId, String systemId) {
          final PsiFile psiFile = myXmlResourceResolver.resolve(null, systemId);
          if (psiFile == null) return null;
          return new InputSource(new StringReader(psiFile.getText()));
        }

        public void startDocument() throws SAXException {
          super.startDocument();
          myParser.setProperty(
            ENTITY_RESOLVER_PROPERTY_NAME,
            myXmlResourceResolver
          );
        }
      });

      final String[] resourcePaths = myXmlResourceResolver.getResourcePaths();
      if (resourcePaths.length > 0) { // if caches are used
        final VirtualFile[] files = new VirtualFile[resourcePaths.length];
        for (int i = 0; i < resourcePaths.length; ++i) {
          files[i] = UriUtil.findRelativeFile(resourcePaths[i], null);
        }

        myFile.putUserData(DEPENDENT_FILES_KEY, files);
        myFile.putUserData(GRAMMAR_POOL_TIME_STAMP_KEY, new Long(calculateTimeStamp(files, myProject)));
      }
    }
    catch (SAXException e) {
      LOG.debug(e);
    }
    catch (Exception exception) {
      filterAppException(exception);
    }
    catch (StackOverflowError error) {
      // http://issues.apache.org/jira/browse/XERCESJ-589
    }
  }

  protected SAXParser createParser() throws SAXException, ParserConfigurationException {
      if (!needsDtdChecking() && !needsSchemaChecking() && !myForceChecking) {
        return null;
      }

      SAXParserFactory factory = new SAXParserFactoryImpl();
      boolean schemaChecking = false;

      if (hasDtdDeclaration()) {
        factory.setValidating(true);
      }

      if (needsSchemaChecking()) {
        factory.setValidating(true);
        factory.setNamespaceAware(true);
        //jdk 1.5 API
        try {
          factory.setXIncludeAware(true);
        } catch(NoSuchMethodError ignore) {}
        schemaChecking = true;
      }

      SAXParser parser = factory.newSAXParser();

      parser.setProperty(ENTITY_RESOLVER_PROPERTY_NAME, myXmlResourceResolver);

      if (schemaChecking) { // when dtd checking schema refs could not be validated @see http://marc.theaimsgroup.com/?l=xerces-j-user&m=112504202423704&w=2
        XMLGrammarPool grammarPool = getGrammarPool(myFile, myForceChecking);

        parser.getXMLReader().setProperty(GRAMMAR_FEATURE_ID, grammarPool);
      }

      try {
        if (schemaChecking) {
          parser.setProperty(JAXPConstants.JAXP_SCHEMA_LANGUAGE,JAXPConstants.W3C_XML_SCHEMA);
          parser.getXMLReader().setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, true);
          
          if (Boolean.TRUE.equals(Boolean.getBoolean(XmlResourceResolver.HONOUR_ALL_SCHEMA_LOCATIONS_PROPERTY_KEY))) {
            parser.getXMLReader().setFeature("http://apache.org/xml/features/honour-all-schemaLocations", true);
          }

          parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/warn-on-undeclared-elemdef",Boolean.TRUE);
          parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/warn-on-duplicate-attdef",Boolean.TRUE);
        }

        parser.getXMLReader().setFeature("http://apache.org/xml/features/warn-on-duplicate-entitydef",Boolean.TRUE);
        parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/unparsed-entity-checking",Boolean.FALSE);
      } catch(SAXNotRecognizedException ex) {
        // it is possible to continue work with configured parser
        LOG.info("Xml parser installation seems screwed", ex);
      }

      return parser;
  }

  public static XMLGrammarPool getGrammarPool(XmlFile file, boolean forceChecking) {
    final XMLGrammarPool previousGrammarPool = getGrammarPool(file);
    XMLGrammarPool grammarPool = null;

    // check if the pool is valid
    if (!forceChecking &&
        !isValidationDependentFilesOutOfDate(file)
       ) {
      grammarPool = previousGrammarPool;
    }

    if (grammarPool == null) {
      grammarPool = new XMLGrammarPoolImpl();
      file.putUserData(GRAMMAR_POOL_KEY,grammarPool);
    }
    return grammarPool;
  }

  @Nullable
  public static XMLGrammarPool getGrammarPool(XmlFile file) {
    return file.getUserData(GRAMMAR_POOL_KEY);
  }

  public static boolean isValidationDependentFilesOutOfDate(XmlFile myFile) {
    final VirtualFile[] files = myFile.getUserData(DEPENDENT_FILES_KEY);
    final Long grammarPoolTimeStamp = myFile.getUserData(GRAMMAR_POOL_TIME_STAMP_KEY);

    if (grammarPoolTimeStamp != null &&
        files != null
       ) {
      long dependentFilesTimestamp = calculateTimeStamp(files,myFile.getProject());

      if (dependentFilesTimestamp == grammarPoolTimeStamp.longValue()) {
        return false;
      }
    }

    return true;
  }

  private static long calculateTimeStamp(final VirtualFile[] files, Project myProject) {
    long timestamp = 0;

    for(VirtualFile file:files) {
      if (file == null || !file.isValid()) break;
      final PsiFile psifile = PsiManager.getInstance(myProject).findFile(file);

      if (psifile != null && psifile.isValid()) {
        timestamp += psifile.getModificationStamp();
      } else {
        break;
      }
    }
    return timestamp;
  }

  private boolean hasDtdDeclaration() {
    XmlDocument document = myFile.getDocument();
    if (document == null) return false;
    XmlProlog prolog = document.getProlog();
    if (prolog == null) return false;
    XmlDoctype doctype = prolog.getDoctype();
    if (doctype == null) return false;

    return true;
  }

  private boolean needsDtdChecking() {
    XmlDocument document = myFile.getDocument();
    if (document == null) return false;

    return (document.getProlog()!=null && document.getProlog().getDoctype()!=null);
  }

  private boolean needsSchemaChecking() {
    XmlDocument document = myFile.getDocument();
    if (document == null) return false;
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null) return false;

    XmlAttribute[] attributes = rootTag.getAttributes();
    for (XmlAttribute attribute : attributes) {
      if (attribute.isNamespaceDeclaration()) return true;
    }

    return false;
  }
  private static class MyContentDisposer implements ContentManagerListener {
    private final Content myContent;
    private final MessageView myMessageView;

    public MyContentDisposer(final Content content, final MessageView messageView) {
      myContent = content;
      myMessageView = messageView;
    }

    public void contentRemoved(ContentManagerEvent event) {
      final Content eventContent = event.getContent();
      if (!eventContent.equals(myContent)) {
        return;
      }
      myMessageView.getContentManager().removeContentManagerListener(this);
      NewErrorTreeViewPanel errorTreeView = eventContent.getUserData(KEY);
      if (errorTreeView != null) {
        errorTreeView.dispose();
      }
      eventContent.putUserData(KEY, null);
    }

    public void contentAdded(ContentManagerEvent event) {
    }
    public void contentRemoveQuery(ContentManagerEvent event) {
    }
    public void selectionChanged(ContentManagerEvent event) {
    }
  }

}
