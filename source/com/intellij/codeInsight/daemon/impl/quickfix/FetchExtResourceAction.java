package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.xml.util.XmlUtil;

import javax.swing.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.lang.reflect.InvocationTargetException;

/**
 * @author mike
 */
public class FetchExtResourceAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.FetchDtdAction");

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    String uri = findUri(file, offset);

    if (uri == null) return false;

    final XmlFile xmlFile = XmlUtil.findXmlFile(file, uri);
    if (xmlFile != null) return false;

    if (!uri.startsWith("http://") && !uri.startsWith("ftp://")) return false;

    setText("Fetch External Resource");
    return true;
  }

  public String getFamilyName() {
    return "Fetch External Resource";
  }

  private String findUri(PsiFile file, int offset) {
    final PsiElement currentElement = file.findElementAt(offset);
    PsiElement element = PsiTreeUtil.getParentOfType(currentElement, XmlDoctype.class);
    if (element != null) {
      return ((XmlDoctype)element).getDtdUri();
    }

    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(currentElement, XmlAttribute.class);
    if (attribute != null && attribute.isNamespaceDeclaration()) {
      final String uri = attribute.getValue();
      final PsiElement parent = attribute.getParent();
      if (uri != null && parent instanceof XmlTag && ((XmlTag)parent).getNSDescriptor(uri, true) == null) {
        return uri;
      }
    }
    return null;
  }

  class FetchingResourceIOException extends IOException {
    private String url;

    FetchingResourceIOException(Throwable cause, String url) {
      initCause(cause);
      this.url = url;
    }
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final String uri = findUri(file, offset);
    if (uri == null) return;

    final ProgressWindow progressWindow = new ProgressWindow(true, project);
    progressWindow.setTitle("Fetching resource");
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      new Thread(new Runnable() {
        public void run() {
          ProgressManager.getInstance().runProcess(new Runnable() {
            public void run() {
              do {
                try {
                  HttpConfigurable.getInstance().prepareURL(uri);
                  fetchDtd(project, uri);
                  break;
                }
                catch (IOException ex) {
                  String uriWithProblems = uri;
                  String message = "Error While Fetching";
                  IOException cause = ex;

                  if (ex instanceof FetchingResourceIOException) {
                    uriWithProblems = ((FetchingResourceIOException)ex).url;
                    cause = (IOException)ex.getCause();
                    if (!uri.equals(uriWithProblems)) message += " Dependent Resource";
                  }

                  if (!IOExceptionDialog.showErrorDialog(cause, message, "Error while fetching " + uriWithProblems)) {
                    break;
                  }
                  else {
                    continue;
                  }
                }
              }
              while (true);
            }
          }, progressWindow);
        }
      }, "Fetching Thread").start();
    }
  }

  private void fetchDtd(final Project project, final String dtdUrl) throws IOException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    final String sep = File.separator;
    final String extResourcesPath = PathManager.getSystemPath() + sep + "extResources";
    final File extResources = new File(extResourcesPath);
    extResources.mkdirs();
    LOG.assertTrue(extResources.exists());

    final PsiManager psiManager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
              extResources.getAbsolutePath().replace(File.separatorChar, '/'));
            LOG.assertTrue(vFile != null);
            final PsiDirectory directory = psiManager.findDirectory(vFile);
            directory.getFiles();
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, indicator.getModalityState());

    final List<String> downloadedResources = new LinkedList<String>();
    final List<String> resourceUrls = new LinkedList<String>();
    final IOException[] nestedException = new IOException[1];

    try {
      final String resPath = fetchOneFile(indicator, dtdUrl, project, extResourcesPath);
      resourceUrls.add(dtdUrl);
      downloadedResources.add(resPath);

      ApplicationManager.getApplication().invokeAndWait(
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                ExternalResourceManagerImpl.getInstance().addResource(dtdUrl, resPath);
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(resPath.replace(File.separatorChar, '/'));

                List<String> strings = extractEmbeddedFileReferences(virtualFile, psiManager);
                for (Iterator<String> iterator = strings.iterator(); iterator.hasNext();) {
                  String s = iterator.next();
                  if (s.startsWith("http://")) {
                    // do not support absolute references
                    continue;
                  }
                  String resourceUrl = dtdUrl.substring(0, dtdUrl.lastIndexOf('/') + 1) + s;
                  String resourcePath;

                  try {
                    resourcePath = fetchOneFile(indicator, resourceUrl, project, extResourcesPath);
                  }
                  catch (IOException e) {
                    nestedException[0] = new FetchingResourceIOException(e,resourceUrl);
                    break;
                  }

                  ExternalResourceManagerImpl.getInstance().addResource(resourceUrl, resourcePath);
                  LocalFileSystem.getInstance().refreshAndFindFileByPath(resourcePath.replace(File.separatorChar, '/'));
                  resourceUrls.add(resourceUrl);
                  downloadedResources.add(resourcePath);
                }
              }
            });
          }
        },
        indicator.getModalityState()
      );
    } catch(IOException ex) {
      nestedException[0] = ex;
    }

    if (nestedException[0]!=null) {
      cleanup(resourceUrls,downloadedResources);
      throw nestedException[0];
    }
  }

  private void cleanup(List<String> resourceUrls, List<String> downloadedResources) {
    for (Iterator<String> iterator = resourceUrls.iterator(), iterator2 = downloadedResources.iterator();
         iterator.hasNext() && iterator2.hasNext();) {
      final String resourcesUrl = iterator.next();
      final String downloadedResource = iterator2.next();

      try {
        SwingUtilities.invokeAndWait( new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(
              new Runnable() {
                public void run() {
                  ExternalResourceManagerImpl.getInstance().removeResource(resourcesUrl);
                  try {
                    LocalFileSystem.getInstance().findFileByIoFile(new File(downloadedResource)).delete(this);
                  } catch(IOException ex) {}
                }
              }
            );
          }
        });
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
    }
  }

  private String fetchOneFile(final ProgressIndicator indicator,
                              final String resourceUrl,
                              Project project,
                              String extResourcesPath) throws IOException {
    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          indicator.setText("Fetching " + resourceUrl);
        }
      }
    );

    byte[] bytes = fetchData(project, resourceUrl, indicator);
    //if (bytes == null) return;
    int slashIndex = resourceUrl.lastIndexOf("/");
    String resPath = extResourcesPath + File.separatorChar +
                           Integer.toHexString(resourceUrl.hashCode()) + "_" +
                           resourceUrl.substring(slashIndex + 1);
    if (resourceUrl.indexOf('.',slashIndex) == -1) {
      // remote url does not contain file with extension
      resPath += ".xml";
    }

    File res = new File(resPath);

    FileOutputStream out = new FileOutputStream(res);
    out.write(bytes);
    out.close();
    return resPath;
  }

  public static List<String> extractEmbeddedFileReferences(final VirtualFile vFile, final PsiManager psiManager) {
    PsiFile file = psiManager.findFile(vFile);
    final List<String> result = new LinkedList<String>();

    if (file instanceof XmlFile) {
      XmlUtil.processXmlElements((XmlFile)file,new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof XmlEntityDecl) {
            String candidateName = null;

            for (PsiElement e = element.getLastChild(); e != null; e = e.getPrevSibling()) {
              if (e instanceof XmlAttributeValue && candidateName==null) {
                candidateName = e.getText().substring(1,e.getTextLength()-1);
              } else if (e instanceof XmlToken &&
                         candidateName != null &&
                         ((XmlToken)e).getTokenType() == XmlTokenType.XML_DOCTYPE_PUBLIC
                         ) {
                if (!result.contains(candidateName)) {
                  result.add(candidateName);
                }
                break;
              }
            }
          } else if (element instanceof XmlTag) {
            final XmlTag tag = (XmlTag)element;

            if(tag.getLocalName().equals("include")) {
              //String namespace = tag.getNamespace();
              // we do not check for namespace here since there are many schema defs like
              // http://www.w3.org/1999/XMLSchema, http://www.w3.org/2001/XMLSchema, add your own here 

              String schemaLocation = ((XmlTag)element).getAttributeValue("schemaLocation");
              if (schemaLocation!=null) {
                result.add(schemaLocation);
              }
            }
          }

          return true;
        }

        public Object getHint(Class hintClass) {
          return null;
        }
        },
        true
      );
    }

    return result;
  }

  private byte[] fetchData(final Project project, final String dtdUrl, ProgressIndicator indicator) throws IOException {

    try {
      URL url = new URL(dtdUrl);
      final URLConnection urlConnection = url.openConnection();

      final int contentLength = urlConnection.getContentLength();
      int bytesRead = 0;

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      final InputStream in = urlConnection.getInputStream();

      byte[] buffer = new byte[256];

      while (true) {
        int read = in.read(buffer);
        if (read < 0) break;

        out.write(buffer, 0, read);
        bytesRead += read;
        indicator.setFraction((double)bytesRead / (double)contentLength);
      }

      in.close();
      out.close();

      return out.toByteArray();
    }
    catch (MalformedURLException e) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(project, "Invalid URL: \n" + dtdUrl, "Invalid URL", Messages.getErrorIcon());
          }
        }, indicator.getModalityState());
      }
    }

    return null;
  }
}
