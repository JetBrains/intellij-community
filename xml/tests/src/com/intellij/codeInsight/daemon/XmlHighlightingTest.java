// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.application.options.XmlSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.XmlDefaultAttributeValueInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.*;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.*;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.javaee.UriUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.ant.dom.AntResolveInspection;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.actions.validate.ValidateXmlActionHandler;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.*;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.model.psi.PsiSymbolReference.getReferenceText;

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
  public void testXmlStylesheet() throws Exception { doTest(); }
  public void testCommentBeforeProlog_2() throws Exception { doTest(); }
  //public void testNoRootTag() throws Exception { doTest(); }

  public void testduplicateAttribute() throws Exception { doTest(); }

  public void testduplicateAttribute2() {
    configureByFiles(null, BASE_PATH + getTestName(false) + ".xml", BASE_PATH + getTestName(false) + ".xsd");

    final String url = "http://www.foo.org/schema";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, getTestName(false) + ".xsd", getTestRootDisposable());
    final String url2 = "http://www.bar.org/foo";
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

  // TODO: external validator should not be launched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testEntityRefWithEmptyDtd() throws Exception { doTest(); }
  public void testEmptyNSRef() throws Exception { doTest(); }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testDoctypeWithoutSchema() throws Exception {
    final String baseName = BASE_PATH + getTestName(false);

    configureByFiles(null, findVirtualFile(baseName + ".xml"), findVirtualFile(baseName + ".ent"));
    doDoTest(true,false);
    myFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
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

  public void testSvg20() throws Exception {
    InspectionsKt.enableInspectionTools(getProject(), getTestRootDisposable(), new XmlDefaultAttributeValueInspection());
    doTest(getFullRelativeTestName(".svg"), true, false);
  }

  public void testNavigateToDeclDefinedWithEntity() throws Exception {
    final String baseName = BASE_PATH + getTestName(false);

    configureByFiles(null, findVirtualFile(baseName + ".xml"), findVirtualFile(baseName + ".dtd"), findVirtualFile(baseName + ".ent"));
    doDoTest(true,false);
    final List<PsiReference> refs = new ArrayList<>();
    myFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(final @NotNull XmlAttribute attribute) {
        refs.add(attribute.getReference());
      }

      @Override
      public void visitXmlTag(final @NotNull XmlTag tag) {
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

    configureByFiles(null, findVirtualFile(baseName + ".xml"), findVirtualFile(baseName + ".ent"));
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
    configureByFiles(null, findVirtualFile(basePath + ".xml"), findVirtualFile(basePath + ".xsd"), findVirtualFile(basePath + "_2.xsd"));
    doDoTest(true,false);

  }

  public void testComplexSchemaValidation9() throws IOException {
    final String basePath = BASE_PATH + getTestName(false);

    configureByFiles(null, findVirtualFile(basePath + ".xml"), findVirtualFile(basePath + ".xsd"));
    doDoTest(true,false);
  }

  public void testComplexSchemaValidation10() throws IOException {
    final String testName = getTestName(false);

    final String basePath = BASE_PATH + testName;
    configureByFiles(null, findVirtualFile(basePath + ".xsd"), findVirtualFile(basePath + "_2.xsd"));
    doDoTest(true,false);

    final List<PsiReference> refs = new ArrayList<>(2);

    myFile.acceptChildren(new XmlRecursiveElementVisitor() {

      @Override public void visitXmlTag(@NotNull XmlTag tag) {
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

  private static void addRefsInPresent(final XmlTag tag, final String name, final List<? super PsiReference> refs) {
    if (tag.getAttributeValue(name) != null) {
      ContainerUtil.addAll(refs, tag.getAttribute(name, null).getValueElement().getReferences());
    }
  }

  public void testComplexSchemaValidation11() {
    final String testName = getTestName(false);
    doTestWithLocations(
      new String[][] {
        {"http://www.springframework.org/schema/beans",testName + ".xsd"},
        {"http://www.springframework.org/schema/util",testName + "_2.xsd"}
      },
      "xml"
    );
  }

  private void doTestWithLocations(String[] @Nullable [] resources, String ext) {
    doConfigureWithLocations(resources, ext);
    doDoTest(true,false);
  }

  private void doConfigureWithLocations(final String[][] resources, final String ext) {

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

  public void testXercesCachingProblem() {
    final String[][] resources = {{"http://www.test.com/test", getTestName(false) + ".dtd"}};
    doConfigureWithLocations(resources, "xml");

    doDoTest(true,true);
    WriteCommandAction.runWriteCommandAction(null, () -> myEditor.getDocument().insertString(myEditor.getDocument().getCharsSequence().toString().indexOf("?>") + 2, "\n"));

    doDoTest(true,true);
  }

  public void testComplexSchemaValidation13() {
    doTestWithLocations(new String[][] { {"urn:test", getTestName(false) + "_2.xsd"} }, "xsd");
  }

  public void testComplexSchemaValidation14() {
    doTestWithLocations(new String[][]{{"parent", getTestName(false) + ".xsd"}}, "xml");
  }

  public void testComplexSchemaValidation14_2() throws Exception {
    doTest(getFullRelativeTestName(".xsd"), true, false);
  }

  public void testComplexSchemaValidation15() {
    doTestWithLocations(
      new String[][] {
        {"http://www.linkedin.com/lispring", getTestName(false) + ".xsd"},
        {"http://www.springframework.org/schema/beans", getTestName(false) + "_2.xsd"}
      },
      "xml"
    );
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testComplexSchemaValidation16() {
    doTestWithLocations(
      new String[][] {
        {"http://www.inversoft.com/schemas/savant-2.0/project", getTestName(false) + ".xsd"},
        {"http://www.inversoft.com/schemas/savant-2.0/base", getTestName(false) + "_2.xsd"}
      },
      "xml"
    );
  }

  public void testComplexSchemaValidation17() {
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
      configureByFiles(null, findVirtualFile(BASE_PATH + "xhtml1-transitional.xsd"), findVirtualFile(BASE_PATH + "xhtml-special.ent"),
                       findVirtualFile(BASE_PATH + "xhtml-symbol.ent"), findVirtualFile(BASE_PATH + "xhtml-lat1.ent"));
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
    configureByFiles(null, findVirtualFile(BASE_PATH + getTestName(false) + ".xml"),
                     findVirtualFile(BASE_PATH + getTestName(false) + ".xsd"));
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

    configureByFiles(null, findVirtualFile(BASE_PATH + getTestName(false) + ".xml"), findVirtualFile(BASE_PATH + location),
                     findVirtualFile(BASE_PATH + location2));
    doDoTest(true,false);

  }

  public void testSchemaValidation4() throws Exception {
    String schemaLocation = getTestName(false)+".xsd";

    configureByFiles(null, findVirtualFile(BASE_PATH + getTestName(false) + ".xml"), findVirtualFile(BASE_PATH + schemaLocation));

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
      @Override public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        final PsiElement parent = value.getParent();
        if (!(parent instanceof XmlAttribute)) return;
        final String name = ((XmlAttribute)parent).getName();
        if ("type".equals(name) || "base".equals(name) || "ref".equals(name)) {
          myTypeOrElementRefs.add(value.getReferences()[0]);
        }
      }

      @Override public void visitXmlTag(@NotNull XmlTag tag) {
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
    List<String> list = new ArrayList<>();
    list.add("http://xml.apache.org/axis/wsdd2/");
    list.add("http://xml.apache.org/axis/wsdd2/providers/java");
    list.add("http://soapinterop.org/xsd2");
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(list, getTestRootDisposable());

    doTest(getFullRelativeTestName(".xml"), true, false);
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testExternalValidatorOnValidXmlWithNamespacesNotSetup2() throws Exception {
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList(""), getTestRootDisposable());
    doTest(getFullRelativeTestName(".xml"), true, false);
  }

  public void testXercesMessagesBinding() throws Exception {
    doTest(getFullRelativeTestName(".xml"), true, false);
  }

  public void testXercesMessagesBinding3() throws Exception {
    doTest(getFullRelativeTestName(".xml"), true, false);
  }

  public void testXercesMessagesBinding4() {
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
        findVirtualFile(getFullRelativeTestName(".xml")),
        findVirtualFile(getFullRelativeTestName(".xsd"))
      },
      true,
      false
    );

    final String url2 = getTestName(false) + "_2.xsd";

    ExternalResourceManagerExImpl.registerResourceTemporarily(url2, url2, getTestRootDisposable());

    doTest(
      new VirtualFile[] {
        findVirtualFile(getFullRelativeTestName("_2.xml")),
        findVirtualFile(getFullRelativeTestName("_2.xsd"))
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
          public void visitXmlAttribute(final @NotNull XmlAttribute attribute) {
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
    doTest(new VirtualFile[]{findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location)}, false, false);
  }

  public void testStackOverflow2() throws Exception {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    final String url = "urn:aaa";
    final String location = getTestName(false) + ".xsd";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    doTest(getFullRelativeTestName(".xsd"), false, false);

  }

  public void testComplexSchemaValidation() throws Exception {
    doTest(getFullRelativeTestName(), false, false);
  }

  public void testComplexDtdValidation() throws Exception {
    String location = "Tapestry_3_0.dtd";
    String url = "http://jakarta.apache.org/tapestry/dtd/Tapestry_3_0.dtd";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());

    myTestJustJaxpValidation = true;
    doTest(new VirtualFile[] {findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location)}, false, false);
    myTestJustJaxpValidation = false;
  }

  public void testComplexDtdValidation2() throws Exception {
    String location = getTestName(false)+".dtd";
    ExternalResourceManagerExImpl.registerResourceTemporarily(location, location, getTestRootDisposable());

    myTestJustJaxpValidation = true;
    doTest(new VirtualFile[] {findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location)}, false, false);
    myTestJustJaxpValidation = false;
  }

  public void testComplexSchemaValidation2() throws Exception {
    doTest(new VirtualFile[] {findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + "jdo_2_0.xsd")}, false, false);
  }

  public void testComplexSchemaValidation3() throws Exception {
    List<VirtualFile> files = new ArrayList<>();
    files.add(findVirtualFile(getFullRelativeTestName()));

    final VirtualFile virtualFile = findVirtualFile(BASE_PATH + "ComplexSchemaValidation3Schemas");
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
    doTest(new VirtualFile[] {findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location)}, false, false);

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
    doTest(new VirtualFile[]{findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location)}, true, false);
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
          files.set(0, findVirtualFile(BASE_PATH + getTestName(false) + "_2.xml"));
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
        findVirtualFile(getFullRelativeTestName()),
        findVirtualFile(getFullRelativeTestName(".xsd"))
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
    doTest(new VirtualFile[]{findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location)}, false, false);
  }

  public void testMavenValidation() throws Exception {
    doTest(getFullRelativeTestName(), false, false);
  }

  public void testResolveEntityUrl() throws Throwable {
    doTest(new VirtualFile[] {
      findVirtualFile(getFullRelativeTestName()),
      findVirtualFile(BASE_PATH + "entities.dtd")
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
        findVirtualFile(getFullRelativeTestName()),
        findVirtualFile(BASE_PATH + getTestName(false) + "_1.xsd"),
        findVirtualFile(BASE_PATH + getTestName(false) + "_2.xsd"),
        findVirtualFile(BASE_PATH + getTestName(false) + "_3.xsd"),
      },
      false,
      false
    );

    XmlTag rootTag = ((XmlFile)myFile).getDocument().getRootTag();
    checkOneTagForSchemaAttribute(rootTag, "xmlns:test2", getTestName(false) + "_2.xsd");

    configureByFiles(null, findVirtualFile(BASE_PATH + getTestName(false) + "_2.xsd"),
                     findVirtualFile(BASE_PATH + getTestName(false) + "_3.xsd"));

    rootTag = ((XmlFile)myFile).getDocument().getRootTag();
    final List<XmlTag> tags = new ArrayList<>();

    XmlUtil.processXmlElements(
      rootTag,
      element -> {
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

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
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
      element -> {
        if (element instanceof XmlTag &&
            ((XmlTag)element).getName().equals("xs:include")
           ) {
          tags.add((XmlTag)element);
        }
        return true;
      },
      true
    );

    assertEquals("Should be three tags", 3, tags.size());
    String location = "xslt-1_0.xsd";
    checkOneTagForSchemaAttribute(tags.get(2), "schemaLocation", location);
  }

  public void testResolvingEntitiesInDtd() throws Exception {
    configureByFiles(null, findVirtualFile(BASE_PATH + getTestName(false) + ".xml"),
                     findVirtualFile(BASE_PATH + getTestName(false) + "/RatingandServiceSelectionRequest.dtd"),
                     findVirtualFile(BASE_PATH + getTestName(false) + "/XpciInterchange.dtd"),
                     findVirtualFile(BASE_PATH + getTestName(false) + "/Xpcivocabulary.dtd"));
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
      element -> {
        if (element instanceof XmlEntityRef) {
          refs.add((XmlEntityRef)element);
        }
        return true;
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
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList("http://ignored/uri"), getTestRootDisposable());
    doTest();
  }

  public void testNonEnumeratedValuesHighlighting() throws Exception {
    final String url = "http://www.w3.org/1999/XSL/Format";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, "fop.xsd", getTestRootDisposable());
    configureByFiles(null, findVirtualFile(BASE_PATH + getTestName(false) + ".xml"), findVirtualFile(BASE_PATH + "fop.xsd"));
    doDoTest(true, false);
  }

  public void testEditorHighlighting() {
    //                       10
    //             0123456789012
    String text = "<html></html>";
    EditorHighlighter xmlHighlighter = XmlHighlighterFactory.createXMLHighlighter(EditorColorsManager.getInstance().getGlobalScheme());
    xmlHighlighter.setText(text);
    HighlighterIterator iterator = xmlHighlighter.createIterator(1);
    assertSame("Xml tag name", XmlTokenType.XML_TAG_NAME, iterator.getTokenType());
    iterator = xmlHighlighter.createIterator(8);
    assertSame("Xml tag name at end of tag", XmlTokenType.XML_TAG_NAME, iterator.getTokenType());

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
    assertSame("Xml attribute value", XmlTokenType.XML_DATA_CHARACTERS, iterator.getTokenType());
    assertEquals(41, iterator.getStart());
    assertEquals(70, iterator.getEnd());

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

    configureByFiles(null, findVirtualFile(baseName + ".xml"), findVirtualFile(baseName + ".dtd"), findVirtualFile(baseName + "2.dtd"));
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

  public void testDoctypeSystemConfigured() {
    ExternalResourceManagerExImpl.registerResourceTemporarily("sample.dtd",
                                                              getTestDataPath() + BASE_PATH + "sample.dtd",
                                                              getTestRootDisposable());
    configureByFiles(null, BASE_PATH + "sample.xml", BASE_PATH + "sample.dtd");
    doDoTest(true, false);
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
    final String actionName = XmlBundle.message("xml.intention.add.xsi.schema.location.for.external.resource");
    doTestWithQuickFix(BASE_PATH + testName, actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "2", actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "3", actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "4", actionName, false);
  }

  public void testHighlightingWithConditionalSectionsInDtd() throws Exception {
    final String testName = getTestName(false);

    configureByFiles(null, findVirtualFile(BASE_PATH + testName + ".xml"), findVirtualFile(BASE_PATH + testName + ".dtd"));
    doDoTest(true, false);

    configureByFiles(null, findVirtualFile(BASE_PATH + testName + "2.xml"), findVirtualFile(BASE_PATH + testName + ".dtd"));
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

  public void testBigPrologHighlightingPerformance() {
    MadTestingUtil.enableAllInspections(myProject, XMLLanguage.INSTANCE);
    configureByText(XmlFileType.INSTANCE,
                    "<!DOCTYPE rules [\n" +
                    IntStream.range(0, 10000).mapToObj(i -> "<!ENTITY pnct" + i + " \"x\">\n").collect(Collectors.joining()) +
                    "]>\n" +
                    "<rules/>");
    PlatformTestUtil
      .startPerformanceTest("highlighting", 4_500, () -> doHighlighting())
      .setup(() -> getPsiManager().dropPsiCaches())
      .usesAllCPUCores()
      .assertTiming();
  }

  public void testDocBookHighlighting2() throws Exception {
    doManyFilesFromSeparateDirTest("http://www.oasis-open.org/docbook/xml/4.5/docbookx.dtd", "docbookx.dtd", null);
  }

  public void testOasisDocumentHighlighting() throws Exception {
    doManyFilesFromSeparateDirTest("concept.dtd", "concept.dtd", null);
  }

  private void doManyFilesFromSeparateDirTest(final String url, final String mainDtdName, @Nullable Runnable additionalTestAction) throws Exception {
    List<VirtualFile> files = new ArrayList<>();
    files.add(findVirtualFile(getFullRelativeTestName()));

    final VirtualFile virtualFile = findVirtualFile(BASE_PATH + getTestName(false));
    ContainerUtil.addAll(files, virtualFile.getChildren());

    ExternalResourceManagerExImpl
      .registerResourceTemporarily(url, UriUtil.findRelativeFile(mainDtdName, virtualFile).getPath(), getTestRootDisposable());
    doTest(VfsUtilCore.toVirtualFileArray(files), true, false);

    if (additionalTestAction != null) additionalTestAction.run();

  }

  private void doSchemaTestWithManyFilesFromSeparateDir(final String[][] urls, @Nullable Processor<? super List<VirtualFile>> additionalTestingProcessor) throws Exception {
    List<VirtualFile> files = new ArrayList<>(6);
    files.add(findVirtualFile(BASE_PATH + getTestName(false) + ".xml"));

    final Set<VirtualFile> usedFiles = new HashSet<>();
    final String base = BASE_PATH + getTestName(false) + "Schemas/";

    for(String[] pair:urls) {
      final String url = pair[0];
      final String filename = pair.length > 1 ? pair[1]:url.substring(url.lastIndexOf('/')+1) + (url.endsWith(".xsd")?"":".xsd");

      final VirtualFile virtualFile = findVirtualFile(base + filename);
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

  enum HighlightingFlag {
    SkipExternalValidation
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface HighlightingFlags {
    HighlightingFlag[] value() default {};
  }

  private static boolean methodOfTestHasAnnotation(final Class testClass, final String testName, final HighlightingFlag flag) {
    Method method;
    try {
      method = testClass.getMethod("test" + testName);
    }
    catch (Exception e) {
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
    configureByFiles(null, findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location));

    doDoTest(true,false);

    Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    final Editor schemaEditor = allEditors[0] == myEditor ? allEditors[1]:allEditors[0];
    final String text = schemaEditor.getDocument().getText();
    final String newText = text.replaceAll("xsd", "xs");
    WriteCommandAction.runWriteCommandAction(null, () -> schemaEditor.getDocument().replaceString(0, text.length(), newText));

    doDoTest(true, false);
  }

  public void testXHtmlEditorHighlighting() {
    //                       10
    //             0123456789012
    String text = "<html></html>";
    EditorHighlighter xhtmlHighlighter = HighlighterFactory
      .createHighlighter(XHtmlFileType.INSTANCE, EditorColorsManager.getInstance().getGlobalScheme(), myProject);
    xhtmlHighlighter.setText(text);
    HighlighterIterator iterator = xhtmlHighlighter.createIterator(1);
    assertSame("Xml tag name", XmlTokenType.XML_TAG_NAME, iterator.getTokenType());
    iterator = xhtmlHighlighter.createIterator(8);
    assertSame("Xml tag name at end of tag", XmlTokenType.XML_TAG_NAME, iterator.getTokenType());

  }

  public void testSchemaValidation6() throws Exception {
    String location = getTestName(false)+".xsd";
    String url = "aaa";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());
    configureByFiles(null, findVirtualFile(getFullRelativeTestName()), findVirtualFile(BASE_PATH + location));

    doDoTest(true, false);

  }

  public void testDTDEditorHighlighting() {
    //                       10        20
    //             012345678901234567890 123456 789
    String text = "<!ENTITY % Charsets \"CDATA\">";
    EditorHighlighter dtdHighlighter = HighlighterFactory.createHighlighter(DTDFileType.INSTANCE, EditorColorsManager.getInstance().getGlobalScheme(), myProject);
    dtdHighlighter.setText(text);
    HighlighterIterator iterator = dtdHighlighter.createIterator(3);

    assertSame("Xml entity name", XmlTokenType.XML_ENTITY_DECL_START, iterator.getTokenType());
    iterator = dtdHighlighter.createIterator(13);
    assertSame("Xml name in dtd", XmlTokenType.XML_NAME, iterator.getTokenType());
    iterator = dtdHighlighter.createIterator(23);
    assertSame("Xml attribute value in dtd", XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType());

    //                10        20        30        40
    //      0123456789012345678901 2345678901234567890123456789
    text = "<!ELEMENT base EMPTY>\n<!ATTLIST base id ID #IMPLIED>";
    dtdHighlighter.setText(text);
    iterator = dtdHighlighter.createIterator(3);
    assertSame("Xml element name", XmlTokenType.XML_ELEMENT_DECL_START, iterator.getTokenType());
    iterator = dtdHighlighter.createIterator(25);
    assertSame("Xml attr list", XmlTokenType.XML_ATTLIST_DECL_START, iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(14);
    assertSame("Xml attr list", TokenType.WHITE_SPACE, iterator.getTokenType());

    iterator = dtdHighlighter.createIterator(21);
    assertSame("Xml attr list", TokenType.WHITE_SPACE, iterator.getTokenType());

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

  public void testImportProblems() {
    final String testName = getTestName(false);
    configureByFiles(
      null,
      BASE_PATH + testName +".xsd",
      BASE_PATH +testName +"2.xsd"
    );
    doDoTest(true, false, true);
  }

  public void testEncoding() throws Exception { doTest(true); }

  public void testSchemaImportHighlightingAndResolve() {
    doTestWithLocations(new String[][]{{"http://www.springframework.org/schema/beans", "ComplexSchemaValidation11.xsd"}}, "xsd");
  }

  public void testDocBookV5() {
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

  public void testDocBook5() {
    doTestWithLocations(
      new String[][] {
        {"http://docbook.org/ns/docbook", "DocBookV5.xsd"},
        {"http://www.w3.org/1999/xlink", "xlink.xsd"},
        {"http://www.w3.org/XML/1998/namespace", "xml.xsd"}
      },
      "xml"
    );
  }

  public void testDocBookRole() {
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
    highlightInfos.removeIf(highlightInfo -> highlightInfo.getSeverity() == HighlightSeverity.INFORMATION);
    return highlightInfos;
  }

  public void testUsingSchemaDtd() {
    doTestWithLocations(null, "xsd");
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testDtdElementRefs() {
    doTestWithLocations(
      new String[] [] {
        {"http://example.com/persistence","XIncludeTestSchema.xsd"}
      },
      "xml"
    );
  }

  public void testDuplicates() {
    doTestWithLocations(null, "xsd");
    doTestWithLocations(null, "dtd");
    doTestWithLocations(null, "xml");
  }

  public void testMinMaxOccursInSchema() {
    doTestWithLocations(null, "xsd");
  }

  public void testXslt2() {
    doTestWithLocations(null, "xsl");
  }

  public void testDefaultAndFixedInSchema() {
    doTestWithLocations(null, "xsd");
  }

  public void testAnyAttributesInAttrGroup() {
    doTestWithLocations(
      new String[] [] {
        {"http://www.foo.org/test",getTestName(false)+".xsd"},
        {"http://www.bar.org/test",getTestName(false)+"_2.xsd"}
      },
      "xml"
    );
  }

  public void testXInclude() {
    final String testName = getTestName(false);
    configureByFiles(
      null,
      BASE_PATH + testName +".xml",
      BASE_PATH +testName +"-inc.xml",
      BASE_PATH +testName +"TestSchema.xsd"
    );

    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList("oxf:/apps/somefile.xml"), getTestRootDisposable());
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

  public void testMuleConfigHighlighting() {
    final String[][] urls = {
      {"http://www.springframework.org/schema/beans/spring-beans-2.0.xsd", "spring-beans-2.0.xsd"},
      {"http://www.springframework.org/schema/tool", "spring-tool-2.5.xsd"},
      {"http://www.springframework.org/schema/context/spring-context-2.5.xsd", "spring-context-2.5.xsd"},
      {"urn:xxx","mule.xsd"},
      {"urn:yyy","mule-management.xsd"}
    };
    doTestWithLocations(urls, "xml");
  }

  public void testMuleConfigHighlighting2() {
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

    VirtualFile[] files = {findVirtualFile(BASE_PATH + testName + "_2.xsd"), findVirtualFile(BASE_PATH + testName + "_3.xsd")};
    doTest(files, true, false);

    files = new VirtualFile[] {findVirtualFile(BASE_PATH + testName + ".xsd"),
      findVirtualFile(BASE_PATH + testName + "_2.xsd"), findVirtualFile(BASE_PATH + testName + "_3.xsd")};
    doTest(files, true, false);
  }

  public void testComplexRedefine4() throws Exception {
    final String testName = getTestName(false);
    VirtualFile[] files = {findVirtualFile(BASE_PATH + testName + ".xml"),
      findVirtualFile(BASE_PATH + testName + ".xsd"), findVirtualFile(BASE_PATH + testName + "_2.xsd")};
    doTest(files, true, false);
  }

  public void testComplexRedefine5() {
    final String testName = getTestName(false);
    String[][] urls = {
      {"http://extended", testName + ".xsd"},
      {"http://simple", testName + "_2.xsd"}
    };
    doTestWithLocations(urls, "xml");
  }

  public void testComplexRedefine6() {
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
      findVirtualFile(BASE_PATH + testName + ".xml"),
      findVirtualFile(BASE_PATH + testName + ".xsd"),
      findVirtualFile(BASE_PATH + testName + "_2.xsd")
    };
    doTest(files, true, false);
  }

  public void testRedefineBaseType() throws Exception {
    final String testName = getTestName(false);
    VirtualFile[] files = {
      findVirtualFile(BASE_PATH + testName + ".xml"),
      findVirtualFile(BASE_PATH + testName + ".xsd"),
      findVirtualFile(BASE_PATH + testName + "_2.xsd")
    };
    doTest(files, true, false);
  }

  public void testComplexSchemaValidation18() throws Exception {
    final String testName = getTestName(false);
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + testName + ".xml"),
        findVirtualFile(BASE_PATH + testName + ".xsd"),
        findVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  public void testComplexSchemaValidation19() throws Exception {
    final String testName = getTestName(false);
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + testName + ".xml"),
        findVirtualFile(BASE_PATH + testName + ".xsd"),
        findVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  public void testComplexSchemaValidation20() throws Exception {
    final String testName = getTestName(false);
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + testName + ".xml"),
        findVirtualFile(BASE_PATH + testName + ".xsd"),
        findVirtualFile(BASE_PATH + testName + "_2.xsd")
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
        findVirtualFile(BASE_PATH + "FinancialReportProcess.bpmn20.xml"),
        findVirtualFile(BASE_PATH + "BPMN20.xsd"),
        findVirtualFile(BASE_PATH + "Semantic.xsd")
      },
      true,
      false
    );
  }

  public void testComplexRedefineFromJar() {
    configureByFiles(null,BASE_PATH + getTestName(false) + ".xml", BASE_PATH + "mylib.jar");
    String path = myFile.getVirtualFile().getParent().getPath() + "/";
    String[][] urls = {
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

  public void testUnqualifiedAttributePsi() {
    doTestWithLocations(null, "xml");
    final List<XmlAttribute> attrs = new ArrayList<>(2);

    myFile.acceptChildren(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(final @NotNull XmlAttribute attribute) {
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

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  public void testHighlightWhenNoNsSchemaLocation() throws Exception {
    final String testName = getTestName(false);

    doTest(
      new VirtualFile[]{
        findVirtualFile(BASE_PATH + testName + ".xml"),
        findVirtualFile(BASE_PATH + testName + ".xsd")
      },
      true,
      false
    );
  }

  public void testSchemaAutodetection() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "SchemaAutodetection/policy.xml"),
        findVirtualFile(BASE_PATH + "SchemaAutodetection/cs-xacml-schema-policy-01.xsd"),
        findVirtualFile(BASE_PATH + "SchemaAutodetection/cs-xacml-schema-context-01.xsd")
      },
      true,
      false
    );
  }

  public void testDtdAutodetection() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "nuancevoicexml-2-0.xml"),
        findVirtualFile(BASE_PATH + "nuancevoicexml-2-0.dtd")
      },
      true,
      false
    );
  }

  public void testSchemaWithXmlId() throws Exception {
    final String testName = getTestName(false);

    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + testName + ".xml")
      },
      true,
      false
    );
  }

  public void testProblemWithImportedNsReference() {
    doTestWithLocations(null, "xsd");
  }

  public void testBadXmlns() throws Exception {
    configureByFile(BASE_PATH + "badXmlns.xml");
    doHighlighting();
  }

  public void testProblemWithMemberTypes() {
    doTestWithLocations(null, "xsd");
  }

  public void testDtdHighlighting() {
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
        findVirtualFile(BASE_PATH + testName + ".xml"),
        findVirtualFile(BASE_PATH + testName + ".xsd")
      },
      true,
      false
    );
  }

  public void testUnresolvedSymbolForForAttribute() throws Exception {
    doTest();
  }

  public void testXsiType() throws Exception {
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
    final String testName = getTestName(false);

    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + testName + ".xml"),
        findVirtualFile(BASE_PATH + testName + "_Types.xsd"),
        findVirtualFile(BASE_PATH + testName + "_Request.xsd"),
        findVirtualFile(BASE_PATH + testName + "_Generic.xsd")
      },
      true,
      false
    );
  }

  public void testMappedSchemaLocation() {
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

  public void testSvgInHtml() throws Exception {
    enableInspectionTools(new HtmlUnknownTagInspection(),
                          new HtmlUnknownAttributeInspection(),
                          new HtmlUnknownBooleanAttributeInspection());
    doTest(getFullRelativeTestName(".html"), true, true);
  }

  public void testSvgAttrValueInHtml() throws Exception {
    enableInspectionTools(new HtmlWrongAttributeValueInspection());
    doTest(getFullRelativeTestName(".html"), true, false);
  }

  public void testAnyAttribute() {
    configureByFiles(null, BASE_PATH + "anyAttribute.xml", BASE_PATH + "services-1.0.xsd");
    doDoTest(true, false);
  }

  public void testAnyAttributeDefaultNamespace() {
    configureByFiles(null, BASE_PATH + "UnityEngine.xml", BASE_PATH + "UnityEngine.UIElements.xsd");
    doDoTest(true, false);
  }

  public void testSubstitution() throws Exception {
    doTest(new VirtualFile[]{
      findVirtualFile(BASE_PATH + "Substitute/test.xml"),
      findVirtualFile(BASE_PATH + "Substitute/schema-b.xsd"),
      findVirtualFile(BASE_PATH + "Substitute/schema-a.xsd")
    }, true, false);
  }

  public void testPrefixedSubstitution() throws Exception {
    doTest(new VirtualFile[]{
      findVirtualFile(BASE_PATH + "Substitute/prefixed.xml"),
      findVirtualFile(BASE_PATH + "Substitute/schema-b.xsd"),
      findVirtualFile(BASE_PATH + "Substitute/schema-a.xsd")
    }, true, false);
  }

  public void testSubstitutionFromImport() throws Exception {
    doTest(new VirtualFile[]{
      findVirtualFile(BASE_PATH + "SubstitutionGroup/problem-with-substitution-groups.xml"),
      findVirtualFile(BASE_PATH + "SubstitutionGroup/munit-runner.xsd"),
      findVirtualFile(BASE_PATH + "SubstitutionGroup/mule.xsd")
    }, true, false);
  }

  public void testDtdWithXsd() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "DtdWithXsd/help.xml"),
        findVirtualFile(BASE_PATH + "DtdWithXsd/helptopic.xsd"),
        findVirtualFile(BASE_PATH + "DtdWithXsd/html-entities.dtd")
      },
      true,
      false
    );
  }

  public void testAnyAttributeNavigation() throws Exception {
    configureByFiles(null, findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xml"),
                     findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xsd"),
                     findVirtualFile(BASE_PATH + "AnyAttributeNavigation/library.xsd"));

    PsiReference at = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());

    XmlTag tag = PsiTreeUtil.getParentOfType(at.getElement(), XmlTag.class);
    XmlElementDescriptorImpl descriptor = (XmlElementDescriptorImpl)tag.getDescriptor();
    XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
    LOG.debug(String.valueOf(Arrays.asList(descriptors)));

    doDoTest(true, false);

    PsiElement resolve = at.resolve();
    assertTrue(resolve instanceof XmlTag);
  }

  public void testDropAnyAttributeCacheOnExitFromDumbMode() throws Exception {
    try {
      DumbServiceImpl.getInstance(myProject).setDumb(true);
      configureByFiles(null, findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xml"),
                       findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xsd"),
                       findVirtualFile(BASE_PATH + "AnyAttributeNavigation/library.xsd"));
      PsiReference at = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());

      XmlTag tag = PsiTreeUtil.getParentOfType(at.getElement(), XmlTag.class);
      XmlElementDescriptor descriptor = tag.getDescriptor();
      XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
      LOG.debug(String.valueOf(Arrays.asList(descriptors)));
    }
    finally {
      DumbServiceImpl.getInstance(myProject).setDumb(false);
    }

    doDoTest(true, false);
  }

  public void testQualifiedAttributeReference() {
    configureByFiles(null, BASE_PATH + "qualified.xml", BASE_PATH + "qualified.xsd");
    doDoTest(true, false);
  }

  public void testUnqualifiedElement() {
    configureByFiles(null, BASE_PATH + "UnqualifiedElement.xml", BASE_PATH + "UnqualifiedElement.xsd");
    doDoTest(true, false);
  }

  public void testEnumeratedBoolean() {
    configureByFiles(null, BASE_PATH + "EnumeratedBoolean.xml", BASE_PATH + "EnumeratedBoolean.xsd");
    doDoTest(true, false);
  }

  public void testEnumeratedList() {
    configureByFiles(null, BASE_PATH + "servers.xml", BASE_PATH + "servers.xsd");
    doDoTest(true, false);
  }

  public void testEnumeratedExtension() {
    configureByFiles(null, BASE_PATH + "enumerations.xml", BASE_PATH + "enumerations.xsd");
    doDoTest(true, false);
  }

  public void testCustomBoolean() {
    configureByFiles(null, BASE_PATH + "CustomBoolean.xml", BASE_PATH + "CustomBoolean.xsd");
    doDoTest(true, false);
  }

  public void testStackOverflowInSchema() {
    configureByFiles(null, BASE_PATH + "XMLSchema_1_1.xsd");
    doHighlighting();
  }

  public void testSchemaVersioning() {
    configureByFiles(null, BASE_PATH + "Versioning.xsd");
    doDoTest(true, false);
  }

  public void testLinksInAttrValuesAndComments() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".xml");
    doDoTest(true, false);

    List<? extends PsiSymbolReference> list = Registry.is("ide.symbol.url.references")
                                              ? PlatformTestUtil.collectUrlReferences(myFile)
                                              : ContainerUtil.map(
                                                PlatformTestUtil.collectWebReferences(myFile),
                                                PsiSymbolService.getInstance()::asSymbolReference
                                              );
    assertEquals(2, list.size());

    Collections.sort(list, Comparator.comparingInt(o -> o.getRangeInElement().getLength()));

    assertEquals("https://www.jetbrains.com/ruby/download", getReferenceText(list.get(0)));
    assertTrue(list.get(0).getElement() instanceof XmlAttributeValue);
    assertEquals("http://blog.jetbrains.com/ruby/2012/04/rubymine-4-0-3-update-is-available/", getReferenceText(list.get(1)));
    assertTrue(list.get(1).getElement() instanceof XmlComment);
  }

  public void testBillionLaughs() {
    configureByFiles(null, BASE_PATH + "BillionLaughs.xml");
    XmlFile file = (XmlFile)getFile();
    int[] count = {0};
    XmlUtil.processXmlElements(file.getRootTag(), element -> {
      count[0]++;
      return true;}, false);
    assertEquals(9, count[0]);
  }

  public void testBillionLaughsValidation() {
    Locale locale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.ENGLISH);
      configureByFiles(null, BASE_PATH + "BillionLaughs.xml");
      doDoTest(false, false);
    }
    finally {
      Locale.setDefault(locale);
    }
  }

  public void testMaxOccurLimitValidation() {
    configureByFiles(null, BASE_PATH + "MaxOccurLimit.xml", BASE_PATH + "MaxOccurLimit.xsd");
    assertTrue(doHighlighting().stream().anyMatch(info -> info.getSeverity() == HighlightSeverity.ERROR));

    configureByFiles(null, BASE_PATH + "MaxOccurLimit.xml", BASE_PATH + "MaxOccurLimit.xsd");
    System.setProperty(ValidateXmlActionHandler.JDK_XML_MAX_OCCUR_LIMIT, "10000");
    assertFalse(doHighlighting().stream().anyMatch(info -> info.getSeverity() == HighlightSeverity.ERROR));
  }

  public void testTheSameElement() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "TheSameElement/IntelliJPersonData.xml"),
        findVirtualFile(BASE_PATH + "TheSameElement/IntellijCalTech.xsd"),
        findVirtualFile(BASE_PATH + "TheSameElement/IntelliJMeldeamt.xsd")
      },
      true,
      false
    );
  }

  public void testTheSameTypeName() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "TheSameTypeName/test2.xml"),
        findVirtualFile(BASE_PATH + "TheSameTypeName/test-common-xsd1.xsd"),
        findVirtualFile(BASE_PATH + "TheSameTypeName/test-common-xsd2.xsd"),
        findVirtualFile(BASE_PATH + "TheSameTypeName/test-xsd1.xsd"),
        findVirtualFile(BASE_PATH + "TheSameTypeName/test-xsd2.xsd"),
      },
      true,
      false
    );
  }

  public void testRedefine() throws Exception {
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "Redefine/derived.xsd"),
        findVirtualFile(BASE_PATH + "Redefine/base.xsd"),
      },
      true, false
    );
  }

  public void testRedefine2() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "Redefine/sample.xml"),
        findVirtualFile(BASE_PATH + "Redefine/derived.xsd"),
        findVirtualFile(BASE_PATH + "Redefine/base.xsd"),
      },
      true, false
    );
  }

  public void testRedefineGroup() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "RedefineGroup/test.xml"),
        findVirtualFile(BASE_PATH + "RedefineGroup/originalschema.xsd"),
        findVirtualFile(BASE_PATH + "RedefineGroup/redefinedschema.xsd"),
      },
      true, false
    );
  }

  public void testMultipleImports() throws Exception {
    doTest(
      new VirtualFile[] {
        findVirtualFile(BASE_PATH + "MultipleImports/agg.xsd"),
        findVirtualFile(BASE_PATH + "MultipleImports/toimport1.xsd"),
        findVirtualFile(BASE_PATH + "MultipleImports/toimport2.xsd"),
      },
      true, false
    );
  }

  public void testImportedAttr() {
    configureByFiles(null, BASE_PATH + "ImportedAttr/main.xml",
                     BASE_PATH + "ImportedAttr/main.xsd",
                     BASE_PATH + "ImportedAttr/include.xsd");
    doHighlighting();
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

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS = old;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
