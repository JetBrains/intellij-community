// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.WatchedRootsProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.XmlEntityCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class FetchExtResourceAction extends BaseExtResourceAction {
  private static final Logger LOG = Logger.getInstance(FetchExtResourceAction.class);
  private static final @NonNls String HTML_MIME = "text/html";
  private static final @NonNls String HTTP_PROTOCOL = "http://";
  private static final @NonNls String HTTPS_PROTOCOL = "https://";
  private static final @NonNls String FTP_PROTOCOL = "ftp://";
  private static final @NonNls String EXT_RESOURCES_FOLDER = "extResources";
  private final boolean myForceResultIsValid;
  private static final String KEY = "xml.intention.fetch.name";

  static final class MyWatchedRootsProvider implements WatchedRootsProvider {
    @Override
    public @NotNull Set<String> getRootsToWatch(@NotNull Project project) {
      String path = getExternalResourcesPath();
      Path file = checkExists(path);
      return Collections.singleton(file.toAbsolutePath().toString());
    }
  }

  public FetchExtResourceAction() {
    myForceResultIsValid = false;
  }

  public FetchExtResourceAction(boolean forceResultIsValid) {
    myForceResultIsValid = forceResultIsValid;
  }

  @Override
  protected String getQuickFixKeyId() {
    return KEY;
  }

  @Override
  protected boolean isAcceptableUri(final String uri) {
    return uri.startsWith(HTTP_PROTOCOL) || uri.startsWith(FTP_PROTOCOL) || uri.startsWith(HTTPS_PROTOCOL);
  }

  public static String findUrl(PsiFile psiFile, int offset, String uri) {
    final PsiElement currentElement = psiFile.findElementAt(offset);
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(currentElement, XmlAttribute.class);

    if (attribute != null) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(currentElement, XmlTag.class);

      if (tag != null) {
        final String prefix = tag.getPrefixByNamespace(XmlUtil.XML_SCHEMA_INSTANCE_URI);
        if (prefix != null) {
          final String attrValue = tag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
          if (attrValue != null) {
            final StringTokenizer tokenizer = new StringTokenizer(attrValue);

            while (tokenizer.hasMoreElements()) {
              if (uri.equals(tokenizer.nextToken())) {
                if (!tokenizer.hasMoreElements()) return uri;
                final String url = tokenizer.nextToken();

                return url.startsWith(HTTP_PROTOCOL) ? url : uri;
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

  private static @NotNull Path checkExists(String dir) {
    Path path = Paths.get(dir);
    try {
      Files.createDirectories(path);
    }
    catch (IOException e) {
      LOG.warn("Unable to create: " + path, e);
    }
    return path;
  }

  static final class FetchingResourceIOException extends IOException {
    private final String url;

    FetchingResourceIOException(Throwable cause, String url) {
      initCause(cause);
      this.url = url;
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void doInvoke(final @NotNull PsiFile psiFile, final int offset, final @NotNull String uri, final Editor editor)
    throws IncorrectOperationException {
    final String url = findUrl(psiFile, offset, uri);
    final Project project = psiFile.getProject();

    ProgressManager.getInstance().run(new Task.Backgroundable(project, XmlBundle.message(
      "xml.intention.fetch.progress.fetching.resource")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        while (true) {
          try {
            HttpConfigurable.getInstance().prepareURL(url);
            fetchDtd(project, uri, url, indicator);
            ApplicationManager.getApplication().invokeLater(() -> DaemonCodeAnalyzer.getInstance(project).restart(psiFile));
            return;
          }
          catch (IOException ex) {
            LOG.info(ex);
            @SuppressWarnings("InstanceofCatchParameter")
            String problemUrl = ex instanceof FetchingResourceIOException ? ((FetchingResourceIOException)ex).url : url;
            String message = XmlBundle.message("xml.intention.fetch.error.fetching.title");

            if (!url.equals(problemUrl)) {
              message = XmlBundle.message("xml.intention.fetch.error.fetching.dependent.resource");
            }

            if (!IOExceptionDialog.showErrorDialog(message, XmlBundle.message("xml.intention.fetch.error.fetching.resource", problemUrl))) {
              break; // cancel fetching
            }
          }
        }
      }
    });
  }

  private void fetchDtd(final Project project, final String dtdUrl, final String url, final ProgressIndicator indicator) throws IOException {
    final String extResourcesPath = getExternalResourcesPath();
    final File extResources = new File(extResourcesPath);
    LOG.assertTrue(extResources.mkdirs() || extResources.exists(), extResources);

    final PsiManager psiManager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {
      final String path = FileUtil.toSystemIndependentName(extResources.getAbsolutePath());
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      LOG.assertTrue(vFile != null, path);
    }));

    final List<String> downloadedResources = new LinkedList<>();
    final List<String> resourceUrls = new LinkedList<>();
    final IOException[] nestedException = new IOException[1];

    try {
      final String resPath = fetchOneFile(indicator, url, project, extResourcesPath, null);
      if (resPath == null) return;
      resourceUrls.add(dtdUrl);
      downloadedResources.add(resPath);

      VirtualFile virtualFile = findFileByPath(resPath, dtdUrl, project);

      Set<String> processedLinks = new HashSet<>();
      Map<String, String> baseUrls = new HashMap<>();
      Map<String, String> parentRefs = new HashMap<>();
      VirtualFile contextFile = virtualFile;
      Set<String> linksToProcess = new HashSet<>(extractEmbeddedFileReferences(virtualFile, null, psiManager, url));

      while (!linksToProcess.isEmpty()) {
        String s = linksToProcess.iterator().next();
        linksToProcess.remove(s);
        processedLinks.add(s);

        final boolean absoluteUrl = s.startsWith(HTTP_PROTOCOL) || s.startsWith(HTTPS_PROTOCOL);
        String resourceUrl;
        if (absoluteUrl) {
          resourceUrl = s;
        }
        else {
          String baseUrl = baseUrls.get(s);
          if (baseUrl == null) baseUrl = url;

          resourceUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + s;
          try {
            URL base = new URL(baseUrl);
            resourceUrl = new URL(base, s).toString();
          }
          catch (MalformedURLException e) {
            LOG.warn(e);
          }
        }

        String refName = s;
        if (absoluteUrl) {
          refName = Integer.toHexString(s.hashCode()) + "_" + refName.substring(refName.lastIndexOf('/') + 1);
        }
        else if (!refName.startsWith("/")) {
          String parentRef = parentRefs.get(refName);
          if (parentRef != null && !parentRef.startsWith("/") && parentRef.contains("/")) {
            refName = new File(new File(parentRef).getParent(), refName).getPath();
          }
        }
        String resourcePath;
        try {
          resourcePath = fetchOneFile(indicator, resourceUrl, project, extResourcesPath, refName);
        }
        catch (IOException e) {
          nestedException[0] = new FetchingResourceIOException(e, resourceUrl);
          break;
        }

        if (resourcePath == null) break;

        virtualFile = findFileByPath(resourcePath, absoluteUrl ? s : null, project);
        downloadedResources.add(resourcePath);

        if (absoluteUrl) {
          resourceUrls.add(s);
        }

        final Set<String> newLinks = extractEmbeddedFileReferences(virtualFile, contextFile, psiManager, resourceUrl);
        for (String u : newLinks) {
          baseUrls.put(u, resourceUrl);
          parentRefs.put(u, refName);
          if (!processedLinks.contains(u)) linksToProcess.add(u);
        }
      }
    }
    catch (IOException ex) {
      nestedException[0] = ex;
    }
    if (nestedException[0] != null) {
      cleanup(resourceUrls, downloadedResources);
      throw nestedException[0];
    }
  }

  private static VirtualFile findFileByPath(final String resPath,
                                            final @Nullable String dtdUrl,
                                            Project project) {
    final Ref<VirtualFile> ref = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      ref.set(LocalFileSystem.getInstance().refreshAndFindFileByPath(resPath.replace(File.separatorChar, '/')));
      if (dtdUrl != null) {
        ExternalResourceManager.getInstance().addResource(dtdUrl, resPath);
      }
      else if (!project.isDisposed()){
        ExternalResourceManager.getInstance().incModificationCount();
        PsiManager.getInstance(project).dropPsiCaches();
      }
    }));
    return ref.get();
  }

  public static String getExternalResourcesPath() {
    return PathManager.getSystemPath() + File.separator + EXT_RESOURCES_FOLDER;
  }

  private void cleanup(final List<String> resourceUrls, final List<String> downloadedResources) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            for (String resourcesUrl : resourceUrls) {
              ExternalResourceManager.getInstance().removeResource(resourcesUrl);
            }

            for (String downloadedResource : downloadedResources) {
              VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(downloadedResource));
              if (virtualFile != null) {
                try {
                  virtualFile.delete(this);
                }
                catch (IOException ignore) {

                }
              }
            }
          }
        });
      }
    });
  }

  private @Nullable String fetchOneFile(final ProgressIndicator indicator,
                                        final String resourceUrl,
                                        final Project project,
                                        String extResourcesPath,
                                        @Nullable String refname) throws IOException {
    SwingUtilities.invokeLater(
      () -> indicator.setText(XmlBundle.message("xml.intention.fetch.progress.fetching", resourceUrl))
    );

    FetchResult result = fetchData(project, resourceUrl, indicator);
    if (result == null) return null;

    if(!resultIsValid(project, indicator, resourceUrl, result)) {
      return null;
    }

    String resPath = extResourcesPath + File.separatorChar;

    if (refname != null) { // resource is known under ref.name so need to save it
      resPath += refname;
      int refNameSlashIndex = resPath.lastIndexOf('/');
      if (refNameSlashIndex != -1) {
        checkExists(resPath.substring(0, refNameSlashIndex));
      }
    }
    else {
      int slashIndex = resourceUrl.lastIndexOf('/');
      resPath += Integer.toHexString(resourceUrl.hashCode()) + "_" + resourceUrl.substring(slashIndex + 1);
    }

    int lastDoPosInResourceUrl = resourceUrl.lastIndexOf('.');
    if (lastDoPosInResourceUrl == -1 ||
        FileTypeManager.getInstance().getFileTypeByExtension(resourceUrl.substring(lastDoPosInResourceUrl + 1)) == FileTypes.UNKNOWN) {
      // remote url does not contain file with extension
      final String extension =
        result.contentType != null &&
        result.contentType.contains(HTML_MIME) ? HtmlFileType.INSTANCE.getDefaultExtension() : XmlFileType.INSTANCE.getDefaultExtension();
      resPath += "." + extension;
    }

    File res = new File(resPath);

    FileUtil.writeToFile(res, result.bytes);
    return resPath;
  }

  private boolean resultIsValid(final Project project, ProgressIndicator indicator, final String resourceUrl, FetchResult result) {
    if (myForceResultIsValid) {
      return true;
    }
    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        result.contentType != null &&
        result.contentType.contains(HTML_MIME) &&
        new String(result.bytes, StandardCharsets.UTF_8).contains("<html")) {
      ApplicationManager.getApplication().invokeLater(() -> Messages.showMessageDialog(project,
                                                                                     XmlBundle.message(
                                                                                       "xml.intention.fetch.error.invalid.url.no.xml.file.at.location", resourceUrl),
                                                                                     XmlBundle.message(
                                                                                       "xml.intention.fetch.error.invalid.url.title"),
                                                                                     Messages.getErrorIcon()), indicator.getModalityState());
      return false;
    }
    return true;
  }

  private static Set<String> extractEmbeddedFileReferences(XmlFile file, XmlFile context, final String url) {
    if (context != null) {
      XmlEntityCache.copyEntityCaches(file, context);
    }

    Set<String> result = new LinkedHashSet<>();
    XmlUtil.processXmlElements(
      file,
      element -> {
        if (element instanceof XmlEntityDecl) {
          String candidateName = null;

          for (PsiElement e = element.getLastChild(); e != null; e = e.getPrevSibling()) {
            if (e instanceof XmlAttributeValue && candidateName == null) {
              candidateName = e.getText().substring(1, e.getTextLength() - 1);
            }
            else if (e instanceof XmlToken &&
                     candidateName != null &&
                     (((XmlToken)e).getTokenType() == XmlTokenType.XML_DOCTYPE_PUBLIC ||
                      ((XmlToken)e).getTokenType() == XmlTokenType.XML_DOCTYPE_SYSTEM
                     )
              ) {
              result.add(candidateName);
              break;
            }
          }
        }
        else if (element instanceof XmlTag tag) {
          String schemaLocation = tag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT);

          if (schemaLocation != null) {
            // processing xsd:import && xsd:include
            final PsiReference[] references = tag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT).getValueElement().getReferences();
            if (references.length > 0) {
              String extension = FileUtilRt.getExtension(new File(url).getName());
              final String namespace = tag.getAttributeValue("namespace");
              if (namespace != null &&
                  schemaLocation.indexOf('/') == -1 &&
                  !extension.equals(FileUtilRt.getExtension(schemaLocation))) {
                result.add(namespace.substring(0, namespace.lastIndexOf('/') + 1) + schemaLocation);
              }
              else {
                result.add(schemaLocation);
              }
            }
          }
          else {
            schemaLocation = tag.getAttributeValue(XmlUtil.SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
            if (schemaLocation != null) {
              final StringTokenizer tokenizer = new StringTokenizer(schemaLocation);

              while (tokenizer.hasMoreTokens()) {
                tokenizer.nextToken();
                if (!tokenizer.hasMoreTokens()) break;
                String location = tokenizer.nextToken();
                result.add(location);
              }
            }
          }
        }

        return true;
      },
      true,
      true
    );
    return result;
  }

  public static Set<String> extractEmbeddedFileReferences(final VirtualFile vFile,
                                                          final @Nullable VirtualFile contextVFile,
                                                          final PsiManager psiManager,
                                                          final String url) {
    return ReadAction.compute(() -> {
      PsiFile psiFile = psiManager.findFile(vFile);

      if (psiFile instanceof XmlFile) {
        PsiFile contextFile = contextVFile != null ? psiManager.findFile(contextVFile) : null;
        return extractEmbeddedFileReferences((XmlFile)psiFile, contextFile instanceof XmlFile ? (XmlFile)contextFile : null, url);
      }

      return Collections.emptySet();
    });
  }

  protected static class FetchResult {
    byte[] bytes;
    String contentType;
  }

  private static @Nullable FetchResult fetchData(final Project project, final String dtdUrl, final ProgressIndicator indicator) throws IOException {
    try {
      return HttpRequests.request(dtdUrl).accept("text/xml,application/xml,text/html,*/*").connect(request -> {
        FetchResult result = new FetchResult();
        result.bytes = request.readBytes(indicator);
        result.contentType = request.getConnection().getContentType();
        return result;
      });
    }
    catch (MalformedURLException e) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().invokeLater(() -> Messages.showMessageDialog(project,
                                                                                       XmlBundle.message(
                                                                                         "xml.intention.fetch.error.invalid.url.message", dtdUrl),
                                                                                       XmlBundle.message(
                                                                                         "xml.intention.fetch.error.invalid.url.title"),
                                                                                       Messages.getErrorIcon()), indicator.getModalityState());
      }
    }

    return null;
  }
}
