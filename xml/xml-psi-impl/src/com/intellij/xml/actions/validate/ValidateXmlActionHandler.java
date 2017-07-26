/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xml.actions.validate;

import com.intellij.javaee.UriUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlResourceResolver;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.XMLEntityManager;
import org.apache.xerces.impl.XercesAccessor;
import org.apache.xerces.jaxp.JAXPConstants;
import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.apache.xerces.util.SecurityManager;
import org.apache.xerces.util.XMLGrammarPoolImpl;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;

import static com.sun.org.apache.xerces.internal.impl.Constants.SECURITY_MANAGER;

/**
 * @author Mike
 */
public class ValidateXmlActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.actions.validate.ValidateXmlAction");

  private static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";
  private static final String GRAMMAR_FEATURE_ID = Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;
  private static final String ENTITY_MANAGER_PROPERTY_ID = Constants.XERCES_PROPERTY_PREFIX + Constants.ENTITY_MANAGER_PROPERTY;

  private static final Key<XMLGrammarPool> GRAMMAR_POOL_KEY = Key.create("GrammarPoolKey");
  private static final Key<Long> GRAMMAR_POOL_TIME_STAMP_KEY = Key.create("GrammarPoolTimeStampKey");
  private static final Key<VirtualFile[]> DEPENDENT_FILES_KEY = Key.create("GrammarPoolFilesKey");
  private static final Key<String[]> KNOWN_NAMESPACES_KEY = Key.create("KnownNamespacesKey");
  private static final Key<Map<String, XMLEntityManager.Entity>> ENTITIES_KEY = Key.create("EntityManagerKey");
  public static final String JDK_XML_MAX_OCCUR_LIMIT = "jdk.xml.maxOccurLimit";

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

  public VirtualFile getProblemFile(SAXParseException ex) {
    String publicId = ex.getPublicId();
    String systemId = ex.getSystemId();
    if (publicId == null) {
      if (systemId != null) {
        if (systemId.startsWith("file:/")) {
          VirtualFile file = VirtualFileManager.getInstance()
            .findFileByUrl(systemId.startsWith("file://") ? systemId : systemId.replace("file:/", "file://"));
          if (file != null) return file;
        }
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

  public enum ProblemType { WARNING, ERROR, FATAL }

  public String buildMessageString(SAXParseException ex) {
    String msg = "(" + ex.getLineNumber() + ":" + ex.getColumnNumber() + ") " + ex.getMessage();
    final VirtualFile file = getProblemFile(ex);

    if ( file != null && !file.equals(myFile.getVirtualFile())) {
      msg = file.getName() + ":" + msg;
    }
    return msg;
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

  public void doParse() {
    try {
      myParser.parse(new InputSource(new StringReader(myFile.getText())), new DefaultHandler() {
        @Override
        public void warning(SAXParseException e) throws SAXException {
          if (myErrorReporter.isUniqueProblem(e)) myErrorReporter.processError(e, ProblemType.WARNING);
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
          if (myErrorReporter.isUniqueProblem(e)) myErrorReporter.processError(e, ProblemType.ERROR);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
          if (myErrorReporter.isUniqueProblem(e)) myErrorReporter.processError(e, ProblemType.FATAL);
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
          final PsiFile psiFile = myXmlResourceResolver.resolve(null, systemId);
          if (psiFile == null) return null;
          return new InputSource(new StringReader(psiFile.getText()));
        }

        @Override
        public void startDocument() throws SAXException {
          super.startDocument();
          myParser.setProperty(
            ENTITY_RESOLVER_PROPERTY_NAME,
            myXmlResourceResolver
          );
          configureEntityManager(myFile, myParser);
        }
      });

      final String[] resourcePaths = myXmlResourceResolver.getResourcePaths();
      if (resourcePaths.length > 0) { // if caches are used
        final VirtualFile[] files = new VirtualFile[resourcePaths.length];
        for (int i = 0; i < resourcePaths.length; ++i) {
          files[i] = UriUtil.findRelativeFile(resourcePaths[i], null);
        }

        myFile.putUserData(DEPENDENT_FILES_KEY, files);
        myFile.putUserData(GRAMMAR_POOL_TIME_STAMP_KEY, calculateTimeStamp(files, myProject));
      }
       myFile.putUserData(KNOWN_NAMESPACES_KEY, getNamespaces(myFile));
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
      try {
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      } catch (Exception ignore) {
      }

      SAXParser parser = factory.newSAXParser();

      parser.setProperty(ENTITY_RESOLVER_PROPERTY_NAME, myXmlResourceResolver);

      try {
        parser.getXMLReader().setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      } catch (Exception ignore) {
      }

      String property = System.getProperty(JDK_XML_MAX_OCCUR_LIMIT);
      if (property != null) {
        SecurityManager securityManager = (SecurityManager)parser.getProperty(SECURITY_MANAGER);
        securityManager.setMaxOccurNodeLimit(Integer.parseInt(property));
      }

      if (schemaChecking) { // when dtd checking schema refs could not be validated @see http://marc.theaimsgroup.com/?l=xerces-j-user&m=112504202423704&w=2
        XMLGrammarPool grammarPool = getGrammarPool(myFile, myForceChecking);
        configureEntityManager(myFile, parser);
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
    if (!forceChecking && !isValidationDependentFilesOutOfDate(file)) {
      grammarPool = previousGrammarPool;
    }

    if (grammarPool == null) {
      invalidateEntityManager(file);
      grammarPool = new XMLGrammarPoolImpl();
      file.putUserData(GRAMMAR_POOL_KEY, grammarPool);
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
    String[] ns = myFile.getUserData(KNOWN_NAMESPACES_KEY);

    if (!Arrays.equals(ns, getNamespaces(myFile))) {
      return true;
    }

    if (grammarPoolTimeStamp != null && files != null) {
      long dependentFilesTimestamp = calculateTimeStamp(files,myFile.getProject());

      if (dependentFilesTimestamp == grammarPoolTimeStamp.longValue()) {
        return false;
      }
    }

    return true;
  }

  private static void invalidateEntityManager(XmlFile file) {
    file.putUserData(ENTITIES_KEY, null);
  }

  private static void configureEntityManager(XmlFile file, SAXParser parser) throws SAXException {
    XMLEntityManager entityManager = (XMLEntityManager)parser.getXMLReader().getProperty(ENTITY_MANAGER_PROPERTY_ID);
    Map<String, XMLEntityManager.Entity> entities = file.getUserData(ENTITIES_KEY);
    if (entities != null) {
      // passing of entityManager object would break validation, so we copy its entities
      Map<String, XMLEntityManager.Entity> map = XercesAccessor.getEntities(entityManager);
      for (Map.Entry<String, XMLEntityManager.Entity> entry : entities.entrySet()) {
        if (entry.getValue().isEntityDeclInExternalSubset()) {
          map.put(entry.getKey(), entry.getValue());
        }
      }
    }
    else {
      file.putUserData(ENTITIES_KEY, XercesAccessor.getEntities(entityManager));
    }
  }

  private static String[] getNamespaces(XmlFile file) {
    XmlTag rootTag = file.getRootTag();
    if (rootTag == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    return ContainerUtil.mapNotNull(rootTag.getAttributes(), attribute -> attribute.getValue(), ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private static long calculateTimeStamp(final VirtualFile[] files, Project myProject) {
    long timestamp = 0;

    for(VirtualFile file:files) {
      if (file == null || !file.isValid()) break;
      final PsiFile psifile = PsiManager.getInstance(myProject).findFile(file);

      if (psifile != null && psifile.isValid()) {
        timestamp += psifile.getViewProvider().getModificationStamp();
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
}
