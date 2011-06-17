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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.WatchedRootsProvider;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.ui.GuiUtils;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.util.net.NetUtils;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author mike
 */
public class FetchExtResourceAction extends BaseExtResourceAction implements WatchedRootsProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.FetchDtdAction");
  private static final @NonNls String HTML_MIME = "text/html";
  private static final @NonNls String HTTP_PROTOCOL = "http://";
  private static final @NonNls String HTTPS_PROTOCOL = "https://";
  private static final @NonNls String FTP_PROTOCOL = "ftp://";
  private static final @NonNls String EXT_RESOURCES_FOLDER = "extResources";

  protected String getQuickFixKeyId() {
    return "fetch.external.resource";
  }

  protected boolean isAcceptableUri(final String uri) {
    return uri.startsWith(HTTP_PROTOCOL) || uri.startsWith(FTP_PROTOCOL) || uri.startsWith(HTTPS_PROTOCOL);
  }

  public static String findUrl(PsiFile file, int offset, String uri) {
    final PsiElement currentElement = file.findElementAt(offset);
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(currentElement, XmlAttribute.class);

    if (attribute != null) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(currentElement, XmlTag.class);

      if (tag != null) {
        final String prefix = tag.getPrefixByNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI);
        if (prefix != null) {
          final String attrValue = tag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
          if (attrValue != null) {
            final StringTokenizer tokenizer = new StringTokenizer(attrValue);

            while(tokenizer.hasMoreElements()) {
              if (uri.equals(tokenizer.nextToken())) {
                if (!tokenizer.hasMoreElements()) return uri;
                final String url = tokenizer.nextToken();

                return url.startsWith(HTTP_PROTOCOL) ? url:uri;
              }

              if (!tokenizer.hasMoreElements()) return uri;
              tokenizer.nextToken(); // skip file location
            }
          }
        }
      }
    }
    return uri;
  }

  @NotNull
  public Set<String> getRootsToWatch() {
    return Collections.singleton(getExternalResourcesPath());
  }

  static class FetchingResourceIOException extends IOException {
    private final String url;

    FetchingResourceIOException(Throwable cause, String url) {
      initCause(cause);
      this.url = url;
    }
  }

  static class FetchingResourceProblemRuntimeWrapper extends RuntimeException {
    FetchingResourceProblemRuntimeWrapper(FetchingResourceIOException cause) {
      super(cause);
    }
  }

  protected void doInvoke(final PsiFile file, final int offset, final String uri, final Editor editor) throws IncorrectOperationException {
    final String url = findUrl(file, offset, uri);
    final Project project = file.getProject();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          while(true) {
            try {
              final ProgressWindow[] result = new ProgressWindow[1];

              ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                public void run() {
                  final ProgressWindow progressWindow = new ProgressWindow(true, project);
                  progressWindow.setTitle(XmlBundle.message("fetching.resource.title"));
                  result[0] = progressWindow;
                }
              }, ModalityState.defaultModalityState());


              ProgressManager.getInstance().runProcess(new Runnable() {
                  public void run() {
                    try {
                      HttpConfigurable.getInstance().prepareURL(url);
                      fetchDtd(project, uri, url);
                    }
                    catch (IOException ex) {
                      FetchingResourceIOException exceptionDescribingIOProblem;

                      if (ex instanceof FetchingResourceIOException) {
                        exceptionDescribingIOProblem = (FetchingResourceIOException)ex;
                      } else {
                        exceptionDescribingIOProblem = new FetchingResourceIOException(ex, url);
                      }

                      throw new FetchingResourceProblemRuntimeWrapper(exceptionDescribingIOProblem);
                    }

                  }
                }, result[0]);
            }
            catch (FetchingResourceProblemRuntimeWrapper e) {
              String message = XmlBundle.message("error.fetching.title");
              FetchingResourceIOException ioproblem = (FetchingResourceIOException)e.getCause();

              if (!url.equals(ioproblem.url)) {
                message = XmlBundle.message("error.fetching.dependent.resource.title");
              }

              final IOException cause = (IOException)ioproblem.getCause();
              LOG.info(cause);
              if (!IOExceptionDialog.showErrorDialog(message,
                XmlBundle.message("error.fetching.resource", ioproblem.url))
              ) {
                break; // cancel fetching
              }
              else {
                continue;  // try another time
              }
            }
            break; // success fetching
          }
        }
      });
    }
  }

  private void fetchDtd(final Project project, final String dtdUrl, final String url) throws IOException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    final String extResourcesPath = getExternalResourcesPath();
    final File extResources = new File(extResourcesPath);
    final boolean alreadyExists = extResources.exists();
    extResources.mkdirs();
    LOG.assertTrue(extResources.exists());

    final PsiManager psiManager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        Runnable action = new Runnable() {
          public void run() {
            VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
              extResources.getAbsolutePath().replace(File.separatorChar, '/'));
            LOG.assertTrue(vFile != null);
            PsiDirectory directory = psiManager.findDirectory(vFile);
            directory.getFiles();
            if (!alreadyExists) LocalFileSystem.getInstance().addRootToWatch(vFile.getPath(), true);
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, indicator.getModalityState());

    final List<String> downloadedResources = new LinkedList<String>();
    final List<String> resourceUrls = new LinkedList<String>();
    final IOException[] nestedException = new IOException[1];

    try {
      final String resPath = fetchOneFile(indicator, url, project, extResourcesPath, null);
      if (resPath == null) return;
      resourceUrls.add(dtdUrl);
      downloadedResources.add(resPath);

      ApplicationManager.getApplication().invokeAndWait(
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                ExternalResourceManagerImpl.getInstance().addResource(dtdUrl, resPath);
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(resPath.replace(File.separatorChar, '/'));

                Set<String> linksToProcess = new HashSet<String>();
                Set<String> processedLinks = new HashSet<String>();
                Map<String,String> baseUrls = new HashMap<String, String>();
                VirtualFile contextFile = virtualFile;
                linksToProcess.addAll( extractEmbeddedFileReferences(virtualFile, null, psiManager) );

                while(!linksToProcess.isEmpty()) {
                  String s = linksToProcess.iterator().next();
                  linksToProcess.remove(s);
                  processedLinks.add(s);

                  final boolean absoluteUrl = s.startsWith(HTTP_PROTOCOL);
                  String resourceUrl;
                  if (absoluteUrl) {
                    resourceUrl = s;
                  } else {
                    String baseUrl = baseUrls.get(s);
                    if (baseUrl == null) baseUrl = url;

                    resourceUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + s;
                  }

                  String resourcePath;

                  String refname = s.substring(s.lastIndexOf('/') + 1);
                  if (absoluteUrl) refname = Integer.toHexString(s.hashCode()) + "_" + refname;
                  try {
                    resourcePath = fetchOneFile(indicator, resourceUrl, project, extResourcesPath, refname);
                  }
                  catch (IOException e) {
                    nestedException[0] = new FetchingResourceIOException(e, resourceUrl);
                    break;
                  }

                  if (resourcePath == null) break;

                  virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(resourcePath.replace(File.separatorChar, '/'));
                  downloadedResources.add(resourcePath);

                  if (absoluteUrl) {
                    ExternalResourceManagerImpl.getInstance().addResource(s, resourcePath);
                    resourceUrls.add(s);
                  }

                  final List<String> newLinks = extractEmbeddedFileReferences(virtualFile, contextFile, psiManager);
                  for(String u:newLinks) {
                    baseUrls.put(u, resourceUrl);
                    if (!processedLinks.contains(u)) linksToProcess.add(u);
                  }
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

  public static String getExternalResourcesPath() {
    return PathManager.getSystemPath() + File.separator + EXT_RESOURCES_FOLDER;
  }

  private void cleanup(final List<String> resourceUrls, final List<String> downloadedResources) {
    try {
      GuiUtils.invokeAndWait(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              for (String resourcesUrl : resourceUrls) {
                ExternalResourceManagerImpl.getInstance().removeResource(resourcesUrl);
              }

              for (String downloadedResource : downloadedResources) {
                try {
                  LocalFileSystem.getInstance().findFileByIoFile(new File(downloadedResource)).delete(this);
                }
                catch (IOException ex) {
                }
              }
            }
          });
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

  @Nullable
  private static String fetchOneFile(final ProgressIndicator indicator,
                              final String resourceUrl,
                              final Project project,
                              String extResourcesPath,
                              String refname) throws IOException {
    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          indicator.setText(XmlBundle.message("fetching.progress.indicator", resourceUrl));
        }
      }
    );

    FetchResult result = fetchData(project, resourceUrl, indicator);
    if (result == null) return null;

    if (!ApplicationManager.getApplication().isUnitTestMode() &&
          result.contentType != null &&
          result.contentType.indexOf(HTML_MIME) != -1 &&
          (new String(result.bytes)).indexOf("<html") != -1) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(project,
                                       XmlBundle.message("invalid.url.no.xml.file.at.location", resourceUrl),
                                       XmlBundle.message("invalid.url.title"),
                                       Messages.getErrorIcon());
          }
        }, indicator.getModalityState());
        return null;
      }

    int slashIndex = resourceUrl.lastIndexOf('/');
    String resPath = extResourcesPath + File.separatorChar;

    if (refname != null) { // resource is known under refname so need to save it
      resPath += refname;
      int refnameSlashIndex = resPath.lastIndexOf('/');
      if (refnameSlashIndex != -1) {
        new File(resPath.substring(0,refnameSlashIndex)).mkdirs();
      }
    } else {
      resPath += Integer.toHexString(resourceUrl.hashCode()) + "_" + resourceUrl.substring(slashIndex + 1);
    }

    final int lastDoPosInResourceUrl = resourceUrl.lastIndexOf('.', slashIndex);
    if (lastDoPosInResourceUrl == -1 ||
        FileTypeManager.getInstance().getFileTypeByExtension(resourceUrl.substring(lastDoPosInResourceUrl + 1)) == StdFileTypes.UNKNOWN
       ) {
      // remote url does not contain file with extension
      resPath += "." + StdFileTypes.XML.getDefaultExtension();
    }

    File res = new File(resPath);

    FileOutputStream out = new FileOutputStream(res);
    try {
      out.write(result.bytes);
    }
    finally {
      out.close();
    }
    return resPath;
  }

  private static List<String> extractEmbeddedFileReferences(XmlFile file, XmlFile context) {
    final List<String> result = new LinkedList<String>();
    if (context != null) {
      XmlEntityRefImpl.copyEntityCaches(file, context);
    }

    XmlUtil.processXmlElements(
      file,
      new PsiElementProcessor() {
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
            String schemaLocation = tag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT);

            if (schemaLocation != null) {
              final PsiReference[] references = tag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT, null).getValueElement().getReferences();
              if (references.length > 0) {
                final String namespace = tag.getAttributeValue("namespace");

                if (namespace != null && schemaLocation.indexOf('/') == -1) {
                  result.add(namespace.substring(0, namespace.lastIndexOf('/') + 1) + schemaLocation);
                } else {
                  result.add(schemaLocation);
                }
              }
            }

            final String prefix = tag.getPrefixByNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI);
            if (prefix != null) {
              schemaLocation = tag.getAttributeValue("schemaLocation",XmlUtil.XML_SCHEMA_INSTANCE_URI);

              if (schemaLocation != null) {
                final StringTokenizer tokenizer = new StringTokenizer(schemaLocation);

                while(tokenizer.hasMoreTokens()) {
                  tokenizer.nextToken();
                  if (!tokenizer.hasMoreTokens()) break;
                  String location = tokenizer.nextToken();

                  if (!result.contains(location)) {
                    result.add(location);
                  }
                }
              }
            }
          }

          return true;
        }

      },
      true,
      true
    );
    return result;
  }

  public static List<String> extractEmbeddedFileReferences(VirtualFile vFile, VirtualFile contextVFile, PsiManager psiManager) {
    PsiFile file = psiManager.findFile(vFile);

    if (file instanceof XmlFile) {
      PsiFile contextFile = contextVFile != null? psiManager.findFile(contextVFile):null;
      return extractEmbeddedFileReferences((XmlFile)file, contextFile instanceof XmlFile ? (XmlFile)contextFile:null);
    }

    return Collections.emptyList();
  }

  static class FetchResult {
    byte[] bytes;
    String contentType;
  }

  @Nullable
  private static FetchResult fetchData(final Project project, final String dtdUrl, ProgressIndicator indicator) throws IOException {

    try {
      URL url = new URL(dtdUrl);
      HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
      urlConnection.addRequestProperty("accept","text/xml,application/xml,text/html,*/*");
      int contentLength = urlConnection.getContentLength();
      int bytesRead = 0;

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      InputStream in = urlConnection.getInputStream();
      String contentType;
      try {
        contentType = urlConnection.getContentType();
        NetUtils.copyStreamContent(indicator, in, out, contentLength);
      }
      finally {
        in.close();
      }

      FetchResult result = new FetchResult();
      result.bytes = out.toByteArray();
      result.contentType = contentType;

      return result;
    }
    catch (MalformedURLException e) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(project,
                                       XmlBundle.message("invalid.uril.message", dtdUrl),
                                       XmlBundle.message("invalid.url.title"),
                                       Messages.getErrorIcon());
          }
        }, indicator.getModalityState());
      }
    }

    return null;
  }
}
