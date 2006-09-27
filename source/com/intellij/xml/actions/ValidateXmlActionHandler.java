package com.intellij.xml.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.*;
import com.intellij.ui.content.*;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xml.util.XmlResourceResolver;
import com.intellij.xml.XmlBundle;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.jaxp.JAXPConstants;
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.apache.xerces.util.XMLGrammarPoolImpl;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.io.FileNotFoundException;
import java.net.UnknownHostException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class ValidateXmlActionHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.actions.ValidateXmlAction");
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("ValidateXmlAction.KEY");
  @NonNls private static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";
  private static final String GRAMMAR_FEATURE_ID = Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;
  private static final Key<XMLGrammarPoolImpl> GRAMMAR_POOL_KEY = Key.create("GrammarPoolKey");
  private static final Key<Long> GRAMMAR_POOL_TIME_STAMP_KEY = Key.create("GrammarPoolTimeStampKey");
  private static final Key<VirtualFile[]> DEPENDENT_FILES_KEY = Key.create("GrammarPoolFilesKey");

  private Project myProject;
  private XmlFile myFile;
  private ErrorReporter myErrorReporter;
  private Object myParser;
  private XmlResourceResolver myXmlResourceResolver;
  private boolean myForceChecking;
  @NonNls
  private static final String ENTITY_RESOLVER_PROPERTY_NAME = "http://apache.org/xml/properties/internal/entity-resolver";
  @NonNls
  public static final String XMLNS_PREFIX = "xmlns";

  public ValidateXmlActionHandler(boolean _forceChecking) {
    myForceChecking = _forceChecking;
  }

  public void setErrorReporter(ErrorReporter errorReporter) {
    myErrorReporter = errorReporter;
  }

  private VirtualFile getFile(String publicId) {
    if (publicId == null) return myFile.getVirtualFile();
    final String path = myXmlResourceResolver.getPathByPublicId(publicId);
    if (path != null) return VfsUtil.findRelativeFile(path,null);
    return null;
  }
  
  public abstract class ErrorReporter {
    public abstract void processError(SAXParseException ex,boolean warning);

    public boolean filterValidationException(Exception ex) {
      if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
      
      if (ex instanceof FileNotFoundException ||
            ex instanceof MalformedURLException || 
            ex instanceof NoRouteToHostException || 
            ex instanceof ConnectException
            ) {
        // do not log problems caused by malformed and/or ignored external resources
        return true;
      }
        
      return false;
    }

    public void startProcessing() {
      doParse();
    }

    public boolean isStopOnUndeclaredResource() {
      return false;
    }
  }

  private String buildMessageString(SAXParseException ex) {
    String msg = "(" + ex.getLineNumber() + ":" + ex.getColumnNumber() + ") " + ex.getMessage();
    final VirtualFile file = getFile(ex.getPublicId());
    
    if ( file != null && !file.equals(myFile.getVirtualFile())) {
      msg = file.getName() + ":" + msg;
    }
    return msg;
  }

  public class TestErrorReporter extends ErrorReporter {
    private ArrayList<String> errors = new ArrayList<String>(3);

    public void processError(SAXParseException ex, boolean warning) {
      errors.add(buildMessageString(ex));
    }

    public List<String> getErrors() {
      return errors;
    }
  }

  class StdErrorReporter extends ErrorReporter {
    private NewErrorTreeViewPanel myErrorsView;
    private final String CONTENT_NAME = XmlBundle.message("xml.validate.tab.content.title");
    private boolean myErrorsDetected = false;
    @NonNls
    private static final String VALIDATE_XML_THREAD_NAME = "Validate XML";

    StdErrorReporter(Project project) {
      myErrorsView = new NewErrorTreeViewPanel(project, null);
    }
    public void startProcessing() {
      final Thread thread = new Thread(new Runnable() {
        public void run() {
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
      }, VALIDATE_XML_THREAD_NAME);
      myErrorsView.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
        public void stopProcess() {
          if (thread != null) {
            thread.stop();
          }
        }

        public boolean isProcessStopped() {
          return thread == null || !thread.isAlive();
        }
      });
      openMessageView();
      thread.start();

      ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
    }

    private void openMessageView() {
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.executeCommand(
          myProject, new Runnable() {
            public void run() {
              MessageView messageView = myProject.getComponent(MessageView.class);
              Content content = PeerFactory.getInstance().getContentFactory().createContent(myErrorsView.getComponent(), CONTENT_NAME, true);
              content.putUserData(KEY, myErrorsView);
              messageView.addContent(content);
              messageView.setSelectedContent(content);
              messageView.addContentManagerListener(new CloseListener(content, messageView));
              removeCompileContents(content);
              messageView.addContentManagerListener(new MyContentDisposer(content, messageView));
            }
          },
          XmlBundle.message("validate.xml.open.message.view.command.name"),
          null
      );
    }
    private void removeCompileContents(Content notToRemove) {
      MessageView messageView = myProject.getComponent(MessageView.class);

      for (Content content : messageView.getContents()) {
        if (content.isPinned()) continue;
        if (CONTENT_NAME.equals(content.getDisplayName()) && content != notToRemove) {
          ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
          if (listErrorView != null) {
            if (messageView.removeContent(content)) {
              content.release();
            }
          }
        }
      }
    }

    public void processError(final SAXParseException ex, final boolean warning) {
      String error = buildMessageString(ex);
      if (LOG.isDebugEnabled()) {
        LOG.debug("enter: processError(error='" + error + "')");
      }

      myErrorsDetected = true;

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        SwingUtilities.invokeLater(
            new Runnable() {
              public void run() {
                myErrorsView.addMessage(
                    warning ? MessageCategory.WARNING : MessageCategory.ERROR,
                    new String[]{ex.getLocalizedMessage()},
                    getFile(ex.getPublicId()),
                    ex.getLineNumber(),
                    ex.getColumnNumber(), null);
              }
            }
        );
      }
    }

    private class CloseListener extends ContentManagerAdapter {
      private Content myContent;
      private ContentManager myContentManager;

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
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    doValidate(project,file);
  }

  public void doValidate(Project project, PsiFile file) {
    myProject = project;
    myFile = (XmlFile)file;

    myXmlResourceResolver = new XmlResourceResolver(myFile, myProject);
    myXmlResourceResolver.setStopOnUnDeclaredResource( myErrorReporter.isStopOnUndeclaredResource() );

    try {
      myParser = createParser();

      if (myParser == null) return;

      myErrorReporter.startProcessing();
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

  public boolean startInWriteAction() {
    return true;
  }

  private void doParse() {
    try {
     if (myParser instanceof SAXParser) {
        SAXParser parser = (SAXParser)myParser;

        try {
          parser.parse(new InputSource(new StringReader(myFile.getText())), new DefaultHandler() {
            public void warning(SAXParseException e) {
              myErrorReporter.processError(e, true);
            }

            public void error(SAXParseException e) {
              myErrorReporter.processError(e, false);
            }

            public void fatalError(SAXParseException e) {
              myErrorReporter.processError(e, false);
            }

            public InputSource resolveEntity(String publicId, String systemId) {
              final PsiFile psiFile =  myXmlResourceResolver.resolve(null, systemId);
              if (psiFile == null) return null;
              return new InputSource(new StringReader(psiFile.getText()));
            }

            public void startDocument() throws SAXException {
              super.startDocument();
              ((SAXParser)myParser).setProperty(
                ENTITY_RESOLVER_PROPERTY_NAME,
                myXmlResourceResolver
              );
            }
          });
          
          final String[] resourcePaths = myXmlResourceResolver.getResourcePaths();
          if (resourcePaths.length > 0) { // if caches are used
            final VirtualFile[] files = new VirtualFile[resourcePaths.length];
            for(int i = 0; i < resourcePaths.length; ++i) {
              files[i] = VfsUtil.findRelativeFile(resourcePaths[i], null);
            }
            
            myFile.putUserData(DEPENDENT_FILES_KEY, files);
            myFile.putUserData(GRAMMAR_POOL_TIME_STAMP_KEY, new Long(calculateTimeStamp(files,myProject)));
          }
        }
        catch (SAXException e) {
          LOG.debug(e);
//          processError(e.getMessage(), false, 0, 0);
        } catch(UnknownHostException ex) {
          LOG.debug(ex);
        }
      }
      else {
        LOG.error("unknown parser: " + myParser);
      }
    }
    catch (Exception exception) {
      filterAppException(exception);
    }
  }

  private Object createParser() {
    try {
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
        schemaChecking = true;
      }
      
      SAXParser parser = factory.newSAXParser();

      parser.setProperty(ENTITY_RESOLVER_PROPERTY_NAME, myXmlResourceResolver);
      
      final XMLGrammarPoolImpl previousGrammarPool = myFile.getUserData(GRAMMAR_POOL_KEY);
      XMLGrammarPoolImpl grammarPool = null;
      
      // check if the pool is valid
      if (!myForceChecking && 
          !isValidationDependentFilesOutOfDate(myFile)
         ) {
        grammarPool = previousGrammarPool;
      }

      if (grammarPool == null) {
        grammarPool = new XMLGrammarPoolImpl();
        myFile.putUserData(GRAMMAR_POOL_KEY,grammarPool);
      }
      
      parser.getXMLReader().setProperty(GRAMMAR_FEATURE_ID, grammarPool);

      if (schemaChecking) {
        parser.setProperty(JAXPConstants.JAXP_SCHEMA_LANGUAGE,JAXPConstants.W3C_XML_SCHEMA);
        parser.getXMLReader().setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, true);

        parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/warn-on-undeclared-elemdef",Boolean.TRUE);
        parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/warn-on-duplicate-attdef",Boolean.TRUE);
      }

      parser.getXMLReader().setFeature("http://apache.org/xml/features/warn-on-duplicate-entitydef",Boolean.TRUE);
      parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/unparsed-entity-checking",Boolean.FALSE);
      parser.getXMLReader().setFeature("http://apache.org/xml/features/xinclude",Boolean.TRUE);

      return parser;
    }
    catch (Exception e) {
      filterAppException(e);
    }

    return null;
  }

  public static boolean isValidationDependentFilesOutOfDate(XmlFile myFile) {
    final VirtualFile[] files = myFile.getUserData(DEPENDENT_FILES_KEY);
    final Long grammarPoolTimeStamp = myFile.getUserData(GRAMMAR_POOL_TIME_STAMP_KEY);

    if (grammarPoolTimeStamp != null &&
        files != null
       ) {
      long dependentFilesTimestamp = calculateTimeStamp(files,myFile.getProject());

      if (dependentFilesTimestamp == grammarPoolTimeStamp.longValue() &&
          dependentFilesTimestamp != 0
        ) {
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
      myMessageView.removeContentManagerListener(this);
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
