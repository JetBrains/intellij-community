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
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.*;
import com.intellij.ui.content.*;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xml.util.XmlResourceResolver;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.jaxp.JAXPConstants;
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.apache.xerces.util.XMLGrammarPoolImpl;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class ValidateXmlActionHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.actions.ValidateXmlAction");
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("ErrorTreeViewPanel.KEY");
  private static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";
  private static final String GRAMMAR_FEATURE_ID = Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;
  private static final Key<XMLGrammarPoolImpl> GRAMMARS_KEY = Key.create("ErrorTreeViewPanel.KEY");

  private Project myProject;
  private XmlFile myFile;
  private ErrorReporter myErrorReporter;
  private Object myParser;
  private XmlResourceResolver myXmlResourceResolver;
  private boolean forceChecking;

  public ValidateXmlActionHandler(boolean _forceChecking) {
    forceChecking = _forceChecking;
  }

  public void setErrorReporter(ErrorReporter errorReporter) {
    myErrorReporter = errorReporter;
  }

  public abstract class ErrorReporter {
    public abstract void processError(SAXParseException ex,boolean warning);

    public boolean filterValidationException(Exception ex) {
      if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
      return false;
    }

    public void startProcessing() {
      doParse();
    }
  }

  private static String buildMessageString(SAXParseException ex) {
    return "(" + ex.getLineNumber() + ":" + ex.getColumnNumber() + ") " +ex.getMessage();
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
    private static final String CONTENT_NAME = "Validate";
    private boolean myErrorsDetected = false;

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
                            WindowManager.getInstance().getStatusBar(myProject).setInfo("No errors detected");
                          }
                        }
                    );
                  }
                }
              }
          );
        }
      }, "Validate XML");
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
            }
          },
          "Open message view",
          null
      );
    }
    private void removeCompileContents(Content notToRemove) {
      MessageView messageView = myProject.getComponent(MessageView.class);
      Content[] contents = messageView.getContents();
      for (int i = 0; i < contents.length; i++) {
        Content content = contents[i];
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
                    myFile.getVirtualFile(),
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
                "Validation is running. Terminate it?",
                "Validation Is Running",
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
                "http://apache.org/xml/properties/internal/entity-resolver",
                myXmlResourceResolver
              );
            }
          });
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
      if (!needsDtdChecking() && !needsSchemaChecking() && !forceChecking) {
        return null;
      }

      SAXParserFactory factory = new SAXParserFactoryImpl();
      boolean schemaChecking = false;

      if (hasDtdDeclaration()) {
        factory.setValidating(true);
      } else if (needsSchemaChecking()) {
        factory.setValidating(true);
        factory.setNamespaceAware(true);
        schemaChecking = true;
      }
      SAXParser parser = factory.newSAXParser();

      parser.setProperty("http://apache.org/xml/properties/internal/entity-resolver", myXmlResourceResolver);
      XMLGrammarPoolImpl myGrammarPool = myFile.getUserData(GRAMMARS_KEY);

      if (myGrammarPool==null) {
        myGrammarPool = new XMLGrammarPoolImpl();
        myFile.putUserData(GRAMMARS_KEY,myGrammarPool);
      }
      parser.getXMLReader().setProperty(GRAMMAR_FEATURE_ID, myGrammarPool);

      if (schemaChecking) {
        parser.setProperty(JAXPConstants.JAXP_SCHEMA_LANGUAGE,JAXPConstants.W3C_XML_SCHEMA);
        parser.getXMLReader().setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, true);
        // bug in Xerces 2.6.2, http://nagoya.apache.org/bugzilla/show_bug.cgi?id=14217
        // parser.getXMLReader().setFeature("http://apache.org/xml/features/validation/warn-on-undeclared-elemdef",Boolean.TRUE);
        //setupSchemas(parser);
      }

      return parser;
    }
    catch (Exception e) {
      filterAppException(e);
    }

    return null;
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
    for (int i = 0; i < attributes.length; i++) {
      XmlAttribute attribute = attributes[i];
      if (attribute.getName().startsWith("xmlns")) return true;
    }

    return false;
  }
}
