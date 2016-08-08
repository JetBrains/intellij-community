/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.application.options.XmlSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection;
import com.intellij.codeInsight.daemon.impl.quickfix.AddXsiSchemaLocationForExtResourceAction;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInspection.htmlInspections.XmlWrongRootElementInspection;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.highlighter.XmlHighlighterFactory;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.javaee.UriUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.ant.dom.AntResolveInspection;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral", "ConstantConditions"})
public class XmlHighlightingTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/xml/";

  private boolean myTestJustJaxpValidation;
  @NonNls private static final String CREATE_NAMESPACE_DECLARATION_INTENTION_NAME = "Create namespace declaration";
  private boolean old;
  private String myOldDoctype;

  private void doTest() throws Exception {
    doTest(false);
  }
  private void doTest(boolean checkWarnings) throws Exception {
    doTest(getFullRelativeTestName(), checkWarnings, false);
  }

  private String getFullRelativeTestName() {
    return getFullRelativeTestName(".xml");
  }

  private String getFullRelativeTestName(String ext) {
    return BASE_PATH + getTestName(false) + ext;
  }

  @NotNull
  @Override
  protected List<HighlightInfo> doHighlighting() {
    if(myTestJustJaxpValidation) {
      XmlHighlightVisitor.setDoJaxpTesting(true);
    }

    final List<HighlightInfo> highlightInfos = super.doHighlighting();
    if(myTestJustJaxpValidation) {
      XmlHighlightVisitor.setDoJaxpTesting(false);
    }

    return highlightInfos;
  }

  @Override
  protected boolean forceExternalValidation() {
    return myTestJustJaxpValidation;
  }

  public void testclosedTag1() throws Exception { doTest(); }
  public void testClosedTag2() throws Exception { doTest(); }
  public void testwrongRootTag1() throws Exception { doTest(); }
  public void testrootTag1() throws Exception { doTest(); }
  public void testManyRootTags() throws Exception { doTest(); }
  public void testCommentBeforeProlog() throws Exception { doTest(); }
  public void testCommentBeforeProlog_2() throws Exception { doTest(); }
  //public void testNoRootTag() throws Exception { doTest(); }

  public void testduplicateAttribute() throws Exception { doTest(); }

  public void testduplicateAttribute2() throws Exception {
    configureByFiles(null, BASE_PATH + getTestName(false) + ".xml", BASE_PATH + getTestName(false) + ".xsd");

    final String url = "http://www.foo.org/schema";
    final String url2 = "http://www.bar.org/foo";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, getTestName(false) + ".xsd", getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily(url2, getTestName(false) + ".xsd", getTestRootDisposable());
    final Collection<HighlightInfo> infoCollection = doDoTest(true, false, true);
    final TextRange startTagNameRange = XmlTagUtil.getStartTagNameElement(((XmlFile)myFile).getDocument().getRootTag()).getTextRange();

    HighlightInfo infoAtTagName = null;

    for(HighlightInfo info:infoCollection) {
      if (info.startOffset == startTagNameRange.getStartOffset() && info.endOffset == startTagNameRange.getEndOffset()) {
        if (info.getDescription()
          .equals("Attribute \"foo\" bound to namespace \"http://www.w3.org/2000/xmlns/\" was already specified for element \"root\".")) {
          infoAtTagName = info;
          break;
        }
      }
    }

    assertNotNull( infoAtTagName );
  }

  // TODO: external validator should not be launched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testDuplicateIdAttribute() throws Exception { doTest(); }
  public void testDuplicateIdAttribute2() throws Exception {
    doTest();
  }
  public void testDuplicateNameAttribute() throws Exception {
    doSchemaTestWithManyFilesFromSeparateDir(
      new String[][] {
        {"http://www.springframework.org/schema/beans/spring-beans-2.5.xsd", "spring-beans-2.5.xsd"},
      },
      null
    );
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testInvalidIdRefAttribute() throws Exception { doTest(); }
  public void testEntityRefWithNoDtd() throws Exception { doTest(); }
  public void testNoSpaceBeforeAttrAndNoCdataEnd() throws Exception { doTest(); }

  // TODO: external validator should not be lauched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testEntityRefWithEmptyDtd() throws Exception { doTest(); }
  public void testEmptyNSRef() throws Exception { doTest(); }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testDoctypeWithoutSchema() throws Exception {
    final String baseName = BASE_PATH + getTestName(false);

    configureByFiles(null, getVirtualFile(baseName + ".xml"), getVirtualFile(baseName + ".ent"));
    doDoTest(true,false);
    myFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttributeValue(XmlAttributeValue value) {
        final PsiElement[] children = value.getChildren();
        for (PsiElement child : children) {
          if (child instanceof XmlEntityRef) {
            PsiElement psiElement = child.getReferences()[0].resolve();
            assertNotNull(psiElement);
            assertEquals(getTestName(false) + ".ent", psiElement.getContainingFile().getVirtualFile().getName());
            assertTrue(((Navigatable)psiElement).canNavigate());
          }
        }
      }
    });
  }

  public void testSvg() throws Exception {
    doTest(getFullRelativeTestName(".svg"), true, false);
  }

  public void testNavigateToDeclDefinedWithEntity() throws Exception {
    final String baseName = BASE_PATH + getTestName(false);

    configureByFiles(null, getVirtualFile(baseName + ".xml"), getVirtualFile(baseName + ".dtd"), getVirtualFile(baseName + ".ent"));
    doDoTest(true,false);
    final List<PsiReference> refs = new ArrayList<>();
    myFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(final XmlAttribute attribute) {
        refs.add(attribute.getReference());
      }

      @Override
      public void visitXmlTag(final XmlTag tag) {
        refs.add(tag.getReference());
        super.visitXmlTag(tag);
      }
    });

    assertEquals(2, refs.size());

    for(PsiReference ref:refs) {
      final PsiElement element = ref.resolve();
      // In order to navigate to entity defined things we need correctly setup navigation element among other things
      assertTrue(element instanceof PsiNamedElement);
      assertSame(element.getNavigationElement(), element);
      assertTrue(!element.isPhysical());

      final PsiElement original = element.getOriginalElement();
      // TODO: fix XmlAttlistDecl to return proper original element
      if (original != element && !(element instanceof XmlAttlistDecl)) {
        assertNotNull(original);
        assertTrue(original.isPhysical());
      }
    }
  }

  public void testDoctypeWithoutSchema2() throws Exception {
    final String baseName = BASE_PATH + "DoctypeWithoutSchema";

    configureByFiles(null, getVirtualFile(baseName + ".xml"), getVirtualFile(baseName + ".ent"));
    doDoTest(true, false);
  }

  public void testComplexSchemaValidation8() throws IOException {
    final String testName = getTestName(false);
    String location = testName +".xsd";
    String location2 = testName +"_2.xsd";
    String url = "http://drools.org/rules";
    String url2 = "http://drools.org/semantics/groovy";

    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily(url2, location2, getTestRootDisposable());
    final String basePath = BASE_PATH + testName;
    configureByFiles(null, getVirtualFile(basePath + ".xml"), getVirtualFile(basePath + ".xsd"), getVirtualFile(basePath + "_2.xsd"));
    doDoTest(true,false);

  }

  public void testComplexSchemaValidation9() throws IOException {
    final String basePath = BASE_PATH + getTestName(false);

    configureByFiles(null, getVirtualFile(basePath + ".xml" ), getVirtualFile(basePath + ".xsd" ));
    doDoTest(true,false);
  }

  public void testComplexSchemaValidation10() throws IOException {
    final String testName = getTestName(false);

    final String basePath = BASE_PATH + testName;
    configureByFiles(null, getVirtualFile(basePath + ".xsd" ), getVirtualFile(basePath + "_2.xsd" ));
    doDoTest(true,false);

    final List<PsiReference> refs = new ArrayList<>(2);

    myFile.acceptChildren(new XmlRecursiveElementVisitor() {

      @Override public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);

        addRefsInPresent(tag, "base", refs);
        if ("group".equals(tag.getLocalName())) {
          addRefsInPresent(tag, "ref", refs);
        }
      }
    });

    assertEquals(2, refs.size());
    PsiElement psiElement = refs.get(0).resolve();
    assertNotNull(psiElement);
    assertEquals(getTestName(false) + "_2.xsd",psiElement.getContainingFile().getName());

    psiElement = refs.get(1).resolve();
    assertNotNull(psiElement);
    assertEquals(getTestName(false) + "_2.xsd",psiElement.getContainingFile().getName());
  }

  private static void addRefsInPresent(final XmlTag tag, final String name, final List<PsiReference> refs) {
    if (tag.getAttributeValue(name) != null) {
      ContainerUtil.addAll(refs, tag.getAttribute(name, null).getValueElement().getReferences());
    }
  }

  public void testComplexSchemaValidation11() throws Exception {
    final String testName = getTestName(false);
    doTestWithLocations(
      new String[][] {
        {"http://www.springframework.org/schema/beans",testName + ".xsd"},
        {"http://www.springframework.org/schema/util",testName + "_2.xsd"}
      },
      "xml"
    );
  }

  private void doTestWithLocations(@Nullable String[][] resources, String ext) throws Exception {
    try {
      doConfigureWithLocations(resources, ext);
      doDoTest(true,false);
    } finally {
      unregisterResources(resources);
    }
  }

  private static void unregisterResources(final String[][] resources) {
    if (resources == null) return;
  }

  private void doConfigureWithLocations(final String[][] resources, final String ext) throws Exception {

    String[] testNames = new String[(resources != null? resources.length:0) + 1];
    testNames[0] = BASE_PATH + getTestName(false) + "." + ext;

    if (resources != null) {
      int curResource = 0;
      for(String[] resource:resources) {
        ExternalResourceManagerExImpl
          .registerResourceTemporarily(resource[0], getTestDataPath() + BASE_PATH + resource[1], getTestRootDisposable());
        testNames[++curResource] = BASE_PATH + resource[1];
      }
    }

    configureByFiles(
      null,
      testNames
    );
  }

  public void testComplexSchemaValidation12() throws Exception {
    final String basePath = BASE_PATH + getTestName(false);
    configureByFile(
      basePath + ".xsd",
      null
    );
    doDoTest(true,false);
  }

  public void testInvalidIdRefInXsl() throws Exception {
    doTest(getFullRelativeTestName(), false, false);
  }

  public void testXercesMessageOnPI() throws Exception {
    try {
      myTestJustJaxpValidation = true;
      doTest();
    } finally {
      myTestJustJaxpValidation = false;
    }
  }
  public void testXercesMessageOnDtd() throws Exception { doTest(); }
  public void testXercesMessagesBinding5() throws Exception {
    try {
      myTestJustJaxpValidation = true;
      doTest();
    } finally {
      myTestJustJaxpValidation = false;
    }
  }

  public void testXercesCachingProblem() throws Exception {
    final String[][] resources = {{"http://www.test.com/test", getTestName(false) + ".dtd"}};
    doConfigureWithLocations(resources, "xml");

    try {
      doDoTest(true,true);
      WriteCommandAction.runWriteCommandAction(null, () -> myEditor.getDocument().insertString(myEditor.getDocument().getCharsSequence().toString().indexOf("?>") + 2, "\n"));

      doDoTest(true,true);
    }
    finally {
      unregisterResources(resources);
    }
  }

  public void testComplexSchemaValidation13() throws Exception {
    doTestWithLocations(new String[][] { {"urn:test", getTestName(false) + "_2.xsd"} }, "xsd");
  }

  public void testComplexSchemaValidation14() throws Exception {
    doTestWithLocations(new String[][]{{"parent", getTestName(false) + ".xsd"}}, "xml");
  }

  public void testComplexSchemaValidation14_2() throws Exception {
    doTest(getFullRelativeTestName(".xsd"), true, false);
  }

  public void testComplexSchemaValidation15() throws Exception {
    doTestWithLocations(
      new String[][] {
        {"http://www.linkedin.com/lispring", getTestName(false) + ".xsd"},
        {"http://www.springframework.org/schema/beans", getTestName(false) + "_2.xsd"}
      },
      "xml"
    );
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testComplexSchemaValidation16() throws Exception {
    doTestWithLocations(
      new String[][] {
        {"http://www.inversoft.com/schemas/savant-2.0/project", getTestName(false) + ".xsd"},
        {"http://www.inversoft.com/schemas/savant-2.0/base", getTestName(false) + "_2.xsd"}
      },
      "xml"
    );
  }

  public void testComplexSchemaValidation17() throws Exception {
    doTestWithLocations(
      new String[][]{
        {"urn:test", getTestName(false) + ".xsd"}
      },
      "xml"
    );
  }

  public void testSchemaReferencesValidation() throws Exception {
    doTest(getFullRelativeTestName(".xsd"), false, false);
  }

  public void testXhtmlSchemaHighlighting() throws Exception {
    disableHtmlSupport();
    try {
      configureByFiles(null, getVirtualFile(BASE_PATH + "xhtml1-transitional.xsd"), getVirtualFile(BASE_PATH + "xhtml-special.ent"),
                       getVirtualFile(BASE_PATH + "xhtml-symbol.ent"), getVirtualFile(BASE_PATH + "xhtml-lat1.ent"));
      doDoTest(true,false);
    }
    finally {
      enableHtmlSupport();
    }
  }

  public void testSchemaValidation() throws Exception {
    doTest();
  }

  public void testSchemaValidation2() throws Exception {
    configureByFiles(null, getVirtualFile(BASE_PATH + getTestName(false)+".xml"), getVirtualFile(BASE_PATH + getTestName(false)+".xsd"));
    doDoTest(true,false);

    final XmlTag rootTag = ((XmlFile)myFile).getDocument().getRootTag();
    final XmlAttributeValue valueElement = rootTag.getAttribute("noNamespaceSchemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI).getValueElement();
    final PsiReference[] references = valueElement.getReferences();

    final String expectedFileName = getTestName(false) + ".xsd";

    assertEquals(1, references.length);
    final PsiElement schema = references[0].resolve();
    assertNotNull(schema);

    assertEquals(expectedFileName,schema.getContainingFile().getName());

    final PsiReference referenceAt = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    assertNotNull(referenceAt);
    final PsiElement psiElement = referenceAt.resolve();
    assertTrue(psiElement instanceof XmlTag &&
               "complexType".equals(((XmlTag)psiElement).getLocalName()) &&
               "Y".equals(((XmlTag)psiElement).getAttributeValue("name"))
               );

    assertEquals(expectedFileName,psiElement.getContainingFile().getName());
  }

  public void testSchemaValidation3() throws Exception {
    String location = getTestName(false)+".xsd";
    String location2 = getTestName(false)+"_2.xsd";
    String url = "parent";
    String url2 = "child";

    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily(url2, location2, getTestRootDisposable());

    configureByFiles(null, getVirtualFile(BASE_PATH + getTestName(false) + ".xml"), getVirtualFile(BASE_PATH + location),
                     getVirtualFile(BASE_PATH + location2));
    doDoTest(true,false);

  }

  public void testSchemaValidation4() throws Exception {
    String schemaLocation = getTestName(false)+".xsd";

    configureByFiles(null, getVirtualFile(BASE_PATH + getTestName(false) + ".xml"), getVirtualFile(BASE_PATH + schemaLocation));

    ExternalResourceManagerExImpl.registerResourceTemporarily(schemaLocation, schemaLocation, getTestRootDisposable());
    doDoTest(true, false);
  }

  public void testSchemaValidation5() throws Exception {
    String schemaLocation = getTestName(false)+".xsd";

    configureByFile(BASE_PATH + schemaLocation);
    doDoTest(true, false);
    final List<PsiReference> myTypeOrElementRefs = new ArrayList<>(1);
    final List<XmlTag> myTypesAndElementDecls = new ArrayList<>(1);

    myFile.accept(new XmlRecursiveElementVisitor() {
      @Override public void visitXmlAttributeValue(XmlAttributeValue value) {
        final PsiElement parent = value.getParent();
        if (!(parent instanceof XmlAttribute)) return;
        final String name = ((XmlAttribute)parent).getName();
        if ("type".equals(name) || "base".equals(name) || "ref".equals(name)) {
          myTypeOrElementRefs.add(value.getReferences()[0]);
        }
      }

      @Override public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        final String localName = tag.getLocalName();
        if ("complexType".equals(localName) || "simpleType".equals(localName) || "element".equals(localName)) {
          if (tag.getAttributeValue("name") != null) myTypesAndElementDecls.add(tag);
        }
      }
    });

    assertEquals(9,myTypesAndElementDecls.size());
    assertEquals(5,myTypeOrElementRefs.size());
    for(XmlTag t:myTypesAndElementDecls) {
      final XmlAttribute attribute = t.getAttribute("name", null);
      final XmlAttributeValue valueElement = attribute.getValueElement();
      final PsiReference nameReference = valueElement.getReferences()[0];
      WriteCommandAction.runWriteCommandAction(null, () -> {
        nameReference.handleElementRename("zzz");
      });
    }

    for(PsiReference ref:myTypeOrElementRefs)  {
      assertNull(ref.resolve());
    }
  }

  public void testExternalValidatorOnValidXmlWithNamespacesNotSetup() throws Exception {
    final ExternalResourceManagerEx instanceEx = ExternalResourceManagerEx.getInstanceEx();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      instanceEx.addIgnoredResource("http://xml.apache.org/axis/wsdd2/");
      instanceEx.addIgnoredResource("http://xml.apache.org/axis/wsdd2/providers/java");
      instanceEx.addIgnoredResource("http://soapinterop.org/xsd2");
    });

    doTest(getFullRelativeTestName(".xml"), true, false);
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testExternalValidatorOnValidXmlWithNamespacesNotSetup2() throws Exception {
    final ExternalResourceManagerEx instanceEx = ExternalResourceManagerEx.getInstanceEx();
    try {
      WriteCommandAction.runWriteCommandAction(null, () -> instanceEx.addIgnoredResource(""));

      doTest(getFullRelativeTestName(".xml"), true, false);
    } finally {
      WriteCommandAction.runWriteCommandAction(null, () -> instanceEx.removeIgnoredResource(""));
    }
  }

  public void testXercesMessagesBinding() throws Exception {
    doTest(getFullRelativeTestName(".xml"), true, false);
  }

  public void testXercesMessagesBinding3() throws Exception {
    doTest(getFullRelativeTestName(".xml"), true, false);
  }

  public void testXercesMessagesBinding4() throws Exception {
    String url = "antlib:org.apache.maven.artifact.ant";

    enableInspectionTool(new AntResolveInspection());
    doTestWithLocations(new String[][]{{url, getTestName(false) + ".xsd"}}, "xml");
  }

  public void testWrongRegExpInSchema() throws Exception {
    doTest(getFullRelativeTestName(".xsd"), true, false);
    doTest(getFullRelativeTestName("2.xsd"), true, false);
  }

  public void testWrongRegExpCategory() throws Exception {
    doTest(getFullRelativeTestName(".xsd"), true, false);
  }

  public void testXercesMessagesBinding2() throws Exception {
    final String url = getTestName(false) + ".xsd";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, url, getTestRootDisposable());

    doTest(
      new VirtualFile[] {
        getVirtualFile(getFullRelativeTestName(".xml")),
        getVirtualFile(getFullRelativeTestName(".xsd"))
      },
      true,
      false
    );

    final String url2 = getTestName(false) + "_2.xsd";

    ExternalResourceManagerExImpl.registerResourceTemporarily(url2, url2, getTestRootDisposable());

    doTest(
      new VirtualFile[] {
        getVirtualFile(getFullRelativeTestName("_2.xml")),
        getVirtualFile(getFullRelativeTestName("_2.xsd"))
      },
      true,
      false
    );

  }

  public void testValidationOfDtdFile() throws Exception {
    doTest(getFullRelativeTestName(".dtd"), false, false);
  }

  public void testXHtmlValidation() throws Exception {
    doTest();
  }

  public void testXHtmlValidation2() throws Exception {
    configureByFile(getFullRelativeTestName());
    doDoTest(true, true, true);
  }

  public void testXHtmlValidation3() throws Exception {
    doManyFilesFromSeparateDirTest(
      "http://www.w3.org/TR/xhtml-basic/xhtml-basic11.dtd",
      "xhtml-basic11.dtd",
      () -> {
        final List<XmlAttribute> attrs = new ArrayList<>();

        myFile.acceptChildren(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlAttribute(final XmlAttribute attribute) {
            if (attribute.getDescriptor() != null) attrs.add(attribute);
          }
        });

        assertEquals(8, attrs.size());
        for (XmlAttribute a : attrs) {
          final PsiElement element = a.getDescriptor().getDeclaration();
          assertTrue(((Navigatable)element).canNavigate());
        }
      }
    );
  }

  public void testReferencingTargetNamespace() throws Exception {
    doTest();
  }

  public void testWebApp() throws Exception {
    doTest();
  }

  public void testEnumeratedAttributesValidation() throws Exception {
    doTest(BASE_PATH + getTestName(false) + ".xsd", false, false);
  }

  public void testStackOverflow() throws Exception {
    String location = "relaxng.xsd";
    String url = "http://relaxng.org/ns/structure/fake/1.0";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    doTest(new VirtualFile[]{getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location)}, false, false);
  }

  public void testStackOverflow2() throws Exception {
    final String url = "urn:aaa";
    final String location = getTestName(false) + ".xsd";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    doTest(getFullRelativeTestName(".xsd"), false, false);

  }

  public void testComplexSchemaValidation() throws Exception {
//    disableHtmlSupport();
    try {
      doTest(getFullRelativeTestName(), false, false);
    }
    finally {
//      enableHtmlSupport();
    }
  }

  public void testComplexDtdValidation() throws Exception {
    String location = "Tapestry_3_0.dtd";
    String url = "http://jakarta.apache.org/tapestry/dtd/Tapestry_3_0.dtd";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());

    myTestJustJaxpValidation = true;
    doTest(new VirtualFile[] { getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location) }, false,false);
    myTestJustJaxpValidation = false;
  }

  public void testComplexDtdValidation2() throws Exception {
    String location = getTestName(false)+".dtd";
    ExternalResourceManagerExImpl.registerResourceTemporarily(location, location, getTestRootDisposable());

    myTestJustJaxpValidation = true;
    doTest(new VirtualFile[] { getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location) }, false,false);
    myTestJustJaxpValidation = false;
  }

  public void testComplexSchemaValidation2() throws Exception {
    doTest(new VirtualFile[] { getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + "jdo_2_0.xsd") }, false,false);
  }

  public void testComplexSchemaValidation3() throws Exception {
    List<VirtualFile> files = new ArrayList<>();
    files.add(getVirtualFile(getFullRelativeTestName()));

    final VirtualFile virtualFile = getVirtualFile(BASE_PATH + "ComplexSchemaValidation3Schemas");
    ContainerUtil.addAll(files, virtualFile.getChildren());

    doTest(VfsUtilCore.toVirtualFileArray(files), true, false);
  }

  public void testComplexSchemaValidation4() throws Exception {
    String[][] urls = {{"http://www.unece.org/etrades/unedocs/repository/codelists/xml/CountryCode.xsd"},
      {"http://www.swissdec.ch/schema/sd/20050902/SalaryDeclarationServiceTypes"},
      {"http://www.swissdec.ch/schema/sd/20050902/SalaryDeclarationContainer"},
      {"http://www.swissdec.ch/schema/sd/20050902/SalaryDeclaration"}};

    doSchemaTestWithManyFilesFromSeparateDir(urls, null);
  }

  public void testComplexSchemaValidation5() throws Exception {
    String location = getTestName(false)+".xsd";
    String url = "http://www.etas.com/TELEGY/Test";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    doTest(new VirtualFile[] { getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location) }, false,false);

    final XmlTag[] subTags = ((XmlFile)myFile).getDocument().getRootTag().getSubTags();

    for(XmlTag t:subTags) {
      final PsiReference reference = t.getReference();
      assertNotNull(reference);
      final PsiElement psiElement = reference.resolve();
      assertTrue(psiElement instanceof XmlTag && "xsd:element".equals(((XmlTag)psiElement).getName()));
    }

  }

  @Override
  protected boolean doExternalValidation() {
    return !methodOfTestHasAnnotation(getClass(), getTestName(false), HighlightingFlag.SkipExternalValidation) && super.doExternalValidation();
  }

  public void testComplexSchemaValidation6() throws Exception {
    String location = getTestName(false)+".xsd";
    String url = "http://abcde/pg.html";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    doTest(new VirtualFile[]{getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location)}, true, false);
  }

  public void testComplexSchemaValidation7() throws Exception {
    String[][] urlLocationPairs = {
      {"http://schemas.xmlsoap.org/wsdl/","wsdl.xsd"},
      {"http://schemas.xmlsoap.org/wsdl/soap/","soap.xsd"},
      {"http://schemas.xmlsoap.org/soap/encoding/","encoding.xsd"},
      {"http://schemas.xmlsoap.org/wsdl/mime/","mime.xsd"},
      {"http://schemas.xmlsoap.org/wsdl/http/","http.xsd"}
    };


    doSchemaTestWithManyFilesFromSeparateDir(
      urlLocationPairs,
      files -> {
        try {
          files.set(0, getVirtualFile(BASE_PATH + getTestName(false) + "_2.xml"));
          doTest(VfsUtilCore.toVirtualFileArray(files), true, false);
          return true;
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    );
  }

  public void testComplexSchemaValidation7_() throws Exception {
    doTest(
      new VirtualFile[]{
        getVirtualFile(getFullRelativeTestName()),
        getVirtualFile(getFullRelativeTestName(".xsd"))
      },
      false,
      false
    );
  }

  public void testWsdlValidation() throws Exception {
    doTest(getFullRelativeTestName(".wsdl"), false, false);
  }

  public void testErrorInDtd() throws Exception {
    String location = "def_xslt.dtd";
    String url = "http://www.w3.org/1999/XSL/Transform";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    doTest(new VirtualFile[]{getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location)}, false, false);
  }

  public void testMavenValidation() throws Exception {
    doTest(getFullRelativeTestName(), false, false);
  }

  public void testResolveEntityUrl() throws Throwable {
    doTest(new VirtualFile[] {
      getVirtualFile(getFullRelativeTestName()),
      getVirtualFile(BASE_PATH + "entities.dtd")
    }, false, false);
  }

  public void testXsiSchemaLocation() throws Exception {
    doTest();
    doTest(getFullRelativeTestName("_2.xml"), false, false);
  }

  public void testXmlPsi() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".xml");

    final XmlTag root = ((XmlFile)myFile).getDocument().getRootTag();
    final XmlTag tag1 = root.getSubTags()[0];
    assert tag1.getLocalName().equals("foo");

    assertEquals("xxx" ,tag1.getAttributeValue("xxx", "abc"));

    final XmlTag tag2 = root.getSubTags()[0].getSubTags()[0];
    assert tag2.getLocalName().equals("bar");

    assertEquals("yyy", tag2.getAttributeValue("yyy", "abc"));
    assertEquals("zzz", tag2.getAttributeValue("zzz"));
    assertNull(tag2.getAttributeValue("zzz", "unknown"));
    assertNull(tag2.getAttributeValue("zzz", "abc"));

    final XmlTag tag3 = root.getSubTags()[0].getSubTags()[1];
    assert tag3.getLocalName().equals("bar2");
    assertEquals("zzz", tag3.getAttributeValue("zzz", "def"));
  }

  public void testXsiSchemaLocation2() throws Exception {
    doTest(
      new VirtualFile[]{
        getVirtualFile(getFullRelativeTestName()),
        getVirtualFile(BASE_PATH + getTestName(false) + "_1.xsd"),
        getVirtualFile(BASE_PATH + getTestName(false) + "_2.xsd"),
        getVirtualFile(BASE_PATH + getTestName(false) + "_3.xsd"),
      },
      false,
      false
    );

    XmlTag rootTag = ((XmlFile)myFile).getDocument().getRootTag();
    checkOneTagForSchemaAttribute(rootTag, "xmlns:test2", getTestName(false) + "_2.xsd");

    configureByFiles(null, getVirtualFile(BASE_PATH + getTestName(false) + "_2.xsd"), getVirtualFile(BASE_PATH + getTestName(false) + "_3.xsd"));

    rootTag = ((XmlFile)myFile).getDocument().getRootTag();
    final List<XmlTag> tags = new ArrayList<>();

    XmlUtil.processXmlElements(
      rootTag,
      new PsiElementProcessor() {
        @Override
        public boolean execute(@NotNull final PsiElement element) {
          if (element instanceof XmlTag &&
              (((XmlTag)element).getName().equals("xs:element") ||
               ((XmlTag)element).getName().equals("xs:attribute") ||
               ((XmlTag)element).getName().equals("xs:restriction") ||
               ((XmlTag)element).getName().equals("xs:group") ||
               ((XmlTag)element).getName().equals("xs:attributeGroup")
              ) &&
                ( ((XmlTag)element).getAttributeValue("type") != null ||
                  ((XmlTag)element).getAttributeValue("ref") != null ||
                  ((XmlTag)element).getAttributeValue("base") != null
                )
             ) {
            tags.add((XmlTag)element);
          }
          return true;
        }
      },
      true
    );

    assertEquals("Should be adequate number of tags", 11, tags.size());

    final String resolveFileName = getTestName(false) + "_3.xsd";
    checkOneTagForSchemaAttribute(tags.get(1), "type", resolveFileName);
    checkOneTagForSchemaAttribute(tags.get(0), "ref", resolveFileName);
    checkOneTagForSchemaAttribute(tags.get(3), "type", resolveFileName);
    checkOneTagForSchemaAttribute(tags.get(4), "base", resolveFileName);
    final String schemaFile = "XMLSchema.xsd";
    checkOneTagForSchemaAttribute(tags.get(5), "base", schemaFile);

    checkOneTagForSchemaAttribute(rootTag, "xmlns:xs", schemaFile);
    checkOneTagForSchemaAttribute(tags.get(6), "ref", resolveFileName);
    checkOneTagForSchemaAttribute(tags.get(7), "ref", resolveFileName);
    checkOneTagForSchemaAttribute(rootTag, "xmlns:test2", getTestName(false) + "_2.xsd");

    checkOneTagForSchemaAttribute(tags.get(8), "type", schemaFile);
    checkOneTagForSchemaAttribute(tags.get(9), "ref", schemaFile);
    checkOneTagForSchemaAttribute(tags.get(10), "ref", resolveFileName);
  }

  private static void checkOneTagForSchemaAttribute(XmlTag tag,String schemaAttrName, String resolveFileName) {
    String attributeValue = tag.getAttributeValue(schemaAttrName);

    if (attributeValue != null) {
      XmlAttribute attribute = tag.getAttribute(schemaAttrName, null);
      PsiReference[] references = attribute.getValueElement().getReferences();
      assertTrue(
        "There should resolvable reference to " + schemaAttrName,
        references.length > 0
      );

      for (PsiReference reference : references) {
        PsiElement element = reference.resolve();
        if (element != null && element.getContainingFile().getName().equals(resolveFileName)) {
          return;
        }
      }
      assertTrue("There should resolvable reference to "+ schemaAttrName + ", 2", true);
    }
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RequiredAttributesInspection(),
      new XmlDuplicatedIdInspection(),
      new XmlInvalidIdInspection(),
      new CheckDtdReferencesInspection(),
      new XmlUnboundNsPrefixInspection(),
      new XmlPathReferenceInspection()
    };
  }

  @HighlightingFlags({HighlightingFlag.SkipExternalValidation})
  public void testXsltValidation() throws Exception {
    doTest(getFullRelativeTestName(".xsl"), true, false);
    doTest(getFullRelativeTestName("2.xsl"), true, false);
  }

  public void testXsltValidation3() throws Exception {
    doTest(getFullRelativeTestName(".xsl"), true, false);
  }

  public void testXsiSchemaLocation3() throws Exception {

    configureByFile(getFullRelativeTestName(".xsd"), null);

    doDoTest(true,false);

    final List<XmlTag> tags = new ArrayList<>();

    XmlUtil.processXmlElements(
      ((XmlFile)myFile).getDocument(),
      new PsiElementProcessor() {
        @Override
        public boolean execute(@NotNull final PsiElement element) {
          if (element instanceof XmlTag &&
              ((XmlTag)element).getName().equals("xs:include")
             ) {
            tags.add((XmlTag)element);
          }
          return true;
        }
      },
      true
    );

    assertEquals("Should be three tags", 3, tags.size());
    String location = "xslt-1_0.xsd";
    checkOneTagForSchemaAttribute(tags.get(2), "schemaLocation", location);
  }

  public void testResolvingEntitiesInDtd() throws Exception {
    configureByFiles(null, getVirtualFile(BASE_PATH + getTestName(false) + ".xml"),
                     getVirtualFile(BASE_PATH + getTestName(false) + "/RatingandServiceSelectionRequest.dtd"),
                     getVirtualFile(BASE_PATH + getTestName(false) + "/XpciInterchange.dtd"),
                     getVirtualFile(BASE_PATH + getTestName(false) + "/Xpcivocabulary.dtd"));
    doDoTest(true, false);
  }

  // TODO: external validator should not be lauched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testEntityHighlighting() throws Exception {
    doTest();
    final XmlTag rootTag = ((XmlFile)myFile).getDocument().getRootTag();
    final List<XmlEntityRef> refs = new ArrayList<>();

    XmlUtil.processXmlElements(
      rootTag,
      new PsiElementProcessor() {
        @Override
        public boolean execute(@NotNull final PsiElement element) {
          if (element instanceof XmlEntityRef) {
            refs.add((XmlEntityRef)element);
          }
          return true;
        }
      },
      true
    );

    assertEquals("Should be 2 entity refs",2,refs.size());

    PsiReference[] entityRefs = refs.get(0).getReferences();
    assertTrue(entityRefs.length == 1 &&
               entityRefs[0].resolve() == null
              );
    entityRefs = refs.get(1).getReferences();
    assertTrue(entityRefs.length == 1 &&
               entityRefs[0].resolve() != null
              );

    doTest(getFullRelativeTestName("2.xml"), false, false);
  }

  public void testEntityRefBadFormat() throws Exception {
    doTest();
  }

  public void testIgnoredNamespaceHighlighting() throws Exception {
    WriteCommandAction.runWriteCommandAction(null, () -> ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("http://ignored/uri"));

    doTest();
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManagerEx.getInstanceEx().removeIgnoredResource("http://ignored/uri"));
  }

  public void testNonEnumeratedValuesHighlighting() throws Exception {
    final String url = "http://www.w3.org/1999/XSL/Format";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, "fop.xsd", getTestRootDisposable());
    configureByFiles(null, getVirtualFile(BASE_PATH + getTestName(false) + ".xml"), getVirtualFile(BASE_PATH + "fop.xsd"));
    doDoTest(true, false);
  }

  public void testEditorHighlighting() throws Exception {
    //                       10
    //             0123456789012
    String text = "<html></html>";
    EditorHighlighter xmlHighlighter = XmlHighlighterFactory.createXMLHighlighter(EditorColorsManager.getInstance().getGlobalScheme());
    xmlHighlighter.setText(text);
    HighlighterIterator iterator = xmlHighlighter.createIterator(1);
    assertSame("Xml tag name", iterator.getTokenType(), XmlTokenType.XML_TAG_NAME);
    iterator = xmlHighlighter.createIterator(8);
    assertSame("Xml tag name at end of tag", iterator.getTokenType(), XmlTokenType.XML_TAG_NAME);

    //               10        20         30
    //      0123456789012345678901234567890
    text = "<a:xxx /> <a:xxx attr = \"1111\"/>";
    xmlHighlighter.setText(text);

    iterator = xmlHighlighter.createIterator(6);
    assertEquals(XmlTokenType.TAG_WHITE_SPACE, iterator.getTokenType());

    iterator = xmlHighlighter.createIterator(21);
    assertEquals(XmlTokenType.TAG_WHITE_SPACE, iterator.getTokenType());

    iterator = xmlHighlighter.createIterator(23);
    assertEquals(XmlTokenType.TAG_WHITE_SPACE, iterator.getTokenType());

    iterator = xmlHighlighter.createIterator(9);
    assertEquals(XmlTokenType.XML_REAL_WHITE_SPACE, iterator.getTokenType());

    //                10        20        30        40          50        60
    //      012345678901234567890123456789012345678901 234567 890123456789012 345678 90
    text = "<!DOCTYPE schema [ <!ENTITY RelativeURL  \"[^:#/\\?]*(:{0,0}|[#/\\?].*)\">";
    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(53);
    assertSame("Xml attribute value", iterator.getTokenType(), XmlTokenType.XML_DATA_CHARACTERS);
    assertEquals(iterator.getStart(),41);
    assertEquals(iterator.getEnd(),70);

    //              10        20        30        40          50        60
    //    012345678901234567890123456789012345678901 234567 890123456789012 345678 90
    text="<a name='$'/>";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(9);
    assertEquals("$ in attr value",XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,iterator.getTokenType());
    iterator = xmlHighlighter.createIterator(10);
    assertEquals("' after $ in attr value",XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,iterator.getTokenType());

    //                10         20         30        40           50        60
    //      0123456789012345678 901 23456789012345678901 2345 67 890123456789012 345678 90
    text = "<project><jar file=\"${\"> </jar><jar file=\"${}\"/></project> ";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(22);
    assertEquals("${ in attr value",XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,iterator.getTokenType());

    //                10
    //      01234567890123456789
    text = "<!--&nbsp; &aaa;-->";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(5);
    assertEquals(
      "char entity in comment",
      XmlTokenType.XML_COMMENT_CHARACTERS,
      iterator.getTokenType()
    );

    iterator = xmlHighlighter.createIterator(12);
    assertEquals(
      "entity ref in comment",
      XmlTokenType.XML_COMMENT_CHARACTERS,
      iterator.getTokenType()
    );

    //                10
    //      01234567890123456789
    text = "<!-- -- -->";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(5);
    assertEquals(
      "double -- in xml comment",
      XmlTokenType.XML_BAD_CHARACTER,
      iterator.getTokenType()
    );

  }

  public void testUsingDtdReference() throws Exception {
    final String baseName = BASE_PATH + getTestName(false);

    configureByFiles(null, getVirtualFile(baseName + ".xml"), getVirtualFile(baseName + ".dtd"), getVirtualFile(baseName + "2.dtd"));
    doDoTest(true, false);
  }

  public void testUnboundNsHighlighting() throws Exception {
    configureByFile(BASE_PATH + "web-app_2_4.xsd");
    final String testName = getTestName(false);
    doTest(BASE_PATH + testName +".xml",true, false);

    doTestWithUnboundNSQuickFix(BASE_PATH + testName + "2");
//    doTestWithUnboundNSQuickFix(BASE_PATH + testName + "3");

    configureByFiles(null, BASE_PATH + testName +"4.xml", BASE_PATH + testName +"4.dtd");
    doDoTest(true, false);

    doTestWithUnboundNSQuickFix(BASE_PATH + testName + "5");
  }

  public void testUnboundNsHighlighting6() throws Exception {
    configureByFile(BASE_PATH + "web-app_2_4.xsd");
    doTestWithQuickFix(BASE_PATH + getTestName(false), CREATE_NAMESPACE_DECLARATION_INTENTION_NAME, true);
  }

  private void doTestWithUnboundNSQuickFix(final String s) throws Exception {
    doTestWithQuickFix(s, CREATE_NAMESPACE_DECLARATION_INTENTION_NAME, false);
  }

  private void doTestWithQuickFix(final String s, final String intentionActionName, boolean withInfos) throws Exception {
    configureByFile(s + ".xml");
    Collection<HighlightInfo> infos = doDoTest(true, withInfos);

    findAndInvokeIntentionAction(infos, intentionActionName, myEditor, myFile);
    checkResultByFile(s + "_after.xml");
  }

  public void testSpecifyXsiSchemaLocationQuickFix() throws Exception {
    configureByFile(BASE_PATH + "web-app_2_4.xsd");
    final String testName = getTestName(false);
    final String actionName = XmlBundle.message(AddXsiSchemaLocationForExtResourceAction.KEY);
    doTestWithQuickFix(BASE_PATH + testName, actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "2", actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "3", actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "4", actionName, false);
  }

  public void testHighlightingWithConditionalSectionsInDtd() throws Exception {
    final String testName = getTestName(false);

    configureByFiles(null, getVirtualFile(BASE_PATH + testName +".xml"), getVirtualFile(BASE_PATH + testName +".dtd"));
    doDoTest(true, false);

    configureByFiles(null, getVirtualFile(BASE_PATH + testName +"2.xml"), getVirtualFile(BASE_PATH + testName +".dtd"));
    doDoTest(true, false);
  }

  public void testUnresolvedDtdElementReferences() throws Exception {
    doDtdRefsWithQuickFixTestSequence(BASE_PATH + getTestName(false), "dtd");
  }

  public void testResolvedDtdElementReferences() throws Exception {
    configureByFile(BASE_PATH + getTestName(false)+".dtd");
    doDoTest(true, false);

    final String text = myEditor.getDocument().getText();
    WriteCommandAction.runWriteCommandAction(null, () -> myEditor.getSelectionModel().setSelection(0, myEditor.getDocument().getTextLength()));

    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_COMMENT_BLOCK);
    action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext()));
    assertNotSame(text,myEditor.getDocument().getText());
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
    Collection<HighlightInfo> infos = doHighlighting();
    assertEquals(0, infos.size());

    action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext()));
    assertEquals(text,myEditor.getDocument().getText().trim());
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
    infos = doHighlighting();
    assertEquals(0, infos.size());
  }

  // TODO: external validator should not be lauched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testUnresolvedDtdElementReferencesInXml() throws Exception {
    doDtdRefsWithQuickFixTestSequence(BASE_PATH + getTestName(false), "xml");
  }

  public void testUnresolvedDtdElementReferencesInXml2() throws Exception {
    final String s = BASE_PATH + getTestName(false);
    doDtdEntityRefWithQuickFixTest(s, "xml");
    doDtdEntityRefWithQuickFixTest(s + "_2", "xml");
  }

  public void testUnresolvedDtdElementReferencesInXml3() throws Exception {
    doDtdEntityRefWithQuickFixTest(BASE_PATH + getTestName(false), "xml");
  }

  public void testUnresolvedDtdEntityReference() throws Exception {
    final String s = BASE_PATH + getTestName(false);
    doDtdEntityRefWithQuickFixTest(s, "dtd");
  }

  private void doDtdEntityRefWithQuickFixTest(final String s, String ext) throws Exception {
    configureByFile(s + "." + ext);
    Collection<HighlightInfo> infos = doDoTest(true, false);
    findAndInvokeIntentionAction(infos, "Create Entity Declaration entity", myEditor, myFile);
    checkResultByFile(s+"_after" + "." + ext);
  }

  private void doDtdRefsWithQuickFixTestSequence(final String s, final String extension) throws Exception {
    configureByFile(s + "." + extension);
    Collection<HighlightInfo> infos = doDoTest(true, false);

    findAndInvokeIntentionAction(infos, "Create Element Declaration aaa", myEditor, myFile);
    checkResultByFile(s+"_after." + extension);
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
    infos = doHighlighting();
    findAndInvokeIntentionAction(infos, "Create Entity Declaration entity", myEditor, myFile);
    checkResultByFile(s+"_after2." + extension);
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testDocBookHighlighting() throws Exception {
    doManyFilesFromSeparateDirTest("http://www.oasis-open.org/docbook/xml/4.4/docbookx.dtd", "docbookx.dtd", () -> {
      XmlTag rootTag = ((XmlFile)myFile).getDocument().getRootTag();
      PsiElement psiElement = rootTag.getReferences()[0].resolve();
      assertTrue(((Navigatable)psiElement).canNavigate());
    });
  }

  public void testDocBookHighlighting2() throws Exception {
    doManyFilesFromSeparateDirTest("http://www.oasis-open.org/docbook/xml/4.5/docbookx.dtd", "docbookx.dtd", null);
  }

  public void testOasisDocumentHighlighting() throws Exception {
    doManyFilesFromSeparateDirTest("concept.dtd", "concept.dtd", null);
  }

  private void doManyFilesFromSeparateDirTest(final String url, final String mainDtdName, @Nullable Runnable additionalTestAction) throws Exception {
    List<VirtualFile> files = new ArrayList<>();
    files.add(getVirtualFile(getFullRelativeTestName()));

    final VirtualFile virtualFile = getVirtualFile(BASE_PATH + getTestName(false));
    ContainerUtil.addAll(files, virtualFile.getChildren());

    ExternalResourceManagerExImpl
      .registerResourceTemporarily(url, UriUtil.findRelativeFile(mainDtdName, virtualFile).getPath(), getTestRootDisposable());
    doTest(VfsUtilCore.toVirtualFileArray(files), true, false);

    if (additionalTestAction != null) additionalTestAction.run();

  }

  private void doSchemaTestWithManyFilesFromSeparateDir(final String[][] urls, @Nullable Processor<List<VirtualFile>> additionalTestingProcessor) throws Exception {
    try {
      List<VirtualFile> files = new ArrayList<>(6);
      files.add( getVirtualFile(BASE_PATH + getTestName(false) + ".xml"));

      final Set<VirtualFile> usedFiles = new THashSet<>();
      final String base = BASE_PATH + getTestName(false) + "Schemas/";

      for(String[] pair:urls) {
        final String url = pair[0];
        final String filename = pair.length > 1 ? pair[1]:url.substring(url.lastIndexOf('/')+1) + (url.endsWith(".xsd")?"":".xsd");

        final VirtualFile virtualFile = getVirtualFile(base + filename);
        usedFiles.add(virtualFile);

        if (url != null) ExternalResourceManagerExImpl.registerResourceTemporarily(url, virtualFile.getPath(), getTestRootDisposable());
        files.add( virtualFile );
      }

      for(VirtualFile file: LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + base.substring(0, base.length() - 1)).getChildren()) {
        if (!usedFiles.contains(file)) {
          files.add(file);
        }
      }

      doTest(VfsUtilCore.toVirtualFileArray(files), true, false);

      if (additionalTestingProcessor != null) additionalTestingProcessor.process(files);
    }
    finally {
      unregisterResources(urls);
    }
  }

  enum HighlightingFlag {
    SkipExternalValidation
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  @interface HighlightingFlags {
    HighlightingFlag[] value() default {};
  }

  private static boolean methodOfTestHasAnnotation(final Class testClass, final String testName, final HighlightingFlag flag) {
    Method method;
    try {
      method = testClass.getMethod("test" + testName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    final HighlightingFlags annotation = method.getAnnotation(HighlightingFlags.class);
    if (annotation != null) {
      final HighlightingFlag[] testOptions = annotation.value();

      if (testOptions != null) {
        Arrays.sort(testOptions);
        return Arrays.binarySearch(testOptions, flag) >= 0;
      }
    }

    return false;
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testSchemaUpdate() throws IOException {
    String location = getTestName(false)+".xsd";
    String url = "http://example.org/ns/books/";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    configureByFiles(null, getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location));

    doDoTest(true,false);

    Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    final Editor schemaEditor = allEditors[0] == myEditor ? allEditors[1]:allEditors[0];
    final String text = schemaEditor.getDocument().getText();
    final String newText = text.replaceAll("xsd", "xs");
    WriteCommandAction.runWriteCommandAction(null, () -> schemaEditor.getDocument().replaceString(0, text.length(), newText));

    doDoTest(true, false);
  }

  public void testXHtmlEditorHighlighting() throws Exception {
    //                       10
    //             0123456789012
    String text = "<html></html>";
    EditorHighlighter xhtmlHighlighter = HighlighterFactory
      .createHighlighter(StdFileTypes.XHTML, EditorColorsManager.getInstance().getGlobalScheme(), myProject);
    xhtmlHighlighter.setText(text);
    HighlighterIterator iterator = xhtmlHighlighter.createIterator(1);
    assertSame("Xml tag name", iterator.getTokenType(), XmlTokenType.XML_TAG_NAME);
    iterator = xhtmlHighlighter.createIterator(8);
    assertSame("Xml tag name at end of tag", iterator.getTokenType(), XmlTokenType.XML_TAG_NAME);

  }

  public void testSchemaValidation6() throws Exception {
    String location = getTestName(false)+".xsd";
    String url = "aaa";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    configureByFiles(null, getVirtualFile(getFullRelativeTestName()), getVirtualFile(BASE_PATH + location));

    doDoTest(true, false);

  }

  public void testDTDEditorHighlighting() throws Exception {
    //                       10        20
    //             012345678901234567890 123456 789
    String text = "<!ENTITY % Charsets \"CDATA\">";
    EditorHighlighter dtdHighlighter = HighlighterFactory.createHighlighter(StdFileTypes.DTD,EditorColorsManager.getInstance().getGlobalScheme(),myProject);
    dtdHighlighter.setText(text);
    HighlighterIterator iterator = dtdHighlighter.createIterator(3);

    assertSame("Xml entity name", iterator.getTokenType(), XmlTokenType.XML_ENTITY_DECL_START);
    iterator = dtdHighlighter.createIterator(13);
    assertSame("Xml name in dtd", iterator.getTokenType(), XmlTokenType.XML_NAME);
    iterator = dtdHighlighter.createIterator(23);
    assertSame("Xml attribute value in dtd", iterator.getTokenType(), XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN);

    //                10        20        30        40
    //      0123456789012345678901 2345678901234567890123456789
    text = "<!ELEMENT base EMPTY>\n<!ATTLIST base id ID #IMPLIED>";
    dtdHighlighter.setText(text);
    iterator = dtdHighlighter.createIterator(3);
    assertSame("Xml element name", iterator.getTokenType(), XmlTokenType.XML_ELEMENT_DECL_START);
    iterator = dtdHighlighter.createIterator(25);
    assertSame("Xml attr list", iterator.getTokenType(), XmlTokenType.XML_ATTLIST_DECL_START);

    iterator = dtdHighlighter.createIterator(14);
    assertSame("Xml attr list", iterator.getTokenType(), TokenType.WHITE_SPACE);

    iterator = dtdHighlighter.createIterator(21);
    assertSame("Xml attr list", iterator.getTokenType(), TokenType.WHITE_SPACE);

    //                10        20        30        40        50        60
    //      0123456789012345678901234567890123456789012345678901234567890123456789
    text = "<![%sgml;[<![IGNORE[<![ INCLUDE [<!ENTITY % aaa SYSTEM 'zzz'>]]>]]>]]>";

    dtdHighlighter.setText(text);
    iterator = dtdHighlighter.createIterator(2);
    assertEquals("Xml conditional section start",XmlTokenType.XML_CONDITIONAL_SECTION_START,iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(67);
    assertEquals("Xml conditional section end",XmlTokenType.XML_CONDITIONAL_SECTION_END,iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(5);
    assertEquals("entity ref in conditional section",XmlTokenType.XML_ENTITY_REF_TOKEN,iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(15);
    assertEquals("ignore in conditional section",XmlTokenType.XML_CONDITIONAL_IGNORE,iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(27);
    assertEquals("include in conditional section",XmlTokenType.XML_CONDITIONAL_INCLUDE,iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(9);
    assertEquals("markup start in conditional section",XmlTokenType.XML_MARKUP_START,iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(33);
    assertEquals("entity decl start in conditional section",XmlTokenType.XML_ENTITY_DECL_START,iterator.getTokenType());

    //                10        20          30         40        50        60         70
    //      012345678901234567890123 456789 0 123456789012345678901234567890123456 7890123456789
    text = "<!ENTITY % ContentType \"CDATA\"\n    -- media type, as per [RFC2045]\n    -- --xxx-->";
    dtdHighlighter.setText(text);
    iterator = dtdHighlighter.createIterator(35);
    assertEquals("Dtd comment start",XmlTokenType.XML_COMMENT_START,iterator.getTokenType());
    iterator = dtdHighlighter.createIterator(40);
    assertEquals("Dtd comment content",XmlTokenType.XML_COMMENT_CHARACTERS,iterator.getTokenType());
    iterator = dtdHighlighter.createIterator(71);
    assertEquals("Dtd comment end",XmlTokenType.XML_COMMENT_END,iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(78);
    assertEquals("Dtd comment content",XmlTokenType.XML_COMMENT_CHARACTERS,iterator.getTokenType());
  }

  public void testSgmlDTD() throws Exception {
    doTest(getFullRelativeTestName(".dtd"), true, false);
  }

  public void testImportProblems() throws Exception {
    final String testName = getTestName(false);
    configureByFiles(
      null,
      BASE_PATH + testName +".xsd",
      BASE_PATH +testName +"2.xsd"
    );
    doDoTest(true, false, true);
  }

  public void testEncoding() throws Exception { doTest(true); }

  public void testSchemaImportHighlightingAndResolve() throws Exception {
    doTestWithLocations(new String[][]{{"http://www.springframework.org/schema/beans", "ComplexSchemaValidation11.xsd"}}, "xsd");
  }

  public void testDocBookV5() throws Exception {
    doTestWithLocations(
      new String[][] {
        {"http://docbook.org/ns/docbook", "DocBookV5.xsd"},
        {"http://www.w3.org/1999/xlink", "xlink.xsd"},
        {"http://www.w3.org/XML/1998/namespace", "xml.xsd"}
      },
      "xml"
    );

    doTestWithLocations(
      new String[][] {
        {"http://www.w3.org/1999/xlink", "xlink.xsd"},
        {"http://www.w3.org/XML/1998/namespace", "xml.xsd"}
      },
      "xsd"
    );
  }

  public void testDocBook5() throws Exception {
    doTestWithLocations(
      new String[][] {
        {"http://docbook.org/ns/docbook", "DocBookV5.xsd"},
        {"http://www.w3.org/1999/xlink", "xlink.xsd"},
        {"http://www.w3.org/XML/1998/namespace", "xml.xsd"}
      },
      "xml"
    );
  }

  public void testDocBookRole() throws Exception {
    doTestWithLocations(
      new String[][] {
        {"http://docbook.org/ns/docbook", "DocBookV5.xsd"},
        {"http://www.w3.org/1999/xlink", "xlink.xsd"},
        {"http://www.w3.org/XML/1998/namespace", "xml.xsd"}
      },
      "xml"
    );
  }

  public void testCorrectGeneratedDtdUpdate() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".xml");
    Collection<HighlightInfo> infos = filterInfos(doHighlighting());
    assertEquals(2, infos.size());

    WriteCommandAction.runWriteCommandAction(null, () -> EditorModificationUtil.deleteSelectedText(myEditor));

    infos = filterInfos(doHighlighting());

    assertEquals(11, infos.size());

    WriteCommandAction.runWriteCommandAction(null, () -> EditorModificationUtil.insertStringAtCaret(myEditor, "<"));

    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(myProject, myEditor);
    infos = filterInfos(doHighlighting());
    assertEquals(2, infos.size());
    LookupManager.getInstance(myProject).hideActiveLookup();
  }

  private static Collection<HighlightInfo> filterInfos(final Collection<HighlightInfo> highlightInfos) {
    for(Iterator<HighlightInfo> i = highlightInfos.iterator(); i.hasNext();) {
      final HighlightInfo highlightInfo = i.next();
      if (highlightInfo.getSeverity() == HighlightSeverity.INFORMATION) i.remove();
    }
    return highlightInfos;
  }

  public void testUsingSchemaDtd() throws Exception {
    doTestWithLocations(null, "xsd");
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testDtdElementRefs() throws Exception {
    doTestWithLocations(
      new String[] [] {
        {"http://example.com/persistence","XIncludeTestSchema.xsd"}
      },
      "xml"
    );
  }

  public void testDuplicates() throws Exception {
    doTestWithLocations(null, "xsd");
    doTestWithLocations(null, "dtd");
    doTestWithLocations(null, "xml");
  }

  public void testMinMaxOccursInSchema() throws Exception {
    doTestWithLocations(null, "xsd");
  }

  public void testXslt2() throws Exception {
    doTestWithLocations(null, "xsl");
  }

  public void testDefaultAndFixedInSchema() throws Exception {
    doTestWithLocations(null, "xsd");
  }

  public void testAnyAttributesInAttrGroup() throws Exception {
    doTestWithLocations(
      new String[] [] {
        {"http://www.foo.org/test",getTestName(false)+".xsd"},
        {"http://www.bar.org/test",getTestName(false)+"_2.xsd"}
      },
      "xml"
    );
  }

  public void testXInclude() throws Exception {
    final String testName = getTestName(false);
    configureByFiles(
      null,
      BASE_PATH + testName +".xml",
      BASE_PATH +testName +"-inc.xml",
      BASE_PATH +testName +"TestSchema.xsd"
    );
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("oxf:/apps/somefile.xml"));

    doDoTest(true, false, true);

    VirtualFile[] includedFiles = FileIncludeManager.getManager(getProject()).getIncludedFiles(getFile().getVirtualFile(), true);
    assertEquals(1, includedFiles.length);
  }

  public void testComplexRedefine() throws Exception {
    final String[][] urls = {
      {"http://graphml.graphdrawing.org/xmlns","graphml.xsd"},
      {"http://graphml.graphdrawing.org/xmlns/1.0/graphml-structure.xsd","graphml-structure.xsd"},
      {"http://www.w3.org/1999/xlink","xlink.xsd"}
    };
    doSchemaTestWithManyFilesFromSeparateDir(urls, null);
  }

  public void testComplexRedefine2() throws Exception {
    final String[][] urls = {
      {"http://www.yworks.com/xml/schema/graphml/1.0/ygraphml.xsd","ygraphml.xsd"},
      {"http://graphml.graphdrawing.org/xmlns/1.0/xlink.xsd","xlink.xsd"}
    };
    doSchemaTestWithManyFilesFromSeparateDir(urls, null);
  }

  public void testMuleConfigHighlighting() throws Exception {
    final String[][] urls = {
      {"http://www.springframework.org/schema/beans/spring-beans-2.0.xsd", "spring-beans-2.0.xsd"},
      {"http://www.springframework.org/schema/tool", "spring-tool-2.5.xsd"},
      {"http://www.springframework.org/schema/context/spring-context-2.5.xsd", "spring-context-2.5.xsd"},
      {"urn:xxx","mule.xsd"},
      {"urn:yyy","mule-management.xsd"}
    };
    doTestWithLocations(urls, "xml");
  }

  public void testMuleConfigHighlighting2() throws Exception {
    String path = getTestName(false) + File.separatorChar;
    final String[][] urls = {
      {"http://www.springframework.org/schema/beans/spring-beans-2.0.xsd", "spring-beans-2.0.xsd"},
      {"http://www.springframework.org/schema/tool", "spring-tool-2.5.xsd"},
      {"http://www.springframework.org/schema/context/spring-context-2.5.xsd", "spring-context-2.5.xsd"},
      {"http://www.mulesource.org/schema/mule/core/2.2",path + "mule2_2.xsd"},
      {"http://www.mulesource.org/schema/mule/cxf/2.2",path + "mule2_2-cxf.xsd"},
      {"http://cxf.apache.org/core",path + "cxf_core.xsd"},
      {"http://cxf.apache.org/configuration/beans",path + "cxf-beans.xsd"},
      {"http://www.mulesource.org/schema/mule/schemadoc/2.2",path + "mule2_2-schemadoc.xsd"}
    };
    doTestWithLocations(urls, "xml");
  }

  @Override
  protected boolean clearModelBeforeConfiguring() {
    return "testComplexRedefine3".equals(getName());
  }

  public void testComplexRedefine3() throws Exception {
    final String testName = getTestName(false);

    VirtualFile[] files = {getVirtualFile(BASE_PATH + testName + "_2.xsd"), getVirtualFile(BASE_PATH + testName + "_3.xsd")};
    doTest(files, true, false);

    files = new VirtualFile[] {getVirtualFile(BASE_PATH + testName + ".xsd"),
      getVirtualFile(BASE_PATH + testName + "_2.xsd"), getVirtualFile(BASE_PATH + testName + "_3.xsd")};
    doTest(files, true, false);
  }

  public void testComplexRedefine4() throws Exception {
    final String testName = getTestName(false);
    VirtualFile[] files = {getVirtualFile(BASE_PATH + testName + ".xml"),
      getVirtualFile(BASE_PATH + testName + ".xsd"), getVirtualFile(BASE_PATH + testName + "_2.xsd")};
    doTest(files, true, false);
  }

  public void testComplexRedefine5() throws Exception {
    final String testName = getTestName(false);
    String[][] urls = {
      {"http://extended", testName + ".xsd"},
      {"http://simple", testName + "_2.xsd"}
    };
    doTestWithLocations(urls, "xml");
  }

  public void testComplexRedefine6() throws Exception {
    final String testName = getTestName(false);
    String[][] urls = {
      {"urn:jboss:bean-deployer:2.0", testName + ".xsd"},
      {"", testName + "_2.xsd"}
    };
    doTestWithLocations(urls,"xml");
  }

  public void testRedefineQualifiedType() throws Exception {
    final String testName = getTestName(false);
    VirtualFile[] files = {
      getVirtualFile(BASE_PATH + testName + ".xml"),
      getVirtualFile(BASE_PATH + testName + ".xsd"),
      getVirtualFile(BASE_PATH + testName + "_2.xsd")
    };
    doTest(files, true, false);
  }

  public void testRedefineBaseType() throws Exception {
    final String testName = getTestName(false);
    VirtualFile[] files = {
      getVirtualFile(BASE_PATH + testName + ".xml"),
      getVirtualFile(BASE_PATH + testName + ".xsd"),
      getVirtualFile(BASE_PATH + testName + "_2.xsd")
    };
    doTest(files, true, false);
  }

  public void testComplexSchemaValidation18() throws Exception {
    final String testName = getTestName(false);
    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + testName + ".xml"),
        getVirtualFile(BASE_PATH + testName + ".xsd"),
        getVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  public void testComplexSchemaValidation19() throws Exception {
    final String testName = getTestName(false);
    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + testName + ".xml"),
        getVirtualFile(BASE_PATH + testName + ".xsd"),
        getVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  public void testComplexSchemaValidation20() throws Exception {
    final String testName = getTestName(false);
    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + testName + ".xml"),
        getVirtualFile(BASE_PATH + testName + ".xsd"),
        getVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  public void testSubstitutionFromInclude() throws Exception {
    ExternalResourceManagerExImpl
      .registerResourceTemporarily("http://www.omg.org/spec/BPMN/20100524/MODEL", "BPMN20.xsd", getTestRootDisposable());

    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "FinancialReportProcess.bpmn20.xml"),
        getVirtualFile(BASE_PATH + "BPMN20.xsd"),
        getVirtualFile(BASE_PATH + "Semantic.xsd")
      },
      true,
      false
    );
  }

  public void testComplexRedefineFromJar() throws Exception {
    String[][] urls = null;

    configureByFiles(null,BASE_PATH + getTestName(false) + ".xml", BASE_PATH + "mylib.jar");
    String path = myFile.getVirtualFile().getParent().getPath() + "/";
    urls = new String[][] {
      {"http://graphml.graphdrawing.org/xmlns",path + "mylib.jar!/graphml.xsd"},
      {"http://graphml.graphdrawing.org/xmlns/1.0/graphml-structure.xsd",path + "mylib.jar!/graphml-structure.xsd"},
      {"http://www.w3.org/1999/xlink",path + "mylib.jar!/xlink.xsd"}
    };

    for(String[] s:urls) ExternalResourceManagerExImpl.registerResourceTemporarily(s[0], s[1], getTestRootDisposable());
    doDoTest(true, false);
  }

  public void testChangeRootElement() throws Exception {
    enableInspectionTool(new XmlWrongRootElementInspection());
    final String testName = getTestName(false);
    configureByFile(BASE_PATH + testName + ".xml");
    Collection<HighlightInfo> infos = doDoTest(true, false);

    findAndInvokeIntentionAction(infos, "Change root tag name to xxx", myEditor, myFile);
    checkResultByFile(BASE_PATH + testName + "_after.xml");
  }

  public void testUnqualifiedAttributePsi() throws Exception {
    doTestWithLocations(null, "xml");
    final List<XmlAttribute> attrs = new ArrayList<>(2);

    myFile.acceptChildren(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(final XmlAttribute attribute) {
        if (!attribute.isNamespaceDeclaration()) attrs.add(attribute);
      }
    });

    assertEquals(4,attrs.size());
    checkAttribute(attrs.get(0), "");
    checkAttribute(attrs.get(1), "foo2");
    checkAttribute(attrs.get(2), "");
    checkAttribute(attrs.get(3), "foo4");
  }

  private static void checkAttribute(final XmlAttribute xmlAttribute, String expectedNs) {
    final String attrNs = xmlAttribute.getNamespace();
    assertEquals(expectedNs, attrNs);
    final XmlTag parent = xmlAttribute.getParent();

    assertEquals(parent.getAttribute(xmlAttribute.getLocalName(), attrNs), xmlAttribute);
    //final String parentNs = parent.getNamespace();
    //if (!parentNs.equals(attrNs)) assertNull(parent.getAttribute(xmlAttribute.getLocalName(), parentNs));
    assertEquals(parent.getAttributeValue(xmlAttribute.getLocalName(), attrNs), xmlAttribute.getValue());
    //if (!parentNs.equals(attrNs)) assertNull(parent.getAttributeValue(xmlAttribute.getLocalName(), parentNs));
  }

  @HighlightingFlags({HighlightingFlag.SkipExternalValidation})
  public void testHighlightWhenNoNsSchemaLocation() throws Exception {
    final String testName = getTestName(false);

    doTest(
      new VirtualFile[]{
        getVirtualFile(BASE_PATH + testName + ".xml"),
        getVirtualFile(BASE_PATH + testName + ".xsd")
      },
      true,
      false
    );
  }

  public void testSchemaAutodetection() throws Exception {
    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "SchemaAutodetection/policy.xml"),
        getVirtualFile(BASE_PATH + "SchemaAutodetection/cs-xacml-schema-policy-01.xsd"),
        getVirtualFile(BASE_PATH + "SchemaAutodetection/cs-xacml-schema-context-01.xsd")
      },
      true,
      false
    );
  }

  public void testDtdAutodetection() throws Exception {
    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "nuancevoicexml-2-0.xml"),
        getVirtualFile(BASE_PATH + "nuancevoicexml-2-0.dtd")
      },
      true,
      false
    );
  }

  public void testSchemaWithXmlId() throws Exception {
    final String testName = getTestName(false);

    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + testName + ".xml")
      },
      true,
      false
    );
  }

  public void testProblemWithImportedNsReference() throws Exception {
    doTestWithLocations(null, "xsd");
  }

  public void testBadXmlns() throws Exception {
    configureByFile(BASE_PATH + "badXmlns.xml");
    doHighlighting();
  }

  public void testProblemWithMemberTypes() throws Exception {
    doTestWithLocations(null, "xsd");
  }

  public void testDtdHighlighting() throws Exception {
    final String testName = getTestName(false);

    doTestWithLocations(
      new String[][] {
        {"http://www.w3.org/TR/xhtml-modularization/DTD/xhtml-inlstyle-1.mod",testName + ".mod"}
      },
      "dtd"
    );
  }

  public void testNoNamespaceLocation() throws Exception {
    final String testName = getTestName(false);

    doTest(
      new VirtualFile[]{
        getVirtualFile(BASE_PATH + testName + ".xml"),
        getVirtualFile(BASE_PATH + testName + ".xsd")
      },
      true,
      false
    );
  }

  public void testUnresolvedSymbolForForAttribute() throws Exception {
    doTest();
  }

  public void testXsiType() throws Exception {
    final String testName = getTestName(false);

    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + testName + ".xml"),
        getVirtualFile(BASE_PATH + testName + "_TypesV1.00.xsd"),
        getVirtualFile(BASE_PATH + testName + "_ActivationRequestV1.00.xsd"),
        getVirtualFile(BASE_PATH + testName + "_GenericActivationRequestV1.00.xsd")
      },
      true,
      false
    );
  }

  public void testMappedSchemaLocation() throws Exception {
    doTestWithLocations(new String[][]{
      {"schemas/Forms.xsd", "Forms.xsd"}
    }, "xml");
  }

  public void testMuleConfigValidation() throws Exception {
    doSchemaTestWithManyFilesFromSeparateDir(
      new String[][]{
        {"http://www.springframework.org/schema/tool", "spring-tool-2.5.xsd"},
        {"http://www.springframework.org/schema/beans/spring-beans-2.5.xsd", "spring-beans-2.5.xsd"},
        {"http://www.mulesource.org/schema/mule/core/2.2/mule.xsd", "mule.xsd"},
        {"http://www.mulesource.org/schema/mule/stdio/2.2/mule-stdio.xsd", "mule-stdio.xsd"},
        {"http://www.springframework.org/schema/context/spring-context-2.5.xsd", "spring-context-2.5.xsd"},
        {"http://www.mulesource.org/schema/mule/schemadoc/2.2/mule-schemadoc.xsd", "mule-schemadoc.xsd"},
      },
      null
    );
  }

  public void testMobileHtml() throws Exception {
    enableInspectionTool(new HtmlUnknownTagInspection());
    doTest(getFullRelativeTestName(".html"), true, true);
  }

  public void testAnyAttribute() throws Exception {
    configureByFiles(null, BASE_PATH + "anyAttribute.xml", BASE_PATH + "services-1.0.xsd");
    doDoTest(true, false);
  }

  public void testSubstitution() throws Exception {
    doTest(new VirtualFile[]{
      getVirtualFile(BASE_PATH + "Substitute/test.xml"),
      getVirtualFile(BASE_PATH + "Substitute/schema-b.xsd"),
      getVirtualFile(BASE_PATH + "Substitute/schema-a.xsd")
    }, true, false);
  }

  public void testPrefixedSubstitution() throws Exception {
    doTest(new VirtualFile[]{
      getVirtualFile(BASE_PATH + "Substitute/prefixed.xml"),
      getVirtualFile(BASE_PATH + "Substitute/schema-b.xsd"),
      getVirtualFile(BASE_PATH + "Substitute/schema-a.xsd")
    }, true, false);
  }

  public void testDtdWithXsd() throws Exception {
    doTest(
      new VirtualFile[] {
        getVirtualFile(BASE_PATH + "DtdWithXsd/help.xml"),
        getVirtualFile(BASE_PATH + "DtdWithXsd/helptopic.xsd"),
        getVirtualFile(BASE_PATH + "DtdWithXsd/html-entities.dtd")
      },
      true,
      false
    );
  }

  public void testAnyAttributeNavigation() throws Exception {
    configureByFiles(null, getVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xml"),
                     getVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xsd"),
                     getVirtualFile(BASE_PATH + "AnyAttributeNavigation/library.xsd"));

    PsiReference at = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());

    XmlTag tag = PsiTreeUtil.getParentOfType(at.getElement(), XmlTag.class);
    XmlElementDescriptorImpl descriptor = (XmlElementDescriptorImpl)tag.getDescriptor();
    XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
    System.out.println(Arrays.asList(descriptors));

    doDoTest(true, false);

    PsiElement resolve = at.resolve();
    assertTrue(resolve instanceof XmlTag);
  }

  public void testDropAnyAttributeCacheOnExitFromDumbMode() throws Exception {
    try {
      DumbServiceImpl.getInstance(myProject).setDumb(true);
      configureByFiles(null, getVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xml"),
                       getVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xsd"),
                       getVirtualFile(BASE_PATH + "AnyAttributeNavigation/library.xsd"));
      PsiReference at = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());

      XmlTag tag = PsiTreeUtil.getParentOfType(at.getElement(), XmlTag.class);
      XmlElementDescriptor descriptor = tag.getDescriptor();
      XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
      System.out.println(Arrays.asList(descriptors));
    }
    finally {
      DumbServiceImpl.getInstance(myProject).setDumb(false);
    }

    doDoTest(true, false);
  }

  public void testQualifiedAttributeReference() throws Exception {
    configureByFiles(null, BASE_PATH + "qualified.xml", BASE_PATH + "qualified.xsd");
    doDoTest(true, false);
  }

  public void testEnumeratedBoolean() throws Exception {
    configureByFiles(null, BASE_PATH + "EnumeratedBoolean.xml", BASE_PATH + "EnumeratedBoolean.xsd");
    doDoTest(true, false);
  }

  public void testStackOverflowInSchema() throws Exception {
    configureByFiles(null, BASE_PATH + "XMLSchema_1_1.xsd");
    doHighlighting();
  }

  public void testSchemaVersioning() throws Exception {
    configureByFiles(null, BASE_PATH + "Versioning.xsd");
    doDoTest(true, false);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (!getTestName(false).equals("DoctypeWithoutSchema2")) enableInspectionTool(new CheckXmlFileWithXercesValidatorInspection());

    old = XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS;
    XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS = false;
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://www.springframework.org/schema/beans/spring-beans-2.5.xsd",
                                                              getTestDataPath() + BASE_PATH + "spring-beans-2.5.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",
                                                              getTestDataPath() + BASE_PATH + "web-app_2_4.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",
                                                              getTestDataPath() + BASE_PATH + "web-app_2_4.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/dtd/web-app_2_3.dtd",
                                                              getTestDataPath() + BASE_PATH + "web-app_2_3.dtd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://struts.apache.org/dtds/struts-config_1_2.dtd",
                                                              getTestDataPath() + BASE_PATH + "struts-config_1_2.dtd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/dtd/ejb-jar_2_0.dtd",
                                                              getTestDataPath() + BASE_PATH + "ejb-jar_2_0.dtd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/",
                                                              getTestDataPath() + BASE_PATH + "wsdl11.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/soap/",
                                                              getTestDataPath() + BASE_PATH + "wsdl11_soapbinding.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://schemas.xmlsoap.org/soap/encoding/",
                                                              getTestDataPath() + BASE_PATH + "soap-encoding.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/application-client_1_4.xsd",
                                                              getTestDataPath() + BASE_PATH + "application-client_1_4.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd",
                                                              getTestDataPath() + BASE_PATH + "hibernate-mapping-3.0.dtd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://maven.apache.org/maven-v4_0_0.xsd",
                                                              getTestDataPath() + BASE_PATH + "maven-4.0.0.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd",
                                                              getTestDataPath() + BASE_PATH + "web-jsptaglibrary_1_2.dtd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/JSP/Page",
                                                              getTestDataPath() + BASE_PATH + "jsp_2_0.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-jsptaglibrary_2_0.xsd",
                                                              getTestDataPath() + BASE_PATH + "web-jsptaglibrary_2_0.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://hibernate.sourceforge.net/hibernate-configuration-3.0.dtd",
                                                              getTestDataPath() + BASE_PATH + "hibernate-configuration-3.0.dtd",
                                                              getTestRootDisposable());
  }

  private void disableHtmlSupport() {
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype("fake", getProject());
  }

  private void enableHtmlSupport() {
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, getProject());
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  @Override
  protected void tearDown() throws Exception {
    XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS = old;
    super.tearDown();
  }

  public void testLinksInAttrValuesAndComments() throws Exception {
    configureByFile(BASE_PATH +getTestName(false) + ".xml");
    doDoTest(true, false);

    List<WebReference> list = PlatformTestUtil.collectWebReferences(myFile);
    assertEquals(2, list.size());

    Collections.sort(list, (o1, o2) -> o1.getCanonicalText().length() - o2.getCanonicalText().length());

    assertEquals("https://www.jetbrains.com/ruby/download", list.get(0).getCanonicalText());
    assertTrue(list.get(0).getElement() instanceof  XmlAttributeValue);
    assertEquals("http://blog.jetbrains.com/ruby/2012/04/rubymine-4-0-3-update-is-available/", list.get(1).getCanonicalText());
    assertTrue(list.get(1).getElement() instanceof  XmlComment);
  }
}