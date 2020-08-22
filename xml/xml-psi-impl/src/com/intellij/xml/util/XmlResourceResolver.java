// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.UriUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.actions.validate.ErrorReporter;
import com.intellij.xml.actions.validate.ValidateXmlActionHandler;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.psi.XmlPsiBundle;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class XmlResourceResolver implements XMLEntityResolver {
  private static final Logger LOG = Logger.getInstance(XmlResourceResolver.class);
  private final XmlFile myFile;
  private final Project myProject;
  private final Map<String,String> myExternalResourcesMap = new HashMap<>(1);
  private boolean myStopOnUnDeclaredResource;
  @NonNls
  public static final String HONOUR_ALL_SCHEMA_LOCATIONS_PROPERTY_KEY = "idea.xml.honour.all.schema.locations";
  private final ErrorReporter myErrorReporter;

  public XmlResourceResolver(XmlFile _xmlFile, Project _project, final ErrorReporter errorReporter) {
    myFile = _xmlFile;
    myProject = _project;
    myErrorReporter = errorReporter;
  }

  public String getPathByPublicId(String baseId) {
    return myExternalResourcesMap.get(baseId);
  }

  public String[] getResourcePaths() {
    return ArrayUtilRt.toStringArray(myExternalResourcesMap.values());
  }

  @Nullable
  public PsiFile resolve(@Nullable final String baseSystemId, final String _systemId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: resolveEntity(baseSystemId='" + baseSystemId + "' systemId='" + _systemId + "," + this + "')");
    }

    if (_systemId == null) return null;
    if (myStopOnUnDeclaredResource &&
        ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(_systemId)) {
      throw new IgnoredResourceException();
    }

    final int length = XmlUtil.getPrefixLength(_systemId);
    final String systemId = _systemId.substring(length);

    final Computable<PsiFile> action = () -> {

      PsiFile baseFile = null;
      if (baseSystemId != null) {
        baseFile = getBaseFile(baseSystemId);
      }
      if (baseFile == null) {
        baseFile = myFile;
      }

      String version = null;
      String tagName = null;
      if (baseFile == myFile) {
        XmlTag rootTag = myFile.getRootTag();
        if (rootTag != null) {
          tagName = rootTag.getLocalName();
          version = rootTag.getAttributeValue("version");
        }
      }
      String resource = ((ExternalResourceManagerEx)ExternalResourceManager.getInstance()).getUserResource(myProject, systemId, version);
      if (resource != null) {
        XmlFile file = XmlUtil.findXmlFile(myFile, resource);
        if (file != null) return file;
      }

      PsiFile byLocation = resolveByLocation(myFile, systemId);
      if (byLocation != null) return byLocation;

      PsiFile psiFile = ExternalResourceManager.getInstance().getResourceLocation(systemId, baseFile, version);
      if (psiFile == null) {
        psiFile = XmlUtil.findXmlFile(baseFile, systemId);
      }
      // autodetection
      if (psiFile == null) {
        psiFile = XmlNamespaceIndex.guessSchema(systemId, tagName, version, null, myFile);
        if (psiFile == null) {
          psiFile = XmlNamespaceIndex.guessDtd(systemId, myFile);
        }
      }

      if (psiFile == null && baseSystemId != null) {
        String fullUrl = baseSystemId.substring( 0, baseSystemId.lastIndexOf('/') + 1 ) + systemId;
        psiFile = XmlUtil.findXmlFile(baseFile,fullUrl);
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("before relative file checking:"+psiFile+","+systemId+","+ baseSystemId+")");
      }
      if (psiFile == null && baseSystemId == null) { // entity file
        File workingFile = new File("");
        String workingDir = workingFile.getAbsoluteFile().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        String relativePath = StringUtil.replace(systemId, workingDir, "");

        if (relativePath.equals(systemId)) {
          // on Windows systemId consisting of idea install path could become encoded DOS short name (e.g. idea%7f1.504)
          // I am not aware how to get such name from 'workingDir' so let just pickup filename from there
          relativePath = systemId.substring(systemId.lastIndexOf('/') + 1);
        }

        String res = myExternalResourcesMap.get(relativePath);
        if (res != null) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(res);
          if (file != null) {
            psiFile = PsiManager.getInstance(myProject).findFile(file);
            if (psiFile != null) return psiFile;
          }
        }

        if (LOG.isDebugEnabled()) {
          LOG.debug("next to relative file checking:"+relativePath+","+myExternalResourcesMap.size()+")");
        }

        for(String path:getResourcePaths()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Finding file by url:" + path);
          }
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(path);
          if (file == null) continue;
          if (LOG.isDebugEnabled()) {
            LOG.debug("Finding "+relativePath+" relative to:"+file.getPath());
          }
          final VirtualFile relativeFile = UriUtil.findRelativeFile(relativePath, file);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Found "+(relativeFile != null ? relativeFile.getPath():"null"));
          }

          if (relativeFile != null) {
            psiFile = PsiManager.getInstance(myProject).findFile(relativeFile);
            if (psiFile != null) break;
          }
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("resolveEntity: psiFile='" + (psiFile != null ? psiFile.getVirtualFile() : null) + "'");
      }
      return psiFile;
    };

    final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(action);
    if (psiFile != null) {
      final VirtualFile file = psiFile.getVirtualFile();
      if (file != null) {
        final String url = file.getUrl();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Adding external resource ref:" + systemId + "," + url + "," + this);
        }
        myExternalResourcesMap.put(systemId,url);
      }
    }
    return psiFile;
  }

  private PsiFile getBaseFile(String baseSystemId) {

    PsiFile baseFile = resolve(null, baseSystemId);
    if (baseFile != null) return baseFile;

    // Find relative to myFile
    File workingFile = new File("");
    String workingDir = workingFile.getAbsoluteFile().getAbsolutePath().replace(File.separatorChar, '/');
    VirtualFile parent = myFile.getVirtualFile().getParent();
    if (parent == null)
      return null;
    String id = StringUtil.replace(baseSystemId, workingDir, parent.getPath());
    VirtualFile vFile = UriUtil.findRelative(id, myFile);

    if (vFile == null) {
      vFile = UriUtil.findRelative(baseSystemId, myFile);
    }
    if (vFile == null) {
      try {
        vFile = VirtualFileManager.getInstance().findFileByUrl(VfsUtilCore.convertFromUrl(new URL(baseSystemId)));
      }
      catch (MalformedURLException ignore) {
      }
    }

    if (vFile != null && !vFile.isDirectory() && !(vFile.getFileSystem() instanceof HttpFileSystem)) {
      baseFile = PsiManager.getInstance(myProject).findFile(vFile);
    }
    return baseFile;
  }

  @Override
  @Nullable
  public XMLInputSource resolveEntity(XMLResourceIdentifier xmlResourceIdentifier) throws XNIException {
    String publicId  = xmlResourceIdentifier.getLiteralSystemId() != null ?
                  xmlResourceIdentifier.getLiteralSystemId():
                  xmlResourceIdentifier.getNamespace();

    if (publicId != null && publicId.startsWith("file:")) {
      Path basePath = new File(URI.create(xmlResourceIdentifier.getBaseSystemId())).getParentFile().toPath();
      try {
        Path publicIdPath = new File(URI.create(publicId)).toPath();
        publicId = basePath.relativize(publicIdPath).toString().replace(File.separatorChar, '/');
      }
      catch (Exception ignore) {
      }
    }
    PsiFile psiFile = resolve(xmlResourceIdentifier.getBaseSystemId(), publicId);
    if (psiFile==null && xmlResourceIdentifier.getLiteralSystemId()!=null && xmlResourceIdentifier.getNamespace()!=null) {
      psiFile = resolve(
        xmlResourceIdentifier.getBaseSystemId(),
        publicId = xmlResourceIdentifier.getNamespace()
      );
    }

    if (psiFile == null) {
      if (publicId != null && publicId.contains(":/")) {
        try {
          myErrorReporter.processError(
            new SAXParseException(XmlPsiBundle.message("xml.inspections.validate.external.resource.is.not.registered", publicId), publicId, null, 0, 0), ValidateXmlActionHandler.ProblemType.ERROR);
        }
        catch (SAXException ignore) {

        }
        final XMLInputSource source = new XMLInputSource(xmlResourceIdentifier);
        source.setPublicId(publicId);
        source.setCharacterStream(new StringReader(""));
        return source;
      }
      return null;
    }

    XMLInputSource source = new XMLInputSource(xmlResourceIdentifier);
    if (xmlResourceIdentifier.getLiteralSystemId() == null) {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        final String url = VfsUtilCore.fixIDEAUrl(virtualFile.getUrl());
        source.setBaseSystemId(url);
        source.setSystemId(url);
      }
    }
    source.setPublicId(publicId);
    source.setBaseSystemId(null);
    source.setCharacterStream(new StringReader(psiFile.getText()));

    return source;
  }

  private static PsiFile resolveByLocation(PsiFile baseFile, String location) {
    if (baseFile instanceof XmlFile) {
      XmlTag tag = ((XmlFile)baseFile).getRootTag();
      if (tag != null) {
        XmlAttribute attribute = tag.getAttribute("schemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI);
        if (attribute != null) {
          XmlAttributeValue element = attribute.getValueElement();
          if (element != null) {
            PsiReference[] references = element.getReferences();
            for (PsiReference reference : references) {
              if (location.equals(reference.getCanonicalText())) {
                PsiElement resolve = reference.resolve();
                return resolve instanceof PsiFile ? (PsiFile)resolve : null;
              }
            }
          }
        }
      }
    }
    return null;
  }

  public void setStopOnUnDeclaredResource(final boolean stopOnUnDeclaredResource) {
    myStopOnUnDeclaredResource = stopOnUnDeclaredResource;
  }

  public static class IgnoredResourceException extends RuntimeException {
  }
}
