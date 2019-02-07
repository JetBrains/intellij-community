// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.xml.actions.validate.TestErrorReporter;
import com.intellij.xml.actions.validate.ValidateXmlActionHandler;
import com.intellij.xml.util.XmlResourceResolver;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings({"ALL"})
public class ValidateXmlTest extends CodeInsightTestCase {
  @NonNls private static final String FILE_NOT_FOUND_MESSAGE = SystemInfo.isWindows ? "The system cannot find the file specified" : "No such file or directory";

  public void testSchemaNoErrors1() throws Throwable {
    perform("1.xsd", "");
  }

  public void testSchemaNoErrors2() throws Throwable {
    perform("library.xsd", "");
  }

  public void testSchemaNoErrors3() throws Throwable {
    perform("library.xml", "");
  }

  public void testSchemaNoErrors4() throws Throwable {
    perform("library1.xml", "");
  }

  public void testSchemaErrors1() throws Throwable {
    // till fixing Xerces
    //perform("e1.xsd", " :12:62: src-resolve: Cannot resolve the name 'PurchaseOrderType' to a(n) 'type definition' component.");
  }

  public void testXInclude() throws Throwable {
    perform("XInclude.xhtml", "xhtml1-transitional.xsd:(1483:51) ct-props-correct.5: Error for type '#AnonType_a'. Two attribute declarations, 'name' and 'id' have types which are derived from ID.\n" +
                              "(8:95) SchemeUnsupported: The XPointer scheme 'xpointer' is not supported.\n" +
                              "(8:95) Include operation failed, reverting to fallback. Resource error reading file as XML (href='x-head.html'). Reason: x-head.html (" + FILE_NOT_FOUND_MESSAGE + ")\n" +
                              "(8:95) An 'include' failed, and no 'fallback' element was found.",
            null, null, true,
            "reason: .*x-head.html",
            "reason: x-head.html"
            );
  }

  public void testXInclude2() throws Throwable {
      perform("XInclude2.xhtml", "xhtml1-transitional.xsd:(1483:51) ct-props-correct.5: error for type '#anontype_a'. two attribute declarations, 'name' and 'id' have types which are derived from id.\n" +
                                 "(6:95) schemeunsupported: the xpointer scheme 'xpointer' is not supported.\n" +
                                 "(6:95) include operation failed, reverting to fallback. resource error reading file as xml (href='x-head.html'). reason: x-head.html (" + FILE_NOT_FOUND_MESSAGE + ")\n" +
                                 "(6:95) an 'include' failed, and no 'fallback' element was found.",
              null, null, true,
              "reason: .*x-head.html",
              "reason: x-head.html"
              );
    }

  public void testExtDndNoErrors1() throws Throwable {
    perform("ejbjar.xml", "");
  }

  public void testExtDndNoErrors2() throws Throwable {
    perform("xhtml.xml", "");
  }

  public void testReferencingDtdIncludeFromExtResource() throws Throwable {
    String[] urls = {"http://www.w3.org/TR/xhtml1/dtd/xhtml1-strict2.dtd",
      "http://www.w3.org/TR/xhtml1/dtd/_xhtml-lat1.ent",
      "http://www.w3.org/TR/xhtml1/dtd/_xhtml-symbol.ent",
      "http://www.w3.org/TR/xhtml1/dtd/_xhtml-special.ent"
    };
    String[] pathes = {"xhtml1-strict2.dtd","xhtml-lat1.ent", "xhtml-symbol.ent", "xhtml-special.ent"};

    perform("xhtml.xhtml", "", urls, pathes, false, null, null);
  }

  public void testXercesStackOverflow() throws Throwable {
    perform(getTestName(false) + ".xml", "");
  }

  public void testSeveralImportsWithOneNamespace() throws Throwable {
    System.setProperty(XmlResourceResolver.HONOUR_ALL_SCHEMA_LOCATIONS_PROPERTY_KEY, "true");
    try {
      String testName = getTestName(false);
      String[] urls = {"","",""};
      String[] pathes = {testName + "_product.xsd", testName + "_instrumentclassification.xsd", testName + "_instrumentreference.xsd"};
      perform(testName + ".xml", "", urls, pathes, false, null, null);
    }
    finally {
      System.setProperty(XmlResourceResolver.HONOUR_ALL_SCHEMA_LOCATIONS_PROPERTY_KEY, "");
    }
  }

  public void testReferencingAnotherSchema() throws Throwable {
    perform(
      getTestName(false) + ".xml",
      "ReferencingAnotherSchema2.xsd:(7:62) src-resolve: Cannot resolve the name 'MyComponentType' to a(n) 'type definition' component."
    );
  }

  public void testSchemaWithVersion() throws Throwable {
    perform(
      getTestName(false) + ".xml",
      "",
      null, null, false, null, null
    );
  }

