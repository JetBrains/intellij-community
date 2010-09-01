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
package com.intellij.xml.util;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.UriUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URIReferenceProvider;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.actions.ValidateXmlActionHandler;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class XmlResourceResolver implements XMLEntityResolver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.XmlResourceResolver");
  private final XmlFile myFile;
  private final Project myProject;
  private final Map<String,String> myExternalResourcesMap = new HashMap<String, String>(1);
  private boolean myStopOnUnDeclaredResource;
  @NonNls
  public static final String HONOUR_ALL_SCHEMA_LOCATIONS_PROPERTY_KEY = "idea.xml.honour.all.schema.locations";
  private final ValidateXmlActionHandler.ErrorReporter myErrorReporter;

  public XmlResourceResolver(XmlFile _xmlFile, Project _project, final ValidateXmlActionHandler.ErrorReporter errorReporter) {
    myFile = _xmlFile;
    myProject = _project;
    myErrorReporter = errorReporter;
  }

  public String getPathByPublicId(String baseId) {
    return myExternalResourcesMap.get(baseId);
  }

  public String[] getResourcePaths() {
    return myExternalResourcesMap.values().toArray(new String[myExternalResourcesMap.size()]);
  }

  @Nullable
  public PsiFile resolve(final String baseSystemId, final String _systemId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: resolveEntity(baseSystemId='" + baseSystemId + "' systemId='" + _systemId + "," + super.toString() + "')");
    }

    if (_systemId == null) return null;
    if (myStopOnUnDeclaredResource &&
        ExternalResourceManagerEx.getInstanceEx().isIgnoredResource(_systemId)) {
      throw new IgnoredResourceException();
    }

    final int length = URIReferenceProvider.getPrefixLength(_systemId);
    final String systemId = _systemId.substring(length);

    final PsiFile[] result = new PsiFile[] { null };
    final Runnable action = new Runnable() {
      public void run() {
        PsiFile baseFile = null;
        VirtualFile vFile = null;

        if (baseSystemId != null) {
          baseFile = resolve(null,baseSystemId);

          if (baseFile == null) {
            if (myFile != null) {
              // Find relative to myFile
              File workingFile = new File("");
              String workingDir = workingFile.getAbsoluteFile().getAbsolutePath().replace(File.separatorChar, '/');
              String id = StringUtil.replace(baseSystemId, workingDir, myFile.getVirtualFile().getParent().getPath());
              vFile = UriUtil.findRelativeFile(id, null);
            }

            if (vFile == null) {
              vFile = UriUtil.findRelativeFile(baseSystemId, null);

              if (vFile == null) {
                try {
                  vFile = VfsUtil.findFileByURL(new URL(baseSystemId));
                } catch(MalformedURLException ignore) {}
              }
            }
          }

          if (vFile != null && !vFile.isDirectory() && !(vFile.getFileSystem() instanceof HttpFileSystem)) {
            baseFile = PsiManager.getInstance(myProject).findFile(vFile);
          }
        }
        if (baseFile == null) {
          baseFile = myFile;
        }

        String version = null;
        if (baseFile == myFile) {
          final XmlDocument document = myFile.getDocument();
          final XmlTag rootTag = document != null ? document.getRootTag():null;
          version = rootTag != null ? rootTag.getAttributeValue("version"):null;
        }
        
        PsiFile psiFile = ExternalResourceManager.getInstance().getResourceLocation(systemId, baseFile, version);
        if (psiFile == null) {
          psiFile = XmlUtil.findXmlFile(baseFile, systemId);
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

          String relativePath = StringUtil.replace(
            systemId,
            workingDir,
            ""
          );

          if (relativePath.equals(systemId)) {
            // on Windows systemId consisting of idea install path could become encoded DOS short name (e.g. idea%7f1.504)
            // I am not aware how to get such name from 'workingDir' so let just pickup filename from there
            relativePath = systemId.substring(systemId.lastIndexOf('/') + 1);
          }

          if (LOG.isDebugEnabled()) {
            LOG.debug("next to relative file checking:"+relativePath+","+myExternalResourcesMap.size()+")");
          }

          for(String path:myExternalResourcesMap.values()) {
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
        result[0] = psiFile;
      }
    };
    ApplicationManager.getApplication().runReadAction(action);

    final PsiFile psiFile = result[0];
    if (psiFile != null) {
      final VirtualFile file = psiFile.getVirtualFile();
      if (file != null) {
        final String url = file.getUrl();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Adding external resource ref:"+systemId+","+url+","+super.toString());
        }
        myExternalResourcesMap.put(systemId,url);
      }
    }
    return psiFile;
  }

  @Nullable
  public XMLInputSource resolveEntity(XMLResourceIdentifier xmlResourceIdentifier) throws XNIException, IOException {
    String publicId  = xmlResourceIdentifier.getLiteralSystemId() != null ?
                  xmlResourceIdentifier.getLiteralSystemId():
                  xmlResourceIdentifier.getNamespace();

    PsiFile psiFile = resolve(xmlResourceIdentifier.getBaseSystemId(), publicId);
    if (psiFile == null && xmlResourceIdentifier.getBaseSystemId() != null) {
        psiFile = ExternalResourceManager.getInstance().getResourceLocation(xmlResourceIdentifier.getBaseSystemId(), myFile, null);
    }
    if (psiFile==null && xmlResourceIdentifier.getLiteralSystemId()!=null && xmlResourceIdentifier.getNamespace()!=null) {
      psiFile = resolve(
        xmlResourceIdentifier.getBaseSystemId(),
        publicId = xmlResourceIdentifier.getNamespace()
      );
    }

    if (psiFile == null) {
      if (publicId != null && publicId.indexOf(":/") != -1) {
        myErrorReporter.processError(
          new SAXParseException(XmlBundle.message("xml.validate.external.resource.is.not.registered", publicId), publicId, null, 0,0), false);
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
        final String url = VfsUtil.fixIDEAUrl(virtualFile.getUrl());
        source.setBaseSystemId(url);
        source.setSystemId(url);
      }
    }
    source.setPublicId(publicId);
    source.setCharacterStream(new StringReader(psiFile.getText()));

    return source;
  }

  public void setStopOnUnDeclaredResource(final boolean stopOnUnDeclaredResource) {
    myStopOnUnDeclaredResource = stopOnUnDeclaredResource;
  }

  public static class IgnoredResourceException extends RuntimeException {
  }
}
