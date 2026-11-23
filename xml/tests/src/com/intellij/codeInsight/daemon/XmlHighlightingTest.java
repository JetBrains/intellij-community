// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.application.options.XmlSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.XmlDefaultAttributeValueInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownBooleanAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.codeInspection.htmlInspections.HtmlWrongAttributeValueInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInspection.htmlInspections.XmlWrongRootElementInspection;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.highlighter.XmlHighlighterFactory;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.javaee.UriUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.ant.dom.AntResolveInspection;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.TokenType;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.actions.validate.ValidateXmlActionHandler;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;
import com.intellij.xml.util.XmlDuplicatedIdInspection;
import com.intellij.xml.util.XmlInvalidIdInspection;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static com.intellij.model.psi.PsiSymbolReference.getReferenceText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"HardCodedStringLiteral", "ConstantConditions"})
@TestApplication
public class XmlHighlightingTest {
  private static final Logger LOG = Logger.getInstance(XmlHighlightingTest.class);
  private static final String BASE_PATH = "/xml/";
  private static final String TEST_DATA_PATH = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(TEST_DATA_PATH, () -> new LocalInspectionTool[]{
    new RequiredAttributesInspection(),
    new XmlDuplicatedIdInspection(),
    new XmlInvalidIdInspection(),
    new CheckDtdReferencesInspection(),
    new XmlUnboundNsPrefixInspection(),
    new XmlPathReferenceInspection(),
    new XmlUnresolvedReferenceInspection()
  });

  @NonNls private static final String CREATE_NAMESPACE_DECLARATION_INTENTION_NAME = "Create namespace declaration";
  private boolean old;
  private String myOldDoctype;