  public void testFilteringErrors() throws Throwable {
    String[] urls = {"urn:myschema"};
    String[] pathes = {"po.xsd"};

    perform("po.xml",
            "po.xsd:(2:77) EmptyTargetNamespace: In schema document 'po.xsd', the value of the 'targetNamespace' attribute cannot be an empty string.\n" +
                                                "po.xsd:(2:77) TargetNamespace.1: Expecting namespace 'urn:myschema', but the target namespace of the schema document is 'null'.\n" +
                                                "(2:60) cvc-elt.1.a: Cannot find the declaration of element 'purchaseOrder'.", urls, pathes, false,
                                                                                                                             "'file[^']*'",
                                                                                                                             "'po.xsd'");
  }

  public void testIgnoredResource() throws Throwable {
    String[] urls = {"http://www.w3.org/TR/xhtml1/dtd/xhtml1-strict2.dtd",
      "http://www.w3.org/TR/xhtml1/dtd/_xhtml-lat1.ent",
      "http://www.w3.org/TR/xhtml1/dtd/_xhtml-symbol.ent",
      "http://www.w3.org/TR/xhtml1/dtd/_xhtml-special.ent"
    };

    ExternalResourceManagerExImpl.getInstanceEx().addIgnoredResources(Arrays.asList(urls), getTestRootDisposable());
    perform("xhtml.xhtml", "");
  }

  public void testWebAppSchema() throws Throwable {
    perform("web.xml", ""); //, new String[]{"http://java.sun.com/xml/ns/j2ee"}, new String[]{"j2ee_1_4.xsd"}, false);
  }

  public void testNoDtdOrSchema1() throws Throwable {
    perform("novalidation.xml", "");
  }

  public void testNoDtdOrSchema2() throws Throwable {
    perform("novalidationerror.xml", "(4:5) The element type \"c\" must be terminated by the matching end-tag \"</c>\".");
  }

  public void testExtDndErrors1() throws Throwable {
    perform("ejbjar1.xml",
            "(7:21) Element type \"escription\" must be declared.\n" +
            "(20:17) The content of element type \"session\" must match \"(description?,display-name?,small-icon?,large-icon?,ejb-name,home?,remote?,local-home?,local?,ejb-class,session-type,transaction-type,env-entry*,ejb-ref*,ejb-local-ref*,security-role-ref*,security-identity?,resource-ref*,resource-env-ref*)\".");
  }

  public void testExtDndErrors2() throws Throwable {
    perform("xhtml1.xml",
            "(6:8) Element type \"bodyb\" must be declared.\n" +
            "(9:8) The content of element type \"html\" must match \"(head,body)\".");
  }

  public void testExtResources() throws Throwable {
    perform("xsl.xml", "", new String[] {"http://www.w3.org/1999/XSL/Transform"}, new String[] {"xsl.xsd"}, false);
  }

  public void testNoRouteToHostException() throws Throwable {
    perform("NoRouteToHostException.xml", "(0:0) External resource http://aaa.bbb.ccc/x.ent is not registered", new String[] {"NoRouteToHostException.dtd"}, new String[] {"NoRouteToHostException.dtd"}, false);
  }

  public void testFatalProblem() throws Throwable {
    // problem in dtd
    perform(
      "fatalError.xml",
      "fatalError.dtd:(10:3) The markup in the document preceding the root element must be well-formed.",
      new String[] {"http://www.w3.org/1999/XSL/Transform"},
      new String[] {"fatalError.dtd"},
      false
    );
  }

  public void testRef() throws Throwable {
    perform("cobb.xml", "(3:57) Attribute \"abstract\" with value \"\" must have a value from the list \"true false \".");
  }

  public void testJ2eeResources() throws Throwable {
    perform("web_app.xml", "(32:15) cvc-complex-type.2.4.a: Invalid content was found starting with element '{\"http://java.sun.com/xml/ns/j2ee\":aaa}'. One of '{\"http://java.sun.com/xml/ns/j2ee\":description, \"http://java.sun.com/xml/ns/j2ee\":display-name, \"http://java.sun.com/xml/ns/j2ee\":icon, \"http://java.sun.com/xml/ns/j2ee\":distributable, \"http://java.sun.com/xml/ns/j2ee\":context-param, \"http://java.sun.com/xml/ns/j2ee\":filter, \"http://java.sun.com/xml/ns/j2ee\":filter-mapping, \"http://java.sun.com/xml/ns/j2ee\":listener, \"http://java.sun.com/xml/ns/j2ee\":servlet, \"http://java.sun.com/xml/ns/j2ee\":servlet-mapping, \"http://java.sun.com/xml/ns/j2ee\":session-config, \"http://java.sun.com/xml/ns/j2ee\":mime-mapping, \"http://java.sun.com/xml/ns/j2ee\":welcome-file-list, \"http://java.sun.com/xml/ns/j2ee\":error-page, \"http://java.sun.com/xml/ns/j2ee\":jsp-config, \"http://java.sun.com/xml/ns/j2ee\":security-constraint, \"http://java.sun.com/xml/ns/j2ee\":login-config, \"http://java.sun.com/xml/ns/j2ee\":security-role, \"http://java.sun.com/xml/ns/j2ee\":env-entry, \"http://java.sun.com/xml/ns/j2ee\":ejb-ref, \"http://java.sun.com/xml/ns/j2ee\":ejb-local-ref, \"http://java.sun.com/xml/ns/j2ee\":service-ref, \"http://java.sun.com/xml/ns/j2ee\":resource-ref, \"http://java.sun.com/xml/ns/j2ee\":resource-env-ref, \"http://java.sun.com/xml/ns/j2ee\":message-destination-ref, \"http://java.sun.com/xml/ns/j2ee\":message-destination, \"http://java.sun.com/xml/ns/j2ee\":locale-encoding-mapping-list}' is expected.");
  }