  private void doTest() throws Exception {
    doTest(false);
  }
  private void doTest(boolean checkWarnings) throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    XmlHighlightVisitor.setDoJaxpTesting(fixture.isForceExternalValidation());
    try {
      fixture.doTest(getFullRelativeTestName(), checkWarnings, false);
    }
    finally {
      XmlHighlightVisitor.setDoJaxpTesting(false);
    }
  }

  private String getFullRelativeTestName() {
    return getFullRelativeTestName(".xml");
  }

  private String getFullRelativeTestName(String ext) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    return BASE_PATH + fixture.getTestName(false) + ext;
  }

  @Test
  public void testclosedTag1() throws Exception { doTest(); }
  @Test
  public void testClosedTag2() throws Exception { doTest(); }
  @Test
  public void testwrongRootTag1() throws Exception { doTest(); }
  @Test
  public void testrootTag1() throws Exception { doTest(); }
  @Test
  public void testManyRootTags() throws Exception { doTest(); }
  @Test
  public void testCommentBeforeProlog() throws Exception { doTest(); }
  @Test
  public void testXmlStylesheet() throws Exception { doTest(); }
  @Test
  public void testCommentBeforeProlog_2() throws Exception { doTest(); }
  //public void testNoRootTag() throws Exception { doTest(); }

  @Test
  public void testduplicateAttribute() throws Exception { doTest(); }

  @Test
  public void testduplicateAttribute2() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + fixture.getTestName(false) + ".xml", BASE_PATH + fixture.getTestName(false) + ".xsd");

    final String url = "http://www.foo.org/schema";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, fixture.getTestName(false) + ".xsd", fixture.getTestRootDisposable());
    final String url2 = "http://www.bar.org/foo";
    ExternalResourceManagerExBase.registerResourceTemporarily(url2, fixture.getTestName(false) + ".xsd", fixture.getTestRootDisposable());
    final Collection<HighlightInfo> infoCollection = fixture.doDoTest(true, false, true);
    final TextRange startTagNameRange = ReadAction.computeBlocking(
      () -> XmlTagUtil.getStartTagNameElement(((XmlFile)fixture.getFile()).getDocument().getRootTag()).getTextRange());

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
  @Test
  public void testDuplicateIdAttribute() throws Exception { doTest(); }
  @Test
  public void testDuplicateIdAttribute2() throws Exception {
    doTest();
  }
  @Test
  public void testDuplicateNameAttribute() throws Exception {
    doSchemaTestWithManyFilesFromSeparateDir(
      new String[][] {
        {"http://www.springframework.org/schema/beans/spring-beans-2.5.xsd", "spring-beans-2.5.xsd"},
      },
      null
    );
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testInvalidIdRefAttribute() throws Exception { doTest(); }
  @Test
  public void testEntityRefWithNoDtd() throws Exception { doTest(); }
  @Test
  public void testNoSpaceBeforeAttrAndNoCdataEnd() throws Exception { doTest(); }

  // TODO: external validator should not be launched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testEntityRefWithEmptyDtd() throws Exception { doTest(); }
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testEmptyNSRef() throws Exception { doTest(); }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testDoctypeWithoutSchema() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String baseName = BASE_PATH + fixture.getTestName(false);

    fixture.configureByFiles(null, fixture.findVirtualFile(baseName + ".xml"), fixture.findVirtualFile(baseName + ".ent"));
    fixture.doDoTest(true,false);
    ReadAction.run(() -> fixture.getFile().accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        final PsiElement[] children = value.getChildren();
        for (PsiElement child : children) {
          if (child instanceof XmlEntityRef) {
            PsiElement psiElement = child.getReferences()[0].resolve();
            assertNotNull(psiElement);
            assertEquals(fixture.getTestName(false) + ".ent", psiElement.getContainingFile().getVirtualFile().getName());
            assertTrue(((Navigatable)psiElement).canNavigate());
          }
        }
      }
    }));
  }

  @Test
  public void testSvg() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".svg"), true, false);
  }

  @Test
  public void testSvg20() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    InspectionsKt.enableInspectionTools(fixture.getProject(), fixture.getTestRootDisposable(), new XmlDefaultAttributeValueInspection());
    fixture.doTest(getFullRelativeTestName(".svg"), true, false);
  }

  @Test
  public void testNavigateToDeclDefinedWithEntity() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String baseName = BASE_PATH + fixture.getTestName(false);

    fixture.configureByFiles(null, fixture.findVirtualFile(baseName + ".xml"), fixture.findVirtualFile(baseName + ".dtd"), fixture.findVirtualFile(baseName + ".ent"));
    fixture.doDoTest(true,false);
    ReadAction.run(() -> {
      final List<PsiReference> refs = new ArrayList<>();
      fixture.getFile().accept(new XmlRecursiveElementVisitor() {
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
    });
  }

  @Test
  public void testDoctypeWithoutSchema2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String baseName = BASE_PATH + "DoctypeWithoutSchema";

    fixture.configureByFiles(null, fixture.findVirtualFile(baseName + ".xml"), fixture.findVirtualFile(baseName + ".ent"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testComplexSchemaValidation8() throws IOException {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    String location = testName +".xsd";
    String location2 = testName +"_2.xsd";
    String url = "http://drools.org/rules";
    String url2 = "http://drools.org/semantics/groovy";

    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily(url2, location2, fixture.getTestRootDisposable());
    final String basePath = BASE_PATH + testName;
    fixture.configureByFiles(null, fixture.findVirtualFile(basePath + ".xml"), fixture.findVirtualFile(basePath + ".xsd"), fixture.findVirtualFile(basePath + "_2.xsd"));
    fixture.doDoTest(true,false);

  }

  @Test
  public void testComplexSchemaValidation9() throws IOException {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String basePath = BASE_PATH + fixture.getTestName(false);

    fixture.configureByFiles(null, fixture.findVirtualFile(basePath + ".xml"), fixture.findVirtualFile(basePath + ".xsd"));
    fixture.doDoTest(true,false);
  }

  @Test
  public void testComplexSchemaValidation10() throws IOException {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);

    final String basePath = BASE_PATH + testName;
    fixture.configureByFiles(null, fixture.findVirtualFile(basePath + ".xsd"), fixture.findVirtualFile(basePath + "_2.xsd"));
    fixture.doDoTest(true,false);

    ReadAction.run(() -> {
      final List<PsiReference> refs = new ArrayList<>(2);

      fixture.getFile().acceptChildren(new XmlRecursiveElementVisitor() {

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
      assertEquals(fixture.getTestName(false) + "_2.xsd",psiElement.getContainingFile().getName());

      psiElement = refs.get(1).resolve();
      assertNotNull(psiElement);
      assertEquals(fixture.getTestName(false) + "_2.xsd",psiElement.getContainingFile().getName());
    });
  }

  private static void addRefsInPresent(final XmlTag tag, final String name, final List<? super PsiReference> refs) {
    if (tag.getAttributeValue(name) != null) {
      ContainerUtil.addAll(refs, tag.getAttribute(name, null).getValueElement().getReferences());
    }
  }

  @Test
  public void testComplexSchemaValidation11() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    doTestWithLocations(
      new String[][] {
        {"http://www.springframework.org/schema/beans",testName + ".xsd"},
        {"http://www.springframework.org/schema/util",testName + "_2.xsd"}
      },
      "xml"
    );
  }

  private void doTestWithLocations(String[] @Nullable [] resources, String ext) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doConfigureWithLocations(resources, ext);
    fixture.doDoTest(true,false);
  }

  private void doConfigureWithLocations(final String[][] resources, final String ext) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();

    String[] testNames = new String[(resources != null? resources.length:0) + 1];
    testNames[0] = BASE_PATH + fixture.getTestName(false) + "." + ext;

    if (resources != null) {
      int curResource = 0;
      for(String[] resource:resources) {
        ExternalResourceManagerExBase
          .registerResourceTemporarily(resource[0], TEST_DATA_PATH + BASE_PATH + resource[1], fixture.getTestRootDisposable());
        testNames[++curResource] = BASE_PATH + resource[1];
      }
    }

    fixture.configureByFiles(
      null,
      testNames
    );
  }

  @Test
  public void testComplexSchemaValidation12() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String basePath = BASE_PATH + fixture.getTestName(false);
    fixture.configureByFile(
      basePath + ".xsd",
      null
    );
    fixture.doDoTest(true,false);
  }

  @Test
  public void testInvalidIdRefInXsl() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(), false, false);
  }

  @Test
  public void testXercesMessageOnPI() throws Exception {
    try {
      daemonFixture.get().setForceExternalValidation(true);
      doTest();
    } finally {
      daemonFixture.get().setForceExternalValidation(false);
    }
  }
  @Test
  public void testXercesMessageOnDtd() throws Exception { doTest(); }
  @Test
  public void testXercesMessagesBinding5() throws Exception {
    try {
      daemonFixture.get().setForceExternalValidation(true);
      doTest();
    } finally {
      daemonFixture.get().setForceExternalValidation(false);
    }
  }

  @Test
  public void testXercesCachingProblem() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String[][] resources = {{"http://www.test.com/test", fixture.getTestName(false) + ".dtd"}};
    doConfigureWithLocations(resources, "xml");

    fixture.doDoTest(true,true);
    WriteCommandAction.runWriteCommandAction(null, () -> fixture.getEditor().getDocument().insertString(fixture.getEditor().getDocument().getCharsSequence().toString().indexOf("?>") + 2, "\n"));

    fixture.doDoTest(true,true);
  }

  @Test
  public void testComplexSchemaValidation13() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTestWithLocations(new String[][] { {"urn:test", fixture.getTestName(false) + "_2.xsd"} }, "xsd");
  }

  @Test
  public void testComplexSchemaValidation14() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTestWithLocations(new String[][]{{"parent", fixture.getTestName(false) + ".xsd"}}, "xml");
  }

  @Test
  public void testComplexSchemaValidation14_2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xsd"), true, false);
  }

  @Test
  public void testComplexSchemaValidation15() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTestWithLocations(
      new String[][] {
        {"http://www.linkedin.com/lispring", fixture.getTestName(false) + ".xsd"},
        {"http://www.springframework.org/schema/beans", fixture.getTestName(false) + "_2.xsd"}
      },
      "xml"
    );
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testComplexSchemaValidation16() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTestWithLocations(
      new String[][] {
        {"http://www.inversoft.com/schemas/savant-2.0/project", fixture.getTestName(false) + ".xsd"},
        {"http://www.inversoft.com/schemas/savant-2.0/base", fixture.getTestName(false) + "_2.xsd"}
      },
      "xml"
    );
  }

  @Test
  public void testComplexSchemaValidation17() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTestWithLocations(
      new String[][]{
        {"urn:test", fixture.getTestName(false) + ".xsd"}
      },
      "xml"
    );
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testSchemaReferencesValidation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xsd"), false, false);
  }

  @Test
  public void testXhtmlSchemaHighlighting() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    disableHtmlSupport();
    try {
      fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + "xhtml1-transitional.xsd"), fixture.findVirtualFile(BASE_PATH + "xhtml-special.ent"),
                       fixture.findVirtualFile(BASE_PATH + "xhtml-symbol.ent"), fixture.findVirtualFile(BASE_PATH + "xhtml-lat1.ent"));
      fixture.doDoTest(true,false);
    }
    finally {
      enableHtmlSupport();
    }
  }

  @Test
  public void testSchemaValidation() throws Exception {
    doTest();
  }

  @Test
  public void testSchemaValidation2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + ".xml"),
                     fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + ".xsd"));
    fixture.doDoTest(true,false);

    ReadAction.run(() -> {
      final XmlTag rootTag = ((XmlFile)fixture.getFile()).getDocument().getRootTag();
      final XmlAttributeValue valueElement = rootTag.getAttribute("noNamespaceSchemaLocation", XmlUtil.XML_SCHEMA_INSTANCE_URI).getValueElement();
      final PsiReference[] references = valueElement.getReferences();

      final String expectedFileName = fixture.getTestName(false) + ".xsd";

      assertEquals(1, references.length);
      final PsiElement schema = references[0].resolve();
      assertNotNull(schema);

      assertEquals(expectedFileName,schema.getContainingFile().getName());

      final PsiReference referenceAt = fixture.getFile().findReferenceAt(fixture.getEditor().getCaretModel().getOffset());
      assertNotNull(referenceAt);
      final PsiElement psiElement = referenceAt.resolve();
      assertTrue(psiElement instanceof XmlTag &&
                 "complexType".equals(((XmlTag)psiElement).getLocalName()) &&
                 "Y".equals(((XmlTag)psiElement).getAttributeValue("name"))
                 );

      assertEquals(expectedFileName,psiElement.getContainingFile().getName());
    });
  }

  @Test
  public void testSchemaValidation3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = fixture.getTestName(false)+".xsd";
    String location2 = fixture.getTestName(false)+"_2.xsd";
    String url = "parent";
    String url2 = "child";

    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily(url2, location2, fixture.getTestRootDisposable());

    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + ".xml"), fixture.findVirtualFile(BASE_PATH + location),
                     fixture.findVirtualFile(BASE_PATH + location2));
    fixture.doDoTest(true,false);

  }

  @Test
  public void testSchemaValidation4() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String schemaLocation = fixture.getTestName(false)+".xsd";

    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + ".xml"), fixture.findVirtualFile(BASE_PATH + schemaLocation));

    ExternalResourceManagerExBase.registerResourceTemporarily(schemaLocation, schemaLocation, fixture.getTestRootDisposable());
    fixture.doDoTest(true, false);
  }

  @Test
  public void testSchemaValidation5() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String schemaLocation = fixture.getTestName(false)+".xsd";

    fixture.configureByFile(BASE_PATH + schemaLocation);
    fixture.doDoTest(true, false);
    final List<PsiReference> myTypeOrElementRefs = new ArrayList<>(1);
    final List<XmlTag> myTypesAndElementDecls = new ArrayList<>(1);

    ReadAction.run(() -> fixture.getFile().accept(new XmlRecursiveElementVisitor() {
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
    }));

    assertEquals(9,myTypesAndElementDecls.size());
    assertEquals(5,myTypeOrElementRefs.size());
    for(XmlTag t:myTypesAndElementDecls) {
      final PsiReference nameReference = ReadAction.computeBlocking(() -> {
        final XmlAttribute attribute = t.getAttribute("name", null);
        final XmlAttributeValue valueElement = attribute.getValueElement();
        return valueElement.getReferences()[0];
      });
      WriteCommandAction.runWriteCommandAction(null, () -> {
        nameReference.handleElementRename("zzz");
      });
    }

    ReadAction.run(() -> {
      for(PsiReference ref:myTypeOrElementRefs)  {
        assertNull(ref.resolve());
      }
    });
  }

  @Test
  public void testExternalValidatorOnValidXmlWithNamespacesNotSetup() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    List<String> list = new ArrayList<>();
    list.add("http://xml.apache.org/axis/wsdd2/");
    list.add("http://xml.apache.org/axis/wsdd2/providers/java");
    list.add("http://soapinterop.org/xsd2");
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(list, fixture.getTestRootDisposable());

    fixture.doTest(getFullRelativeTestName(".xml"), true, false);
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testExternalValidatorOnValidXmlWithNamespacesNotSetup2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList(""), fixture.getTestRootDisposable());
    fixture.doTest(getFullRelativeTestName(".xml"), true, false);
  }

  @Test
  public void testXercesMessagesBinding() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xml"), true, false);
  }

  @Test
  public void testXercesMessagesBinding3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xml"), true, false);
  }

  @Test
  public void testXercesMessagesBinding4() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String url = "antlib:org.apache.maven.artifact.ant";

    fixture.enableInspectionTool(new AntResolveInspection());
    doTestWithLocations(new String[][]{{url, fixture.getTestName(false) + ".xsd"}}, "xml");
  }

  @Test
  public void testWrongRegExpInSchema() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xsd"), true, false);
    fixture.doTest(getFullRelativeTestName("2.xsd"), true, false);
  }

  @Test
  public void testWrongRegExpCategory() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xsd"), true, false);
  }

  @Test
  public void testXercesMessagesBinding2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String url = fixture.getTestName(false) + ".xsd";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, url, fixture.getTestRootDisposable());

    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(getFullRelativeTestName(".xml")),
        fixture.findVirtualFile(getFullRelativeTestName(".xsd"))
      },
      true,
      false
    );

    final String url2 = fixture.getTestName(false) + "_2.xsd";

    ExternalResourceManagerExBase.registerResourceTemporarily(url2, url2, fixture.getTestRootDisposable());

    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(getFullRelativeTestName("_2.xml")),
        fixture.findVirtualFile(getFullRelativeTestName("_2.xsd"))
      },
      true,
      false
    );

  }

  @Test
  public void testValidationOfDtdFile() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".dtd"), false, false);
  }

  @Test
  public void testXHtmlValidation() throws Exception {
    doTest();
  }

  @Test
  public void testXHtmlValidation2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(getFullRelativeTestName());
    fixture.doDoTest(true, true, true);
  }

  @Test
  public void testXHtmlValidation3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doManyFilesFromSeparateDirTest(
      "http://www.w3.org/TR/xhtml-basic/xhtml-basic11.dtd",
      "xhtml-basic11.dtd",
      () -> {
        final List<XmlAttribute> attrs = new ArrayList<>();

        fixture.getFile().acceptChildren(new XmlRecursiveElementVisitor() {
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

  @Test
  public void testReferencingTargetNamespace() throws Exception {
    doTest();
  }

  @Test
  public void testWebApp() throws Exception {
    doTest();
  }

  @Test
  public void testEnumeratedAttributesValidation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(BASE_PATH + fixture.getTestName(false) + ".xsd", false, false);
  }

  @Test
  public void testStackOverflow() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = "relaxng.xsd";
    String url = "http://relaxng.org/ns/structure/fake/1.0";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    fixture.doTest(new VirtualFile[]{fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location)}, false, false);
  }

  @Test
  public void testStackOverflow2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    RecursionManager.disableMissedCacheAssertions(fixture.getTestRootDisposable());
    final String url = "urn:aaa";
    final String location = fixture.getTestName(false) + ".xsd";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    fixture.doTest(getFullRelativeTestName(".xsd"), false, false);

  }

  @Test
  public void testComplexSchemaValidation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(), false, false);
  }

  @Test
  public void testComplexDtdValidation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = "Tapestry_3_0.dtd";
    String url = "http://jakarta.apache.org/tapestry/dtd/Tapestry_3_0.dtd";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());

    fixture.setForceExternalValidation(true);
    fixture.doTest(new VirtualFile[] {fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location)}, false, false);
    fixture.setForceExternalValidation(false);
  }

  @Test
  public void testComplexDtdValidation2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = fixture.getTestName(false)+".dtd";
    ExternalResourceManagerExBase.registerResourceTemporarily(location, location, fixture.getTestRootDisposable());

    fixture.setForceExternalValidation(true);
    fixture.doTest(new VirtualFile[] {fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location)}, false, false);
    fixture.setForceExternalValidation(false);
  }

  @Test
  public void testComplexSchemaValidation2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(new VirtualFile[] {fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + "jdo_2_0.xsd")}, false, false);
  }

  @Test
  public void testComplexSchemaValidation3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    List<VirtualFile> files = new ArrayList<>();
    files.add(fixture.findVirtualFile(getFullRelativeTestName()));

    final VirtualFile virtualFile = fixture.findVirtualFile(BASE_PATH + "ComplexSchemaValidation3Schemas");
    ContainerUtil.addAll(files, virtualFile.getChildren());

    fixture.doTest(VfsUtilCore.toVirtualFileArray(files), true, false);
  }

  @Test
  public void testComplexSchemaValidation4() throws Exception {
    String[][] urls = {{"http://www.unece.org/etrades/unedocs/repository/codelists/xml/CountryCode.xsd"},
      {"http://www.swissdec.ch/schema/sd/20050902/SalaryDeclarationServiceTypes"},
      {"http://www.swissdec.ch/schema/sd/20050902/SalaryDeclarationContainer"},
      {"http://www.swissdec.ch/schema/sd/20050902/SalaryDeclaration"}};

    doSchemaTestWithManyFilesFromSeparateDir(urls, null);
  }

  @Test
  public void testComplexSchemaValidation5() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = fixture.getTestName(false)+".xsd";
    String url = "http://www.etas.com/TELEGY/Test";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    fixture.doTest(new VirtualFile[] {fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location)}, false, false);

    ReadAction.run(() -> {
      final XmlTag[] subTags = ((XmlFile)fixture.getFile()).getDocument().getRootTag().getSubTags();

      for(XmlTag t:subTags) {
        final PsiReference reference = t.getReference();
        assertNotNull(reference);
        final PsiElement psiElement = reference.resolve();
        assertTrue(psiElement instanceof XmlTag && "xsd:element".equals(((XmlTag)psiElement).getName()));
      }
    });

  }

  @Test
  public void testComplexSchemaValidation6() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = fixture.getTestName(false)+".xsd";
    String url = "http://abcde/pg.html";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    fixture.doTest(new VirtualFile[]{fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location)}, true, false);
  }

  @Test
  public void testComplexSchemaValidation7() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
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
          files.set(0, fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "_2.xml"));
          fixture.doTest(VfsUtilCore.toVirtualFileArray(files), true, false);
          return true;
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    );
  }

  @Test
  public void testComplexSchemaValidation7_() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[]{
        fixture.findVirtualFile(getFullRelativeTestName()),
        fixture.findVirtualFile(getFullRelativeTestName(".xsd"))
      },
      false,
      false
    );
  }

  @Test
  public void testWsdlValidation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".wsdl"), false, false);
  }

  @Test
  public void testErrorInDtd() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = "def_xslt.dtd";
    String url = "http://www.w3.org/1999/XSL/Transform";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    fixture.doTest(new VirtualFile[]{fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location)}, false, false);
  }

  @Test
  public void testResolveEntityUrl() throws Throwable {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(new VirtualFile[] {
      fixture.findVirtualFile(getFullRelativeTestName()),
      fixture.findVirtualFile(BASE_PATH + "entities.dtd")
    }, false, false);
  }

  @Test
  public void testXsiSchemaLocation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTest();
    fixture.doTest(getFullRelativeTestName("_2.xml"), false, false);
  }

  @Test
  public void testXmlPsi() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + fixture.getTestName(false) + ".xml");

    ReadAction.run(() -> {
      final XmlTag root = ((XmlFile)fixture.getFile()).getDocument().getRootTag();
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
    });
  }

  @Test
  public void testXsiSchemaLocation2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[]{
        fixture.findVirtualFile(getFullRelativeTestName()),
        fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "_1.xsd"),
        fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "_2.xsd"),
        fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "_3.xsd"),
      },
      false,
      false
    );

    ReadAction.run(() -> {
      XmlTag rootTag = ((XmlFile)fixture.getFile()).getDocument().getRootTag();
      checkOneTagForSchemaAttribute(rootTag, "xmlns:test2", fixture.getTestName(false) + "_2.xsd");
    });

    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "_2.xsd"),
                     fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "_3.xsd"));

    ReadAction.run(() -> {
      XmlTag rootTag = ((XmlFile)fixture.getFile()).getDocument().getRootTag();
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

      assertEquals(11, tags.size(), "Should be adequate number of tags");

      final String resolveFileName = fixture.getTestName(false) + "_3.xsd";
      checkOneTagForSchemaAttribute(tags.get(1), "type", resolveFileName);
      checkOneTagForSchemaAttribute(tags.get(0), "ref", resolveFileName);
      checkOneTagForSchemaAttribute(tags.get(3), "type", resolveFileName);
      checkOneTagForSchemaAttribute(tags.get(4), "base", resolveFileName);
      final String schemaFile = "XMLSchema.xsd";
      checkOneTagForSchemaAttribute(tags.get(5), "base", schemaFile);

      checkOneTagForSchemaAttribute(rootTag, "xmlns:xs", schemaFile);
      checkOneTagForSchemaAttribute(tags.get(6), "ref", resolveFileName);
      checkOneTagForSchemaAttribute(tags.get(7), "ref", resolveFileName);
      checkOneTagForSchemaAttribute(rootTag, "xmlns:test2", fixture.getTestName(false) + "_2.xsd");

      checkOneTagForSchemaAttribute(tags.get(8), "type", schemaFile);
      checkOneTagForSchemaAttribute(tags.get(9), "ref", schemaFile);
      checkOneTagForSchemaAttribute(tags.get(10), "ref", resolveFileName);
    });
  }

  private static void checkOneTagForSchemaAttribute(XmlTag tag,String schemaAttrName, String resolveFileName) {
    String attributeValue = tag.getAttributeValue(schemaAttrName);

    if (attributeValue != null) {
      XmlAttribute attribute = tag.getAttribute(schemaAttrName, null);
      PsiReference[] references = attribute.getValueElement().getReferences();
      assertTrue(references.length > 0, "There should resolvable reference to " + schemaAttrName);

      for (PsiReference reference : references) {
        PsiElement element = reference.resolve();
        if (element != null && element.getContainingFile().getName().equals(resolveFileName)) {
          return;
        }
      }
      assertTrue(true, "There should resolvable reference to "+ schemaAttrName + ", 2");
    }
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testXsltValidation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xsl"), true, false);
    fixture.doTest(getFullRelativeTestName("2.xsl"), true, false);
  }

  @Test
  public void testXsltValidation3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".xsl"), true, false);
  }

  @Test
  public void testXsiSchemaLocation3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();

    fixture.configureByFile(getFullRelativeTestName(".xsd"), null);

    fixture.doDoTest(true,false);

    ReadAction.run(() -> {
      final List<XmlTag> tags = new ArrayList<>();

      XmlUtil.processXmlElements(
        ((XmlFile)fixture.getFile()).getDocument(),
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

      assertEquals(3, tags.size(), "Should be three tags");
      String location = "xslt-1_0.xsd";
      checkOneTagForSchemaAttribute(tags.get(2), "schemaLocation", location);
    });
  }

  @Test
  public void testResolvingEntitiesInDtd() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + ".xml"),
                     fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "/RatingandServiceSelectionRequest.dtd"),
                     fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "/XpciInterchange.dtd"),
                     fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + "/Xpcivocabulary.dtd"));
    fixture.doDoTest(true, false);
  }

  // TODO: external validator should not be lauched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testEntityHighlighting() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTest();
    ReadAction.run(() -> {
      final XmlTag rootTag = ((XmlFile)fixture.getFile()).getDocument().getRootTag();
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

      assertEquals(2, refs.size(), "Should be 2 entity refs");

      PsiReference[] entityRefs = refs.get(0).getReferences();
      assertTrue(entityRefs.length == 1 &&
                 entityRefs[0].resolve() == null
                );
      entityRefs = refs.get(1).getReferences();
      assertTrue(entityRefs.length == 1 &&
                 entityRefs[0].resolve() != null
                );
    });

    fixture.doTest(getFullRelativeTestName("2.xml"), false, false);
  }

  @Test
  public void testEntityRefBadFormat() throws Exception {
    doTest();
  }

  @Test
  public void testIgnoredNamespaceHighlighting() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList("http://ignored/uri"), fixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testNonEnumeratedValuesHighlighting() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String url = "http://www.w3.org/1999/XSL/Format";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, "fop.xsd", fixture.getTestRootDisposable());
    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + ".xml"), fixture.findVirtualFile(BASE_PATH + "fop.xsd"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testEditorHighlighting() {
    //                       10
    //             0123456789012
    String text = "<html></html>";
    EditorHighlighter xmlHighlighter = XmlHighlighterFactory.createXMLHighlighter(EditorColorsManager.getInstance().getGlobalScheme());
    xmlHighlighter.setText(text);
    HighlighterIterator iterator = xmlHighlighter.createIterator(1);
    assertSame(XmlTokenType.XML_TAG_NAME, iterator.getTokenType(), "Xml tag name");
    iterator = xmlHighlighter.createIterator(8);
    assertSame(XmlTokenType.XML_TAG_NAME, iterator.getTokenType(), "Xml tag name at end of tag");

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
    assertSame(XmlElementType.XML_MARKUP_DECL, iterator.getTokenType(), "Xml unfinished markup declaration");
    assertEquals(17, iterator.getStart());
    assertEquals(70, iterator.getEnd());

    //              10        20        30        40          50        60
    //    012345678901234567890123456789012345678901 234567 890123456789012 345678 90
    text="<a name='$'/>";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(9);
    assertEquals(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType(), "$ in attr value");
    iterator = xmlHighlighter.createIterator(10);
    assertEquals(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, iterator.getTokenType(), "' after $ in attr value");

    //                10         20         30        40           50        60
    //      0123456789012345678 901 23456789012345678901 2345 67 890123456789012 345678 90
    text = "<project><jar file=\"${\"> </jar><jar file=\"${}\"/></project> ";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(22);
    assertEquals(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, iterator.getTokenType(), "${ in attr value");

    //                10
    //      01234567890123456789
    text = "<!--&nbsp; &aaa;-->";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(5);
    assertEquals(XmlTokenType.XML_COMMENT_CHARACTERS, iterator.getTokenType(), "char entity in comment");

    iterator = xmlHighlighter.createIterator(12);
    assertEquals(XmlTokenType.XML_COMMENT_CHARACTERS, iterator.getTokenType(), "entity ref in comment");

    //                10
    //      01234567890123456789
    text = "<!-- -- -->";

    xmlHighlighter.setText(text);
    iterator = xmlHighlighter.createIterator(5);
    assertEquals(XmlTokenType.XML_BAD_CHARACTER, iterator.getTokenType(), "double -- in xml comment");

  }

  @Test
  public void testUsingDtdReference() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String baseName = BASE_PATH + fixture.getTestName(false);

    fixture.configureByFiles(null, fixture.findVirtualFile(baseName + ".xml"), fixture.findVirtualFile(baseName + ".dtd"), fixture.findVirtualFile(baseName + "2.dtd"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testUnboundNsHighlighting() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + "web-app_2_4.xsd");
    final String testName = fixture.getTestName(false);
    fixture.doTest(BASE_PATH + testName +".xml",true, false);

    doTestWithUnboundNSQuickFix(BASE_PATH + testName + "2");
//    doTestWithUnboundNSQuickFix(BASE_PATH + testName + "3");

    fixture.configureByFiles(null, BASE_PATH + testName +"4.xml", BASE_PATH + testName +"4.dtd");
    fixture.doDoTest(true, false);

    doTestWithUnboundNSQuickFix(BASE_PATH + testName + "5");
  }

  @Test
  public void testDoctypeSystemConfigured() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    ExternalResourceManagerExBase.registerResourceTemporarily("sample.dtd",
                                                              TEST_DATA_PATH + BASE_PATH + "sample.dtd",
                                                              fixture.getTestRootDisposable());
    fixture.configureByFiles(null, BASE_PATH + "sample.xml", BASE_PATH + "sample.dtd");
    fixture.doDoTest(true, false);
  }

  @Test
  public void testUnboundNsHighlighting6() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + "web-app_2_4.xsd");
    doTestWithQuickFix(BASE_PATH + fixture.getTestName(false), CREATE_NAMESPACE_DECLARATION_INTENTION_NAME, true);
  }

  private void doTestWithUnboundNSQuickFix(final String s) throws Exception {
    doTestWithQuickFix(s, CREATE_NAMESPACE_DECLARATION_INTENTION_NAME, false);
  }

  private void doTestWithQuickFix(final String s, final String intentionActionName, boolean withInfos) throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(s + ".xml");
    Collection<HighlightInfo> infos = fixture.doDoTest(true, withInfos);

    fixture.findAndInvokeIntentionAction(infos, intentionActionName);
    fixture.checkResultByFile(s + "_after.xml");
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testSpecifyXsiSchemaLocationQuickFix() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + "web-app_2_4.xsd");
    final String testName = fixture.getTestName(false);
    final String actionName = XmlBundle.message("xml.intention.add.xsi.schema.location.for.external.resource");
    doTestWithQuickFix(BASE_PATH + testName, actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "2", actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "3", actionName, false);
    doTestWithQuickFix(BASE_PATH + testName + "4", actionName, false);
  }

  @Test
  public void testHighlightingWithConditionalSectionsInDtd() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);

    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + testName + ".xml"), fixture.findVirtualFile(BASE_PATH + testName + ".dtd"));
    fixture.doDoTest(true, false);

    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + testName + "2.xml"), fixture.findVirtualFile(BASE_PATH + testName + ".dtd"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testUnresolvedDtdElementReferences() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doDtdRefsWithQuickFixTestSequence(BASE_PATH + fixture.getTestName(false), "dtd");
  }

  @Test
  public void testResolvedDtdElementReferences() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + fixture.getTestName(false)+".dtd");
    fixture.doDoTest(true, false);

    final String text = fixture.getEditor().getDocument().getText();
    WriteCommandAction.runWriteCommandAction(null, () -> fixture.getEditor().getSelectionModel().setSelection(0, fixture.getEditor().getDocument().getTextLength()));

    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_COMMENT_BLOCK);
    EdtTestUtil.runInEdtAndWait(
      () -> action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext())));
    assertNotSame(text,fixture.getEditor().getDocument().getText());
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(fixture.getProject()).commitDocument(fixture.getEditor().getDocument()));
    Collection<HighlightInfo> infos = fixture.doHighlighting();
    assertEquals(0, infos.size());

    EdtTestUtil.runInEdtAndWait(
      () -> action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext())));
    assertEquals(text,fixture.getEditor().getDocument().getText().trim());
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(fixture.getProject()).commitDocument(fixture.getEditor().getDocument()));
    infos = fixture.doHighlighting();
    assertEquals(0, infos.size());
  }

  // TODO: external validator should not be lauched due to error detected after general highlighting pass!
  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testUnresolvedDtdElementReferencesInXml() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doDtdRefsWithQuickFixTestSequence(BASE_PATH + fixture.getTestName(false), "xml");
  }

  @Test
  public void testUnresolvedDtdElementReferencesInXml2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String s = BASE_PATH + fixture.getTestName(false);
    doDtdEntityRefWithQuickFixTest(s, "xml");
    doDtdEntityRefWithQuickFixTest(s + "_2", "xml");
  }

  @Test
  public void testUnresolvedDtdElementReferencesInXml3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doDtdEntityRefWithQuickFixTest(BASE_PATH + fixture.getTestName(false), "xml");
  }

  @Test
  public void testUnresolvedDtdEntityReference() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String s = BASE_PATH + fixture.getTestName(false);
    doDtdEntityRefWithQuickFixTest(s, "dtd");
  }

  private void doDtdEntityRefWithQuickFixTest(final String s, String ext) throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(s + "." + ext);
    Collection<HighlightInfo> infos = fixture.doDoTest(true, false);
    fixture.findAndInvokeIntentionAction(infos, "Create Entity Declaration entity");
    fixture.checkResultByFile(s+"_after" + "." + ext);
  }

  private void doDtdRefsWithQuickFixTestSequence(final String s, final String extension) throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(s + "." + extension);
    Collection<HighlightInfo> infos = fixture.doDoTest(true, false);

    fixture.findAndInvokeIntentionAction(infos, "Create Element Declaration aaa");
    fixture.checkResultByFile(s+"_after." + extension);
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(fixture.getProject()).commitDocument(fixture.getEditor().getDocument()));
    infos = fixture.doHighlighting();
    fixture.findAndInvokeIntentionAction(infos, "Create Entity Declaration entity");
    fixture.checkResultByFile(s+"_after2." + extension);
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(fixture.getProject()).commitDocument(fixture.getEditor().getDocument()));
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testDocBookHighlighting() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doManyFilesFromSeparateDirTest("http://www.oasis-open.org/docbook/xml/4.4/docbookx.dtd", "docbookx.dtd", () -> {
      XmlTag rootTag = ((XmlFile)fixture.getFile()).getDocument().getRootTag();
      PsiElement psiElement = rootTag.getReferences()[0].resolve();
      assertTrue(((Navigatable)psiElement).canNavigate());
    });
  }

  @PerformanceUnitTest
  @Test
  public void testBigPrologHighlightingPerformance() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText(XmlFileType.INSTANCE,
                    "<!DOCTYPE rules [\n" +
                    IntStream.range(0, 10000).mapToObj(i -> "<!ENTITY pnct" + i + " \"x\">\n").collect(Collectors.joining()) +
                    "]>\n" +
                    "<rules/>");
    Benchmark
      .newBenchmark("highlighting", () -> fixture.doHighlighting())
      .setup(() -> EdtTestUtil.runInEdtAndWait(() -> PsiManager.getInstance(fixture.getProject()).dropPsiCaches()))
      .runAsStressTest()
      .start();
  }

  @Test
  public void testDocBookHighlighting2() throws Exception {
    doManyFilesFromSeparateDirTest("http://www.oasis-open.org/docbook/xml/4.5/docbookx.dtd", "docbookx.dtd", null);
  }

  @Test
  public void testOasisDocumentHighlighting() throws Exception {
    doManyFilesFromSeparateDirTest("concept.dtd", "concept.dtd", null);
  }

  private void doManyFilesFromSeparateDirTest(final String url, final String mainDtdName, @Nullable Runnable additionalTestAction) throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    List<VirtualFile> files = new ArrayList<>();
    files.add(fixture.findVirtualFile(getFullRelativeTestName()));

    final VirtualFile virtualFile = fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false));
    ContainerUtil.addAll(files, virtualFile.getChildren());

    ExternalResourceManagerExBase
      .registerResourceTemporarily(url, UriUtil.findRelativeFile(mainDtdName, virtualFile).getPath(), fixture.getTestRootDisposable());
    fixture.doTest(VfsUtilCore.toVirtualFileArray(files), true, false);

    if (additionalTestAction != null) ReadAction.run(() -> additionalTestAction.run());

  }

  private void doSchemaTestWithManyFilesFromSeparateDir(final String[][] urls, @Nullable Processor<? super List<VirtualFile>> additionalTestingProcessor) throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    List<VirtualFile> files = new ArrayList<>(6);
    files.add(fixture.findVirtualFile(BASE_PATH + fixture.getTestName(false) + ".xml"));

    final Set<VirtualFile> usedFiles = new HashSet<>();
    final String base = BASE_PATH + fixture.getTestName(false) + "Schemas/";

    for(String[] pair:urls) {
      final String url = pair[0];
      final String filename = pair.length > 1 ? pair[1]:url.substring(url.lastIndexOf('/')+1) + (url.endsWith(".xsd")?"":".xsd");

      final VirtualFile virtualFile = fixture.findVirtualFile(base + filename);
      usedFiles.add(virtualFile);

      if (url != null) ExternalResourceManagerExBase.registerResourceTemporarily(url, virtualFile.getPath(), fixture.getTestRootDisposable());
      files.add( virtualFile );
    }

    for(VirtualFile file: StandardFileSystems.local().findFileByPath(TEST_DATA_PATH + base.substring(0, base.length() - 1)).getChildren()) {
      if (!usedFiles.contains(file)) {
        files.add(file);
      }
    }

    fixture.doTest(VfsUtilCore.toVirtualFileArray(files), true, false);

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
  @Test
  public void testSchemaUpdate() throws IOException {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = fixture.getTestName(false)+".xsd";
    String url = "http://example.org/ns/books/";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    fixture.configureByFiles(null, fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location));

    fixture.doDoTest(true,false);

    Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    final Editor schemaEditor = allEditors[0] == fixture.getEditor() ? allEditors[1]:allEditors[0];
    final String text = schemaEditor.getDocument().getText();
    final String newText = text.replaceAll("xsd", "xs");
    WriteCommandAction.runWriteCommandAction(null, () -> schemaEditor.getDocument().replaceString(0, text.length(), newText));

    fixture.doDoTest(true, false);
  }

  @Test
  public void testXHtmlEditorHighlighting() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    //                       10
    //             0123456789012
    String text = "<html></html>";
    EditorHighlighter xhtmlHighlighter = HighlighterFactory
      .createHighlighter(XHtmlFileType.INSTANCE, EditorColorsManager.getInstance().getGlobalScheme(), fixture.getProject());
    xhtmlHighlighter.setText(text);
    HighlighterIterator iterator = xhtmlHighlighter.createIterator(1);
    assertSame(XmlTokenType.XML_TAG_NAME, iterator.getTokenType(), "Xml tag name");
    iterator = xhtmlHighlighter.createIterator(8);
    assertSame(XmlTokenType.XML_TAG_NAME, iterator.getTokenType(), "Xml tag name at end of tag");

  }

  @Test
  public void testSchemaValidation6() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String location = fixture.getTestName(false)+".xsd";
    String url = "aaa";
    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, fixture.getTestRootDisposable());
    fixture.configureByFiles(null, fixture.findVirtualFile(getFullRelativeTestName()), fixture.findVirtualFile(BASE_PATH + location));

    fixture.doDoTest(true, false);

  }

  @Test
  public void testDTDEditorHighlighting() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    //                       10        20
    //             012345678901234567890 123456 789
    String text = "<!ENTITY % Charsets \"CDATA\">";
    EditorHighlighter dtdHighlighter = HighlighterFactory.createHighlighter(DTDFileType.INSTANCE, EditorColorsManager.getInstance().getGlobalScheme(), fixture.getProject());
    dtdHighlighter.setText(text);
    HighlighterIterator iterator = dtdHighlighter.createIterator(3);

    assertSame(XmlTokenType.XML_ENTITY_DECL_START, iterator.getTokenType(), "Xml entity name");
    iterator = dtdHighlighter.createIterator(13);
    assertSame(XmlTokenType.XML_NAME, iterator.getTokenType(), "Xml name in dtd");
    iterator = dtdHighlighter.createIterator(23);
    assertSame(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, iterator.getTokenType(), "Xml attribute value in dtd");

    //                10        20        30        40
    //      0123456789012345678901 2345678901234567890123456789
    text = "<!ELEMENT base EMPTY>\n<!ATTLIST base id ID #IMPLIED>";
    dtdHighlighter.setText(text);
    iterator = dtdHighlighter.createIterator(3);
    assertSame(XmlTokenType.XML_ELEMENT_DECL_START, iterator.getTokenType(), "Xml element name");
    iterator = dtdHighlighter.createIterator(25);
    assertSame(XmlTokenType.XML_ATTLIST_DECL_START, iterator.getTokenType(), "Xml attr list");

    iterator = dtdHighlighter.createIterator(14);
    assertSame(TokenType.WHITE_SPACE, iterator.getTokenType(), "Xml attr list");

    iterator = dtdHighlighter.createIterator(21);
    assertSame(TokenType.WHITE_SPACE, iterator.getTokenType(), "Xml attr list");

    //                10        20        30        40        50        60
    //      0123456789012345678901234567890123456789012345678901234567890123456789
    text = "<![%sgml;[<![IGNORE[<![ INCLUDE [<!ENTITY % aaa SYSTEM 'zzz'>]]>]]>]]>";

    dtdHighlighter.setText(text);
    iterator = dtdHighlighter.createIterator(2);
    assertEquals(XmlTokenType.XML_CONDITIONAL_SECTION_START, iterator.getTokenType(), "Xml conditional section start");

    iterator = dtdHighlighter.createIterator(67);
    assertEquals(XmlTokenType.XML_CONDITIONAL_SECTION_END, iterator.getTokenType(), "Xml conditional section end");

    iterator = dtdHighlighter.createIterator(5);
    assertEquals(XmlTokenType.XML_ENTITY_REF_TOKEN, iterator.getTokenType(), "entity ref in conditional section");

    iterator = dtdHighlighter.createIterator(15);
    assertEquals(XmlTokenType.XML_CONDITIONAL_IGNORE, iterator.getTokenType(), "ignore in conditional section");

    iterator = dtdHighlighter.createIterator(27);
    assertEquals(XmlTokenType.XML_CONDITIONAL_INCLUDE, iterator.getTokenType(), "include in conditional section");

    iterator = dtdHighlighter.createIterator(9);
    assertEquals(XmlTokenType.XML_MARKUP_START, iterator.getTokenType(), "markup start in conditional section");

    iterator = dtdHighlighter.createIterator(33);
    assertEquals(XmlTokenType.XML_ENTITY_DECL_START, iterator.getTokenType(), "entity decl start in conditional section");

    //                10        20          30         40        50        60         70
    //      012345678901234567890123 456789 0 123456789012345678901234567890123456 7890123456789
    text = "<!ENTITY % ContentType \"CDATA\"\n    -- media type, as per [RFC2045]\n    -- --xxx-->";
    dtdHighlighter.setText(text);
    iterator = dtdHighlighter.createIterator(35);
    assertEquals(XmlTokenType.XML_COMMENT_START, iterator.getTokenType(), "Dtd comment start");
    iterator = dtdHighlighter.createIterator(40);
    assertEquals(XmlTokenType.XML_COMMENT_CHARACTERS, iterator.getTokenType(), "Dtd comment content");
    iterator = dtdHighlighter.createIterator(71);
    assertEquals(XmlTokenType.XML_COMMENT_END, iterator.getTokenType(), "Dtd comment end");

    iterator = dtdHighlighter.createIterator(78);
    assertEquals(XmlTokenType.XML_COMMENT_CHARACTERS, iterator.getTokenType(), "Dtd comment content");
  }

  @Test
  public void testSgmlDTD() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(getFullRelativeTestName(".dtd"), true, false);
  }

  @Test
  public void testImportProblems() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    fixture.configureByFiles(
      null,
      BASE_PATH + testName +".xsd",
      BASE_PATH +testName +"2.xsd"
    );
    fixture.doDoTest(true, false, true);
  }

  @Test
  public void testEncoding() throws Exception { doTest(true); }

  @Test
  public void testSchemaImportHighlightingAndResolve() {
    doTestWithLocations(new String[][]{{"http://www.springframework.org/schema/beans", "ComplexSchemaValidation11.xsd"}}, "xsd");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testCorrectGeneratedDtdUpdate() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + fixture.getTestName(false) + ".xml");
    Collection<HighlightInfo> infos = filterInfos(fixture.doHighlighting());
    assertEquals(2, infos.size());

    WriteCommandAction.runWriteCommandAction(null, () -> EditorModificationUtil.deleteSelectedText(fixture.getEditor()));

    infos = filterInfos(fixture.doHighlighting());

    assertEquals(11, infos.size());

    WriteCommandAction.runWriteCommandAction(null, () -> EditorModificationUtil.insertStringAtCaret(fixture.getEditor(), "<"));

    EdtTestUtil.runInEdtAndWait(
      () -> new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(fixture.getProject(), fixture.getEditor()));
    infos = filterInfos(fixture.doHighlighting());
    assertEquals(2, infos.size());
    EdtTestUtil.runInEdtAndWait(() -> LookupManager.getInstance(fixture.getProject()).hideActiveLookup());
  }

  private static Collection<HighlightInfo> filterInfos(final Collection<HighlightInfo> highlightInfos) {
    highlightInfos.removeIf(highlightInfo -> highlightInfo.getSeverity() == HighlightSeverity.INFORMATION);
    return highlightInfos;
  }

  @Test
  public void testUsingSchemaDtd() {
    doTestWithLocations(null, "xsd");
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testDtdElementRefs() {
    doTestWithLocations(
      new String[] [] {
        {"http://example.com/persistence","XIncludeTestSchema.xsd"}
      },
      "xml"
    );
  }

  @Test
  public void testDuplicates() {
    doTestWithLocations(null, "xsd");
    doTestWithLocations(null, "dtd");
    doTestWithLocations(null, "xml");
  }

  @Test
  public void testMinMaxOccursInSchema() {
    doTestWithLocations(null, "xsd");
  }

  @Test
  public void testXslt2() {
    doTestWithLocations(null, "xsl");
  }

  @Test
  public void testDefaultAndFixedInSchema() {
    doTestWithLocations(null, "xsd");
  }

  @Test
  public void testAnyAttributesInAttrGroup() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTestWithLocations(
      new String[] [] {
        {"http://www.foo.org/test",fixture.getTestName(false)+".xsd"},
        {"http://www.bar.org/test",fixture.getTestName(false)+"_2.xsd"}
      },
      "xml"
    );
  }

  @Test
  public void testXInclude() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    fixture.configureByFiles(
      null,
      BASE_PATH + testName +".xml",
      BASE_PATH +testName +"-inc.xml",
      BASE_PATH +testName +"TestSchema.xsd"
    );

    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList("oxf:/apps/somefile.xml"), fixture.getTestRootDisposable());
    fixture.doDoTest(true, false, true);

    VirtualFile[] includedFiles = ReadAction.computeBlocking(
      () -> FileIncludeManager.getManager(fixture.getProject()).getIncludedFiles(fixture.getFile().getVirtualFile(), true));
    assertEquals(1, includedFiles.length);
  }

  @Test
  public void testComplexRedefine() throws Exception {
    final String[][] urls = {
      {"http://graphml.graphdrawing.org/xmlns","graphml.xsd"},
      {"http://graphml.graphdrawing.org/xmlns/1.0/graphml-structure.xsd","graphml-structure.xsd"},
      {"http://www.w3.org/1999/xlink","xlink.xsd"}
    };
    doSchemaTestWithManyFilesFromSeparateDir(urls, null);
  }

  @Test
  public void testComplexRedefine2() throws Exception {
    final String[][] urls = {
      {"http://www.yworks.com/xml/schema/graphml/1.0/ygraphml.xsd","ygraphml.xsd"},
      {"http://graphml.graphdrawing.org/xmlns/1.0/xlink.xsd","xlink.xsd"}
    };
    doSchemaTestWithManyFilesFromSeparateDir(urls, null);
  }

  @Test
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

  @Test
  public void testMuleConfigHighlighting2() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    String path = fixture.getTestName(false) + File.separatorChar;
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

  @Test
  public void testComplexRedefine3() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);

    VirtualFile[] files = {fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd"), fixture.findVirtualFile(BASE_PATH + testName + "_3.xsd")};
    fixture.doTest(files, true, false);

    files = new VirtualFile[] {fixture.findVirtualFile(BASE_PATH + testName + ".xsd"),
      fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd"), fixture.findVirtualFile(BASE_PATH + testName + "_3.xsd")};
    fixture.doTest(files, true, false);
  }

  @Test
  public void testComplexRedefine4() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    VirtualFile[] files = {fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
      fixture.findVirtualFile(BASE_PATH + testName + ".xsd"), fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd")};
    fixture.doTest(files, true, false);
  }

  @Test
  public void testComplexRedefine5() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    String[][] urls = {
      {"http://extended", testName + ".xsd"},
      {"http://simple", testName + "_2.xsd"}
    };
    doTestWithLocations(urls, "xml");
  }

  @Test
  public void testComplexRedefine6() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    String[][] urls = {
      {"urn:jboss:bean-deployer:2.0", testName + ".xsd"},
      {"", testName + "_2.xsd"}
    };
    doTestWithLocations(urls,"xml");
  }

  @Test
  public void testRedefineQualifiedType() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    VirtualFile[] files = {
      fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
      fixture.findVirtualFile(BASE_PATH + testName + ".xsd"),
      fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd")
    };
    fixture.doTest(files, true, false);
  }

  @Test
  public void testRedefineBaseType() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    VirtualFile[] files = {
      fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
      fixture.findVirtualFile(BASE_PATH + testName + ".xsd"),
      fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd")
    };
    fixture.doTest(files, true, false);
  }

  @Test
  public void testComplexSchemaValidation18() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
        fixture.findVirtualFile(BASE_PATH + testName + ".xsd"),
        fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testComplexSchemaValidation19() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
        fixture.findVirtualFile(BASE_PATH + testName + ".xsd"),
        fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testComplexSchemaValidation20() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
        fixture.findVirtualFile(BASE_PATH + testName + ".xsd"),
        fixture.findVirtualFile(BASE_PATH + testName + "_2.xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testSubstitutionFromInclude() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    ExternalResourceManagerExBase
      .registerResourceTemporarily("http://www.omg.org/spec/BPMN/20100524/MODEL", "BPMN20.xsd", fixture.getTestRootDisposable());

    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "FinancialReportProcess.bpmn20.xml"),
        fixture.findVirtualFile(BASE_PATH + "BPMN20.xsd"),
        fixture.findVirtualFile(BASE_PATH + "Semantic.xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testComplexRedefineFromJar() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null,BASE_PATH + fixture.getTestName(false) + ".xml", BASE_PATH + "mylib.jar");
    String path = fixture.getFile().getVirtualFile().getParent().getPath() + "/";
    String[][] urls = {
      {"http://graphml.graphdrawing.org/xmlns",path + "mylib.jar!/graphml.xsd"},
      {"http://graphml.graphdrawing.org/xmlns/1.0/graphml-structure.xsd",path + "mylib.jar!/graphml-structure.xsd"},
      {"http://www.w3.org/1999/xlink",path + "mylib.jar!/xlink.xsd"}
    };

    for(String[] s:urls) ExternalResourceManagerExBase.registerResourceTemporarily(s[0], s[1], fixture.getTestRootDisposable());
    fixture.doDoTest(true, false);
  }

  @Test
  public void testChangeRootElement() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTool(new XmlWrongRootElementInspection());
    final String testName = fixture.getTestName(false);
    fixture.configureByFile(BASE_PATH + testName + ".xml");
    Collection<HighlightInfo> infos = fixture.doDoTest(true, false);

    fixture.findAndInvokeIntentionAction(infos, "Change root tag name to xxx");
    fixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testUnqualifiedAttributePsi() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    doTestWithLocations(null, "xml");
    ReadAction.run(() -> {
      final List<XmlAttribute> attrs = new ArrayList<>(2);

      fixture.getFile().acceptChildren(new XmlRecursiveElementVisitor() {
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
    });
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
  @Test
  public void testHighlightWhenNoNsSchemaLocation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);

    fixture.doTest(
      new VirtualFile[]{
        fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
        fixture.findVirtualFile(BASE_PATH + testName + ".xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testSchemaAutodetection() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "SchemaAutodetection/policy.xml"),
        fixture.findVirtualFile(BASE_PATH + "SchemaAutodetection/cs-xacml-schema-policy-01.xsd"),
        fixture.findVirtualFile(BASE_PATH + "SchemaAutodetection/cs-xacml-schema-context-01.xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testDtdAutodetection() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "nuancevoicexml-2-0.xml"),
        fixture.findVirtualFile(BASE_PATH + "nuancevoicexml-2-0.dtd")
      },
      true,
      false
    );
  }

  @Test
  public void testSchemaWithXmlId() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);

    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + testName + ".xml")
      },
      true,
      false
    );
  }

  @Test
  public void testProblemWithImportedNsReference() {
    doTestWithLocations(null, "xsd");
  }

  @Test
  public void testBadXmlns() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + "badXmlns.xml");
    fixture.doHighlighting();
  }

  @Test
  public void testProblemWithMemberTypes() {
    doTestWithLocations(null, "xsd");
  }

  @Test
  public void testDtdHighlighting() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);

    doTestWithLocations(
      new String[][] {
        {"http://www.w3.org/TR/xhtml-modularization/DTD/xhtml-inlstyle-1.mod",testName + ".mod"}
      },
      "dtd"
    );
  }

  @Test
  public void testNoNamespaceLocation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final String testName = fixture.getTestName(false);

    fixture.doTest(
      new VirtualFile[]{
        fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
        fixture.findVirtualFile(BASE_PATH + testName + ".xsd")
      },
      true,
      false
    );
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testUnresolvedSymbolForForAttribute() throws Exception {
    doTest();
  }

  @Test
  public void testXsiType() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    RecursionManager.assertOnRecursionPrevention(fixture.getTestRootDisposable());
    final String testName = fixture.getTestName(false);

    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + testName + ".xml"),
        fixture.findVirtualFile(BASE_PATH + testName + "_Types.xsd"),
        fixture.findVirtualFile(BASE_PATH + testName + "_Request.xsd"),
        fixture.findVirtualFile(BASE_PATH + testName + "_Generic.xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testMappedSchemaLocation() {
    doTestWithLocations(new String[][]{
      {"schemas/Forms.xsd", "Forms.xsd"}
    }, "xml");
  }

  @Test
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

  @Test
  public void testMobileHtml() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTool(new HtmlUnknownTagInspection());
    fixture.doTest(getFullRelativeTestName(".html"), true, true);
  }

  @Test
  public void testSvgInHtml() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTools(new HtmlUnknownTagInspection(),
                          new HtmlUnknownAttributeInspection(),
                          new HtmlUnknownBooleanAttributeInspection());
    fixture.doTest(getFullRelativeTestName(".html"), true, true);
  }

  @Test
  public void testSvgAttrValueInHtml() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTools(new HtmlWrongAttributeValueInspection());
    fixture.doTest(getFullRelativeTestName(".html"), true, false);
  }

  @Test
  public void testAnyAttribute() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "anyAttribute.xml", BASE_PATH + "services-1.0.xsd");
    fixture.doDoTest(true, false);
  }

  @Test
  public void testAnyAttributeDefaultNamespace() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "UnityEngine.xml", BASE_PATH + "UnityEngine.UIElements.xsd");
    fixture.doDoTest(true, false);
  }

  @Test
  public void testSubstitution() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(new VirtualFile[]{
      fixture.findVirtualFile(BASE_PATH + "Substitute/test.xml"),
      fixture.findVirtualFile(BASE_PATH + "Substitute/schema-b.xsd"),
      fixture.findVirtualFile(BASE_PATH + "Substitute/schema-a.xsd")
    }, true, false);
  }

  @Test
  public void testPrefixedSubstitution() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(new VirtualFile[]{
      fixture.findVirtualFile(BASE_PATH + "Substitute/prefixed.xml"),
      fixture.findVirtualFile(BASE_PATH + "Substitute/schema-b.xsd"),
      fixture.findVirtualFile(BASE_PATH + "Substitute/schema-a.xsd")
    }, true, false);
  }

  @Test
  public void testSubstitutionFromImport() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(new VirtualFile[]{
      fixture.findVirtualFile(BASE_PATH + "SubstitutionGroup/problem-with-substitution-groups.xml"),
      fixture.findVirtualFile(BASE_PATH + "SubstitutionGroup/munit-runner.xsd"),
      fixture.findVirtualFile(BASE_PATH + "SubstitutionGroup/mule.xsd")
    }, true, false);
  }

  @Test
  public void testDtdWithXsd() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "DtdWithXsd/help.xml"),
        fixture.findVirtualFile(BASE_PATH + "DtdWithXsd/helptopic.xsd"),
        fixture.findVirtualFile(BASE_PATH + "DtdWithXsd/html-entities.dtd")
      },
      true,
      false
    );
  }

  @Test
  public void testAnyAttributeNavigation() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xml"),
                     fixture.findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xsd"),
                     fixture.findVirtualFile(BASE_PATH + "AnyAttributeNavigation/library.xsd"));

    PsiReference at = ReadAction.computeBlocking(() -> {
      PsiReference ref = fixture.getFile().findReferenceAt(fixture.getEditor().getCaretModel().getOffset());

      XmlTag tag = PsiTreeUtil.getParentOfType(ref.getElement(), XmlTag.class);
      XmlElementDescriptorImpl descriptor = (XmlElementDescriptorImpl)tag.getDescriptor();
      XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
      LOG.debug(String.valueOf(Arrays.asList(descriptors)));
      return ref;
    });

    fixture.doDoTest(true, false);

    PsiElement resolve = ReadAction.computeBlocking(() -> at.resolve());
    assertTrue(resolve instanceof XmlTag);
  }

  @Test
  public void testDropAnyAttributeCacheOnExitFromDumbMode() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    DumbModeTestUtils.runInDumbModeSynchronously(fixture.getProject(), () -> {
      fixture.configureByFiles(null, fixture.findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xml"),
                       fixture.findVirtualFile(BASE_PATH + "AnyAttributeNavigation/test.xsd"),
                       fixture.findVirtualFile(BASE_PATH + "AnyAttributeNavigation/library.xsd"));
      ReadAction.run(() -> {
        PsiReference at = fixture.getFile().findReferenceAt(fixture.getEditor().getCaretModel().getOffset());

        XmlTag tag = PsiTreeUtil.getParentOfType(at.getElement(), XmlTag.class);
        XmlElementDescriptor descriptor = tag.getDescriptor();
        XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
        LOG.debug(String.valueOf(Arrays.asList(descriptors)));
      });
    });

    fixture.doDoTest(true, false);
  }

  @Test
  public void testQualifiedAttributeReference() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "qualified.xml", BASE_PATH + "qualified.xsd");
    fixture.doDoTest(true, false);
  }

  @Test
  public void testUnqualifiedElement() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "UnqualifiedElement.xml", BASE_PATH + "UnqualifiedElement.xsd");
    fixture.doDoTest(true, false);
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testEnumeratedBoolean() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "EnumeratedBoolean.xml", BASE_PATH + "EnumeratedBoolean.xsd");
    fixture.doDoTest(true, false);
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testEnumeratedList() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "servers.xml", BASE_PATH + "servers.xsd");
    fixture.doDoTest(true, false);
  }

  @HighlightingFlags(HighlightingFlag.SkipExternalValidation)
  @Test
  public void testEnumeratedExtension() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "enumerations.xml", BASE_PATH + "enumerations.xsd");
    fixture.doDoTest(true, false);
  }

  @Test
  public void testCustomBoolean() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "CustomBoolean.xml", BASE_PATH + "CustomBoolean.xsd");
    fixture.doDoTest(true, false);
  }

  @Test
  public void testStackOverflowInSchema() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "XMLSchema_1_1.xsd");
    fixture.doHighlighting();
  }

  @Test
  public void testSchemaVersioning() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "Versioning.xsd");
    fixture.doDoTest(true, false);
  }

  @Test
  public void testLinksInAttrValuesAndComments() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + fixture.getTestName(false) + ".xml");
    fixture.doDoTest(true, false);

    ReadAction.run(() -> {
      List<? extends PsiSymbolReference> list = Registry.is("ide.symbol.url.references")
                                                ? PlatformTestUtil.collectUrlReferences(fixture.getFile())
                                                : ContainerUtil.map(
                                                  PlatformTestUtil.collectWebReferences(fixture.getFile()),
                                                  PsiSymbolService.getInstance()::asSymbolReference
                                                );
      assertEquals(2, list.size());

      list = ContainerUtil.sorted(list, Comparator.comparingInt(o -> o.getRangeInElement().getLength()));

      assertEquals("https://www.jetbrains.com/ruby/download", getReferenceText(list.get(0)));
      assertTrue(list.get(0).getElement() instanceof XmlAttributeValue);
      assertEquals("http://blog.jetbrains.com/ruby/2012/04/rubymine-4-0-3-update-is-available/", getReferenceText(list.get(1)));
      assertTrue(list.get(1).getElement() instanceof XmlComment);
    });
  }

  @Test
  public void testBillionLaughs() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "BillionLaughs.xml");
    XmlFile file = (XmlFile)fixture.getFile();
    int[] count = {0};
    ReadAction.run(() -> XmlUtil.processXmlElements(file.getRootTag(), element -> {
      count[0]++;
      return true;}, false));
    assertEquals(9, count[0]);
  }

  @Test
  public void testBillionLaughsValidation() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    Locale locale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.ENGLISH);
      fixture.configureByFiles(null, BASE_PATH + "BillionLaughs.xml");
      fixture.doDoTest(false, false);
    }
    finally {
      Locale.setDefault(locale);
    }
  }

  @Test
  public void testMaxOccurLimitValidation() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "MaxOccurLimit.xml", BASE_PATH + "MaxOccurLimit.xsd");
    assertTrue(fixture.doHighlighting().stream().anyMatch(info -> info.getSeverity() == HighlightSeverity.ERROR));

    fixture.configureByFiles(null, BASE_PATH + "MaxOccurLimit.xml", BASE_PATH + "MaxOccurLimit.xsd");
    System.setProperty(ValidateXmlActionHandler.JDK_XML_MAX_OCCUR_LIMIT, "10000");
    assertFalse(fixture.doHighlighting().stream().anyMatch(info -> info.getSeverity() == HighlightSeverity.ERROR));
  }

  @Test
  public void testTheSameElement() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "TheSameElement/IntelliJPersonData.xml"),
        fixture.findVirtualFile(BASE_PATH + "TheSameElement/IntellijCalTech.xsd"),
        fixture.findVirtualFile(BASE_PATH + "TheSameElement/IntelliJMeldeamt.xsd")
      },
      true,
      false
    );
  }

  @Test
  public void testTheSameTypeName() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "TheSameTypeName/test2.xml"),
        fixture.findVirtualFile(BASE_PATH + "TheSameTypeName/test-common-xsd1.xsd"),
        fixture.findVirtualFile(BASE_PATH + "TheSameTypeName/test-common-xsd2.xsd"),
        fixture.findVirtualFile(BASE_PATH + "TheSameTypeName/test-xsd1.xsd"),
        fixture.findVirtualFile(BASE_PATH + "TheSameTypeName/test-xsd2.xsd"),
      },
      true,
      false
    );
  }

  @Test
  public void testRedefine() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    RecursionManager.assertOnRecursionPrevention(fixture.getTestRootDisposable());
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "Redefine/derived.xsd"),
        fixture.findVirtualFile(BASE_PATH + "Redefine/base.xsd"),
      },
      true, false
    );
  }

  @Test
  public void testRedefine2() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "Redefine/sample.xml"),
        fixture.findVirtualFile(BASE_PATH + "Redefine/derived.xsd"),
        fixture.findVirtualFile(BASE_PATH + "Redefine/base.xsd"),
      },
      true, false
    );
  }

  @Test
  public void testRedefineGroup() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "RedefineGroup/test.xml"),
        fixture.findVirtualFile(BASE_PATH + "RedefineGroup/originalschema.xsd"),
        fixture.findVirtualFile(BASE_PATH + "RedefineGroup/redefinedschema.xsd"),
      },
      true, false
    );
  }

  @Test
  public void testMultipleImports() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.doTest(
      new VirtualFile[] {
        fixture.findVirtualFile(BASE_PATH + "MultipleImports/agg.xsd"),
        fixture.findVirtualFile(BASE_PATH + "MultipleImports/toimport1.xsd"),
        fixture.findVirtualFile(BASE_PATH + "MultipleImports/toimport2.xsd"),
      },
      true, false
    );
  }

  @Test
  public void testImportedAttr() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "ImportedAttr/main.xml",
                     BASE_PATH + "ImportedAttr/main.xsd",
                     BASE_PATH + "ImportedAttr/include.xsd");
    fixture.doHighlighting();
  }

  @Test
  public void testXhtml() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, BASE_PATH + "test.xhtml");
    fixture.doDoTest(true, false);
  }

  @BeforeEach
  void setUp() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.setExternalValidation(!methodOfTestHasAnnotation(XmlHighlightingTest.class, fixture.getTestName(false), HighlightingFlag.SkipExternalValidation));
    fixture.setClearModelBeforeConfiguring("ComplexRedefine3".equals(fixture.getTestName(false)));
    if (!fixture.getTestName(false).equals("DoctypeWithoutSchema2")) fixture.enableInspectionTool(new CheckXmlFileWithXercesValidatorInspection());

    old = XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS;
    XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS = false;
    ExternalResourceManagerExBase.registerResourceTemporarily("http://www.springframework.org/schema/beans/spring-beans-2.5.xsd",
                                                              TEST_DATA_PATH + BASE_PATH + "spring-beans-2.5.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",
                                                              TEST_DATA_PATH + BASE_PATH + "web-app_2_4.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",
                                                              TEST_DATA_PATH + BASE_PATH + "web-app_2_4.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/dtd/web-app_2_3.dtd",
                                                              TEST_DATA_PATH + BASE_PATH + "web-app_2_3.dtd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://struts.apache.org/dtds/struts-config_1_2.dtd",
                                                              TEST_DATA_PATH + BASE_PATH + "struts-config_1_2.dtd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/dtd/ejb-jar_2_0.dtd",
                                                              TEST_DATA_PATH + BASE_PATH + "ejb-jar_2_0.dtd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/",
                                                              TEST_DATA_PATH + BASE_PATH + "wsdl11.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/soap/",
                                                              TEST_DATA_PATH + BASE_PATH + "wsdl11_soapbinding.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://schemas.xmlsoap.org/soap/encoding/",
                                                              TEST_DATA_PATH + BASE_PATH + "soap-encoding.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/application-client_1_4.xsd",
                                                              TEST_DATA_PATH + BASE_PATH + "application-client_1_4.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd",
                                                              TEST_DATA_PATH + BASE_PATH + "hibernate-mapping-3.0.dtd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://maven.apache.org/maven-v4_0_0.xsd",
                                                              TEST_DATA_PATH + BASE_PATH + "maven-4.0.0.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd",
                                                              TEST_DATA_PATH + BASE_PATH + "web-jsptaglibrary_1_2.dtd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/JSP/Page",
                                                              TEST_DATA_PATH + BASE_PATH + "jsp_2_0.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://java.sun.com/xml/ns/j2ee/web-jsptaglibrary_2_0.xsd",
                                                              TEST_DATA_PATH + BASE_PATH + "web-jsptaglibrary_2_0.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://hibernate.sourceforge.net/hibernate-configuration-3.0.dtd",
                                                              TEST_DATA_PATH + BASE_PATH + "hibernate-configuration-3.0.dtd",
                                                              fixture.getTestRootDisposable());
  }

  private void disableHtmlSupport() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(fixture.getProject());
    manager.setDefaultHtmlDoctype("fake", fixture.getProject());
  }

  private void enableHtmlSupport() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, fixture.getProject());
  }

  @AfterEach
  void tearDown() {
    XmlSettings.getInstance().SHOW_XML_ADD_IMPORT_HINTS = old;
  }
}