  //public void testJBossDeploymentStructureDescriptor() throws Throwable {
  //  perform("jboss-deployment-structure.xml", "", new String[] {"urn:jboss:deployment-structure:1.0"}, new String[] { "jboss-deployment-structure-1_0.xsd" }, false);
  //}

  public void testSchemaAutodetection() throws Throwable {
    perform("nuancevoicexml-2-0.xml", "(0:0) External resource http://www.w3.org/2001/vxml is not registered\n" +
                                      "(-1:-1) Premature end of file.");
  }

  public void testXsd() throws Throwable {
    perform("test.xsd", "(11:59) cvc-complex-type.2.4.d: Invalid content was found starting with element 'attribute'. No child element is expected at this point.");
  }

  public void testXsd11Alternative() throws Throwable {
    perform("Alternative.xsd", "(41:83) cvc-complex-type.2.4.a: Invalid content was found starting with element '{\"http://www.w3.org/2001/XMLSchema\":alternative}'. One of '{\"http://www.w3.org/2001/XMLSchema\":annotation, \"http://www.w3.org/2001/XMLSchema\":simpleType, \"http://www.w3.org/2001/XMLSchema\":complexType, \"http://www.w3.org/2001/XMLSchema\":unique, \"http://www.w3.org/2001/XMLSchema\":key, \"http://www.w3.org/2001/XMLSchema\":keyref}' is expected.");
    ExternalResourceManagerEx.getInstanceEx().setXmlSchemaVersion(ExternalResourceManagerEx.XMLSchemaVersion.XMLSchema_1_1, getProject());
    perform("Alternative.xsd", "XMLSchema.xsd:(936:30) rcase-Recurse.2: There is not a complete functional mapping between the particles.\n" +
                               "XMLSchema.xsd:(936:30) derivation-ok-restriction.5.4.2: Error for type 'all'.  The particle of the type is not a valid restriction of the particle of the base.");
  }

  public void testRelativePath() throws Throwable {
    perform("instances/enumerations.xml", "");
  }

  private void perform(String fileName, String message) throws Throwable {
    perform(fileName,message,null,null, false);
  }

  private void perform(String fileName, String message, String[] urls, String[] files, boolean caseInsensitive) throws Throwable {
    perform(fileName, message, urls, files, caseInsensitive, null, null);
  }

  private void perform(String fileName, String message, String[] urls, String[] files, boolean caseInsensitive, String pattern, String replacement) throws Throwable {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(
      myProject, myModule, PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/validateXml", myFilesToDelete
    );
    VirtualFile virtualFile = VfsUtil.findRelativeFile(root, fileName.split("/"));

    if (urls != null && files != null) {
      assertEquals(urls.length,files.length);
      for(int i = 0; i< urls.length; ++i) {
        final VirtualFile vFile = root.findChild(files[i]);
        assertNotNull(files[i], vFile);
        ExternalResourceManagerExImpl.registerResourceTemporarily(urls[i], vFile.getPath(), getTestRootDisposable());
      }
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    myFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    assertNotNull(myFile);
    myEditor = createEditor(virtualFile);
    assertNotNull(myEditor);

    ValidateXmlActionHandler handler = new ValidateXmlActionHandler(true);
    TestErrorReporter errorReporter = new TestErrorReporter(handler);
    handler.setErrorReporter(errorReporter);
    handler.doValidate((XmlFile)myFile);
    String actual = "";


    List errors = errorReporter.getErrors();
    for (Iterator i = errors.iterator(); i.hasNext();) {
      actual += i.next();
      if (i.hasNext()) {
        actual += "\n";
      }
    }

    if (caseInsensitive) {
      message = message.toLowerCase();
      actual = actual.toLowerCase();
    }

    if (pattern != null && replacement != null) actual = actual.replaceAll(pattern, replacement);
    assertEquals(message, actual);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    String BASE_PATH = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/xml/";
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/dtd/ejb-jar_2_0.dtd",
                                                              BASE_PATH + "ejb-jar_2_0.dtd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",
                                                              BASE_PATH + "web-app_2_4.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/jsp_2_0.xsd",
                                                              BASE_PATH + "jsp_2_0.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://www.ibm.com/webservices/xsd/j2ee_web_services_client_1_1.xsd",
                                                              BASE_PATH + "j2ee_web_services_client_1_1.xsd",
                                                              getTestRootDisposable());
  }
}
