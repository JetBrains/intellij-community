/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger;


import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiModifier
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.wrq.rearranger.settings.CommentRule
import com.wrq.rearranger.settings.RearrangerSettings
import com.wrq.rearranger.util.CommentRuleBuilder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import com.wrq.rearranger.util.java.*

/** JUnit tests for the rearranger plugin. */
class RearrangerTest extends LightCodeInsightFixtureTestCase {

  private RearrangerSettings    mySettings
  private JavaClassRuleBuilder  classRule
  private JavaFieldRuleBuilder  fieldRule
  private JavaMethodRuleBuilder methodRule
  private CommentRuleBuilder    commentRule

  @Override
  protected String getBasePath() {
    return "/plugins/rearranger/test/testData/com/wrq/rearranger";
  }

  protected final void setUp() throws Exception {
    super.setUp();
    
    mySettings = new RearrangerSettings();
//        rs.setAskBeforeRearranging(true); // uncomment for debugging file structure popup
    mySettings.showFields = true
    mySettings.showParameterNames = true
    mySettings.showParameterTypes = true
    mySettings.showRules = true
    mySettings.rearrangeInnerClasses = true

    classRule = new JavaClassRuleBuilder(settings: mySettings)
    fieldRule = new JavaFieldRuleBuilder(settings: mySettings)
    methodRule = new JavaMethodRuleBuilder(settings: mySettings)
    commentRule = new CommentRuleBuilder(settings: mySettings)
  }

  public final void testNoRearrangement() throws Exception {
    doTest('RearrangementTest', 'NoRearrangementResult1')
  }

  public final void testPublicFieldRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult2') {
      fieldRule.create {
        modifier( PsiModifier.PUBLIC )
  } } }

  public final void testNotPublicFieldRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult3') {
      fieldRule.create {
        modifier( PsiModifier.PUBLIC, invert: true )
  } } }

  public final void testConstructorRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult4') {
      methodRule.create {
        modifier([ PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL ])
        target( MethodType.CONSTRUCTOR )
  } } }

  public final void testClassRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult5') {
      classRule.create {
        modifier( PsiModifier.PACKAGE_LOCAL )
  } } }

  public final void testPSFRearrangement() throws Exception {
    doTest('RearrangementTest2', 'RearrangementResult6') {
      fieldRule.create {
        modifier([ PsiModifier.FINAL, PsiModifier.STATIC ])
  } } }

  public final void testAnonClassInit() throws Exception {
    doTest('RearrangementTest7', 'RearrangementResult7') {
      fieldRule.create {
        initializer( InitializerType.ANONYMOUS_CLASS )
  } } }

  public final void testNameMatch() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult8') {
      fieldRule.create { name('.*5') }
      methodRule.create { name('.*2') }
  } }

  public final void testStaticInitializer() throws Exception {
    doTest('RearrangementTest8', 'RearrangementResult8A') {
      methodRule.create { modifier( PsiModifier.STATIC ) }
  } }

  public final void testAlphabetizingGSMethods() throws Exception {
    mySettings.keepGettersSettersTogether = false
    doTest('RearrangementTest', 'RearrangementResult9') {
      methodRule.create {
        target([ MethodType.GETTER_OR_SETTER, MethodType.OTHER ])
        sort(SortType.BY_NAME)
  } } }

  public final void testSimpleComment() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult10') {
      fieldRule.create { modifier( PsiModifier.PUBLIC) }
      commentRule.create {
        comment( '// simple comment **********', condition: CommentRule.EMIT_IF_ITEMS_MATCH_PRECEDING_RULE )
  } } }

  /**
   * Delete old comment and insert (identical) new one.  This tests proper identification and deletion of old
   * comments.
   *
   * @throws Exception test exception
   */
  public final void testReplayComment() throws Exception {
    doTest('RearrangementResult10', 'RearrangementResult10') {
      fieldRule.create { modifier( PsiModifier.PUBLIC) }
      commentRule.create {
        comment( '// simple comment **********', condition: CommentRule.EMIT_IF_ITEMS_MATCH_PRECEDING_RULE )
  } } }

  public final void testMultipleRuleCommentMatch() throws Exception {
    doTest('RearrangementTest11', 'RearrangementResult11') {
      commentRule.create { comment('// FIELDS:', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, allSubsequent: false) }
      commentRule.create { comment('// FINAL FIELDS:', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, allSubsequent: false,
                                   subsequentRulesToMatch: 1) }
      fieldRule.create { modifier(PsiModifier.FINAL) }
      commentRule.create { comment('// NON-FINAL FIELDS:', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, allSubsequent: true,
                                   subsequentRulesToMatch: 1) }
      fieldRule.create { modifier(PsiModifier.FINAL, invert: true) }
  } }

//  public final void testOpsBlockingQueueExample() throws Exception {
//    testOpsBlockingQueueExampleWorker(false, "/com/wrq/rearranger/OpsBlockingQueue.java",
//                                      false, "/com/wrq/rearranger/OpsBlockingQueue.java");
//  }
//
//  public final void testOpsBlockingQueueExampleWithGlobalPattern() throws Exception {
//    testOpsBlockingQueueExampleWorker(true, "/com/wrq/rearranger/OpsBlockingQueue.java",
//                                      false, "/com/wrq/rearranger/OpsBlockingQueue.java");
//  }
//
//  public final void testOpsBlockingQueueExampleWithIndentedComments() throws Exception {
//    testOpsBlockingQueueExampleWorker(false, "/com/wrq/rearranger/OpsBlockingQueueIndented.java",
//                                      true, "/com/wrq/rearranger/OpsBlockingQueueIndentedResult.java");
//  }
//
//  private void testOpsBlockingQueueExampleWorker(boolean doGlobalPattern,
//                                                 String srcFilename,
//                                                 boolean doublePublicMethods,
//                                                 String compareFilename)
//    throws Exception
//  {
//    // submitted by Joe Martinez.
//    configureByFile(srcFilename);
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    CommentRule c;
//    FieldAttributes fa;
//    c = new CommentRule();
//    c.setCommentText("//**************************************        PUBLIC STATIC FIELDS         *************************************");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(2);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(false);
//    rs.addItem(c, 0);
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    fa.getPlAttr().setPlPublic(true);
//    fa.getStAttr().setValue(true);
//    fa.getfAttr().setValue(true);
//    rs.addItem(fa, 1);
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    fa.getPlAttr().setPlPublic(true);
//    fa.getStAttr().setValue(true);
//    rs.addItem(fa, 2);
//    c = new CommentRule();
//    c.setCommentText("//**************************************        PUBLIC FIELDS          *****************************************");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(1);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(true);
//    rs.addItem(c, 3);
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    fa.getPlAttr().setPlPublic(true);
//    rs.addItem(fa, 4);
//    c = new CommentRule();
//    c.setCommentText("//***********************************       PROTECTED/PACKAGE FIELDS        **************************************");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(3);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(false);
//    rs.addItem(c, 5);
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    fa.getPlAttr().setPlProtected(true);
//    fa.getPlAttr().setPlPackage(true);
//    fa.getStAttr().setValue(true);
//    fa.getfAttr().setValue(true);
//    rs.addItem(fa, 6);
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    fa.getPlAttr().setPlProtected(true);
//    fa.getPlAttr().setPlPackage(true);
//    fa.getStAttr().setValue(true);
//    rs.addItem(fa, 7);
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    fa.getPlAttr().setPlProtected(true);
//    fa.getPlAttr().setPlPackage(true);
//    rs.addItem(fa, 8);
//    c = new CommentRule();
//    c.setCommentText("//**************************************        PRIVATE FIELDS          *****************************************");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(1);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(false);
//    rs.addItem(c, 9);
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    fa.getPlAttr().setPlPrivate(true);
//    rs.addItem(fa, 10);
//    c = new CommentRule();
//    c.setCommentText("//**************************************        CONSTRUCTORS              ************************************* ");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(2);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(false);
//    rs.addItem(c, 11);
//    MethodAttributes ma = new MethodAttributes();
//    ma.getPlAttr().setPlPublic(true);
//    ma.setConstructorMethodType(true);
//    rs.addItem(ma, 12);
//    ma = new MethodAttributes();
//    ma.setConstructorMethodType(true);
//    rs.addItem(ma, 13);
//    c = new CommentRule();
//    c.setCommentText("//***********************************        GETTERS AND SETTERS              ********************************** ");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(2);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(false);
//    rs.addItem(c, 14);
//    ma = new MethodAttributes();
//    ma.getSortAttr().setByName(true);
//    ma.getPlAttr().setPlPublic(true);
//    ma.setGetterSetterMethodType(true);
//    rs.addItem(ma, 15);
//    ma = new MethodAttributes();
//    ma.getSortAttr().setByName(true);
//    ma.setGetterSetterMethodType(true);
//    rs.addItem(ma, 16);
//    c = new CommentRule();
//    String commentText =
//      "//**************************************        PUBLIC METHODS              ************************************* ";
//    if (doublePublicMethods) {
//      commentText += "\n// PUBLIC METHODS LINE 2";
//    }
//    c.setCommentText(commentText);
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(1);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(true);
//    rs.addItem(c, 17);
//    ma = new MethodAttributes();
//    ma.getSortAttr().setByName(true);
//    ma.getPlAttr().setPlPublic(true);
//    rs.addItem(ma, 18);
//    c = new CommentRule();
//    c.setCommentText("//*********************************     PACKAGE/PROTECTED METHODS              ******************************** ");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(1);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(true);
//    rs.addItem(c, 19);
//    ma = new MethodAttributes();
//    ma.getSortAttr().setByName(true);
//    ma.getPlAttr().setPlProtected(true);
//    ma.getPlAttr().setPlPackage(true);
//    rs.addItem(ma, 20);
//    c = new CommentRule();
//    c.setCommentText("//**************************************        PRIVATE METHODS              *************************************");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(1);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(true);
//    rs.addItem(c, 21);
//    ma = new MethodAttributes();
//    ma.getSortAttr().setByName(true);
//    ma.getPlAttr().setPlPrivate(true);
//    rs.addItem(ma, 22);
//    c = new CommentRule();
//    c.setCommentText("//**************************************        INNER CLASSES              ************************************* ");
//    c.setEmitCondition(2);
//    c.setnPrecedingRulesToMatch(1);
//    c.setnSubsequentRulesToMatch(1);
//    c.setAllPrecedingRules(true);
//    c.setAllSubsequentRules(true);
//    rs.addItem(c, 23);
//    InnerClassAttributes ic = new InnerClassAttributes();
//    ic.getSortAttr().setByName(true);
//    rs.addItem(ic, 24);
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(false);
//    if (doGlobalPattern) {
//      rs.setGlobalCommentPattern("//\\*{20,45}[A-Z /]*\\*{20,45}\n");
//    }
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile(compareFilename);
//  }
//
//  public final void testReturnTypeMatch() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest12.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    MethodAttributes ma;
//    ma = new MethodAttributes();
//    ma.getReturnTypeAttr().setMatch(true);
//    ma.getReturnTypeAttr().setExpression("void");
//    rs.addItem(ma, 0);
//    FieldAttributes fa = new FieldAttributes();
//    fa.getTypeAttr().setMatch(true);
//    fa.getTypeAttr().setExpression("int");
//    rs.addItem(fa, 1);
//    ma = new MethodAttributes();
//    ma.getReturnTypeAttr().setMatch(true);
//    ma.getReturnTypeAttr().setExpression(".*je.*");
//    rs.addItem(ma, 2);
//    ma = new MethodAttributes();
//    ma.getReturnTypeAttr().setMatch(true);
//    ma.getReturnTypeAttr().setExpression("Integer\\[\\]");
//    rs.addItem(ma, 3);
//    ma = new MethodAttributes();
//    ma.getReturnTypeAttr().setMatch(true);
//    ma.getReturnTypeAttr().setExpression("int");
//    rs.addItem(ma, 4);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult12.java");
//  }
//
//  public final void testRelatedMethodsDepthOriginal() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13DO.java");
//  }
//
//  public final void testRelatedMethodsDepthAlphabetical() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.ALPHABETICAL_ORDER);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13DA.java");
//  }
//
//  public final void testRelatedMethodsDepthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13DI.java");
//  }
//
//  public final void testRelatedMethodsBreadthOriginal() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13BO.java");
//  }
//
//  public final void testRelatedMethodsBreadthAlphabetical() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.ALPHABETICAL_ORDER);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13BA.java");
//  }
//
//  public final void testRelatedMethodsBreadthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13BI.java");
//  }
//
//  public final void testEmitTLCommentsRelatedMethodsBreadthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_TOP_LEVEL);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13BITLC.java");
//  }
//
//  public final void testEmitEMCommentsRelatedMethodsBreadthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_EACH_METHOD);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13BIEMC.java");
//  }
//
//  public final void testEmitELCommentsRelatedMethodsBreadthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_EACH_LEVEL);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13BIELC.java");
//  }
//
//  public final void testEmitNFCommentsRelatedMethodsBreadthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_NEW_FAMILY);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13BINFC.java");
//  }
//
//  public final void testEmitTLCommentsRelatedMethodsDepthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_TOP_LEVEL);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13DITLC.java");
//  }
//
//  public final void testEmitEMCommentsRelatedMethodsDepthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_EACH_METHOD);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13DIEMC.java");
//  }
//
//  public final void testEmitELCommentsRelatedMethodsDepthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_EACH_LEVEL);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13DIELC.java");
//  }
//
//  public final void testEmitNFCommentsRelatedMethodsDepthInvocation() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_NEW_FAMILY);
//    CommentRule c = new CommentRule();
//    c.setCommentText("// Preceding comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setPrecedingComment(c);
//    c = new CommentRule();
//    c.setCommentText("// Trailing comment: TL=%TL%\n" +
//                     "// MN=%MN%\n" +
//                     "// AM=%AM%\n" +
//                     "// Level %LV%");
//    rs.getExtractedMethodsSettings().setTrailingComment(c);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13DINFC.java");
//  }
//
//  public final void testRelatedMethodsException() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest13.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER);
//    MethodAttributes ma = new MethodAttributes();
//    ma.setNoExtractedMethods(true);
//    ma.getNameAttr().setMatch(true);
//    ma.getNameAttr().setExpression("GF");
//    rs.addItem(ma, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult13Ex.java");
//  }
//
//  public final void testKeepOverloadedMethodsTogether() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest14.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(false);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
//    rs.setKeepOverloadedMethodsTogether(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult14.java");
//  }
//
//  public final void testOverrides() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest15.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//  }
//
//  public final void testImplements() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest16.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//  }
//
//  public final void testXML() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest17.xml");
//    // we should not do anything to XML files.
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementTest17.xml");
//  }
//
//  public final void testKeepGSTogether() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest18.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.ALPHABETICAL_ORDER);
//    FieldAttributes fa = new FieldAttributes();
//    rs.addItem(fa, 0);
//    MethodAttributes ma = new MethodAttributes();
//    ma.setConstructorMethodType(true);
//    rs.addItem(ma, 1);
//    ma = new MethodAttributes();
//    ma.setGetterSetterMethodType(true);
//    ma.getSortAttr().setByName(true);
//    rs.addItem(ma, 2);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult18.java");
//  }
//
//  public final void testKeepGSWithProperty() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest18.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(true);
//    rs.setKeepGettersSettersWithProperty(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.ALPHABETICAL_ORDER);
//    FieldAttributes fa = new FieldAttributes();
//    rs.addItem(fa, 0);
//    MethodAttributes ma = new MethodAttributes();
//    ma.setConstructorMethodType(true);
//    rs.addItem(ma, 1);
//    ma = new MethodAttributes();
//    ma.setGetterSetterMethodType(true);
//    ma.getSortAttr().setByName(true);
//    rs.addItem(ma, 2);
////        rs.setAskBeforeRearranging(true);       //  testing only
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult18A.java");
//  }
//
//  public final void testKeepGSWithPropertyElseTogether() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest18B.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(true);
//    rs.setKeepGettersSettersWithProperty(true);
//    rs.getExtractedMethodsSettings().setOrdering(RelatedMethodsSettings.ALPHABETICAL_ORDER);
//    FieldAttributes fa = new FieldAttributes();
//    rs.addItem(fa, 0);
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("// Getters/Setters");
//    cr.setEmitCondition(CommentRule.EMIT_ALWAYS);
//    rs.addItem(cr, 1);
//    MethodAttributes ma = new MethodAttributes();
//    ma.setGetterSetterMethodType(true);
//    ma.getSortAttr().setByName(true);
//    ma.getGetterSetterDefinition().setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    ma.getGetterSetterDefinition().setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_IMMATERIAL);
//    ma.getGetterSetterDefinition().setSetterNameCriterion(GetterSetterDefinition.SETTER_NAME_CORRECT_PREFIX);
//    ma.getGetterSetterDefinition().setSetterBodyCriterion(GetterSetterDefinition.SETTER_BODY_IMMATERIAL);
//    rs.addItem(ma, 2);
//    cr = new CommentRule();
//    cr.setCommentText("// Other Methods");
//    cr.setEmitCondition(CommentRule.EMIT_ALWAYS);
//    rs.addItem(cr, 3);
//    ma = new MethodAttributes();
//    ma.getSortAttr().setByName(true);
//    rs.addItem(ma, 4);
//
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult18B.java");
//  }
//
//  public final void testKeepOverloadsTogetherOriginalOrder() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest19.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(false);
//    rs.setKeepOverloadedMethodsTogether(true);
//    rs.setOverloadedOrder(RearrangerSettings.OVERLOADED_ORDER_RETAIN_ORIGINAL);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult19A.java");
//  }
//
//  public final void testKeepOverloadsTogetherAscendingOrder() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest19.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(false);
//    rs.setKeepOverloadedMethodsTogether(true);
//    rs.setOverloadedOrder(RearrangerSettings.OVERLOADED_ORDER_ASCENDING_PARAMETERS);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult19B.java");
//  }
//
//  public final void testKeepOverloadsTogetherDescendingOrder() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest19.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(false);
//    rs.setKeepOverloadedMethodsTogether(true);
//    rs.setOverloadedOrder(RearrangerSettings.OVERLOADED_ORDER_DESCENDING_PARAMETERS);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult19C.java");
//  }
//
//  public final void testInnerClassReferenceToChild() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest20.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult20.java");
//  }
//
//  public final void testMultipleFieldDecl() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest21.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getSortAttr().setByName(true);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult21.java");
//  }
//
//  public final void testRemoveBlankLines() throws Exception {
//    configureByFile("/com/wrq/rearranger/SpaceTest1.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(0);
//    rs.getAfterMethodLBrace().setForce(true);
//    rs.getAfterMethodLBrace().setnBlankLines(0);
//    rs.getBeforeMethodRBrace().setForce(true);
//    rs.getBeforeMethodRBrace().setnBlankLines(0);
//    rs.getAfterMethodRBrace().setForce(true);
//    rs.getAfterMethodRBrace().setnBlankLines(0);
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(0);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getAfterClassRBrace().setnBlankLines(0);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/SpaceResult1.java");
//  }
//
//  public final void testAddBlankLines() throws Exception {
//    configureByFile("/com/wrq/rearranger/SpaceTest2.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(2);
//    rs.getAfterMethodLBrace().setForce(true);
//    rs.getAfterMethodLBrace().setnBlankLines(3);
//    rs.getBeforeMethodRBrace().setForce(true);
//    rs.getBeforeMethodRBrace().setnBlankLines(2);
//    rs.getAfterMethodRBrace().setForce(true);
//    rs.getAfterMethodRBrace().setnBlankLines(1);
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(3);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getAfterClassRBrace().setnBlankLines(4);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/SpaceResult2.java");
//  }
//
//  public final void testInnerClassBlankLines() throws Exception {
//    configureByFile("/com/wrq/rearranger/SpaceTest4.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(0);
//    rs.getAfterMethodLBrace().setForce(true);
//    rs.getAfterMethodLBrace().setnBlankLines(0);
//    rs.getBeforeMethodRBrace().setForce(true);
//    rs.getBeforeMethodRBrace().setnBlankLines(0);
//    rs.getAfterMethodRBrace().setForce(true);
//    rs.getAfterMethodRBrace().setnBlankLines(1);
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(1);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getAfterClassRBrace().setnBlankLines(1);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/SpaceResult4.java");
//  }
//
//  public void testInnerClassSpacing() throws Exception {
//    configureByFile("/com/wrq/rearranger/SpaceTest5.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final File physFile = new File(InteractiveTest.DEFAULT_CONFIGURATION);
//    rs = RearrangerSettings.getSettingsFromFile(physFile);
//    rs.setAskBeforeRearranging(false);
//    rs.getAfterClassRBrace().setnBlankLines(2);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(3);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/SpaceResult5.java");
//  }
//
//  public void testNoRearrangementInnerClass() throws Exception {
//    configureByFile("/com/wrq/rearranger/NoRearrangeInnerClassTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final File settingsFile = new File(InteractiveTest.DEFAULT_CONFIGURATION_ROOT +
//                                       "/test/testData/com/wrq/rearranger/NoRearrangementInnerClassCfg.xml");
//    rs = RearrangerSettings.getSettingsFromFile(settingsFile);
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/NoRearrangeInnerClassResult.java");
//  }
//
//  public void testSpacingWithTrailingWhitespace() throws Exception {
//    configureByFile("/com/wrq/rearranger/SpaceTest6.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/SpaceResult6.java");
//  }
//
//  public void testSpacingJoinLineBug() throws Exception {
//    configureByFile("/com/wrq/rearranger/SpaceTest7.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    File settingsFile = new File(InteractiveTest.DEFAULT_CONFIGURATION_ROOT +
//                                 "/test/testData/com/wrq/rearranger/SpaceTest7cfg.xml");
//    rs = RearrangerSettings.getSettingsFromFile(settingsFile);
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/SpaceResult7.java");
//  }
//
//  /**
//   * Submitted by Brian Buckley.
//   *
//   * @throws Exception test exception
//   */
//  public void testSpacingConflictingSettingBug() throws Exception {
//    configureByFile("/com/wrq/rearranger/SpaceTest8.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(0);
//    rs.getBeforeMethodLBrace().setForce(true);
//    rs.getBeforeMethodLBrace().setnBlankLines(1);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/SpaceResult8.java");
//  }
//
//  public void testGetPrefixImmaterial() throws Exception {
//    configureByFile("/com/wrq/rearranger/GetterDefinitionTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma = new MethodAttributes();
//    GetterSetterDefinition gsd = ma.getGetterSetterDefinition();
//    rs.addItem(ma, 0);
//    ma.setGetterSetterMethodType(true);
//    gsd.setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    gsd.setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_IMMATERIAL);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/GetPrefixImmaterialResult.java");
//  }
//
//  public void testGetPrefixReturns() throws Exception {
//    configureByFile("/com/wrq/rearranger/GetterDefinitionTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma = new MethodAttributes();
//    GetterSetterDefinition gsd = ma.getGetterSetterDefinition();
//    rs.addItem(ma, 0);
//    ma.setGetterSetterMethodType(true);
//    gsd.setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    gsd.setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_RETURNS);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/GetPrefixReturnsResult.java");
//  }
//
//  public void testGetPrefixReturnsField() throws Exception {
//    configureByFile("/com/wrq/rearranger/GetterDefinitionTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma = new MethodAttributes();
//    GetterSetterDefinition gsd = ma.getGetterSetterDefinition();
//    rs.addItem(ma, 0);
//    ma.setGetterSetterMethodType(true);
//    gsd.setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    gsd.setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_RETURNS_FIELD);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/GetPrefixReturnsFieldResult.java");
//  }
//
//  public void testGetFieldReturns() throws Exception {
//    configureByFile("/com/wrq/rearranger/GetterDefinitionTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma = new MethodAttributes();
//    GetterSetterDefinition gsd = ma.getGetterSetterDefinition();
//    rs.addItem(ma, 0);
//    ma.setGetterSetterMethodType(true);
//    gsd.setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_MATCHES_FIELD);
//    gsd.setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_RETURNS);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/GetFieldReturnsResult.java");
//  }
//
//  public void testGetFieldReturnsField() throws Exception {
//    configureByFile("/com/wrq/rearranger/GetterDefinitionTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma = new MethodAttributes();
//    GetterSetterDefinition gsd = ma.getGetterSetterDefinition();
//    rs.addItem(ma, 0);
//    ma.setGetterSetterMethodType(true);
//    gsd.setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_MATCHES_FIELD);
//    gsd.setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_RETURNS_FIELD);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/GetFieldReturnsFieldResult.java");
//  }
//
//  public void testSpecialGS() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest22.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma = new MethodAttributes();
//    GetterSetterDefinition gsd = ma.getGetterSetterDefinition();
//    rs.addItem(ma, 0);
//    ma.setGetterSetterMethodType(true);
//    gsd.setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    gsd.setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_RETURNS);
//    gsd.setSetterBodyCriterion(GetterSetterDefinition.SETTER_BODY_IMMATERIAL);
//    gsd.setSetterNameCriterion(GetterSetterDefinition.SETTER_NAME_CORRECT_PREFIX);
//    rs.setKeepGettersSettersTogether(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult22.java");
//  }
//
//  public void testInterfaceNoNameNotAlphabeticalNoExcludeMethodAlphabetical() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest23.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(false);
//    InterfaceAttributes ia = new InterfaceAttributes();
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Header ****/");
//    ia.setPrecedingComment(cr);
//    cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Trailer ***/");
//    ia.setTrailingComment(cr);
//    ia.setNoExtractedMethods(true);
//    ia.setMethodOrder(InterfaceAttributes.METHOD_ORDER_ALPHABETICAL);
//    ia.setAlphabetizeInterfaces(false);
//    rs.addItem(ia, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult23NNNANXMA.java");
//  }
//
//  public void testInterfaceNoNameNotAlphabeticalNoExcludeMethodEncountered() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest23.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(false);
//    InterfaceAttributes ia = new InterfaceAttributes();
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Header ****/");
//    ia.setPrecedingComment(cr);
//    cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Trailer ***/");
//    ia.setTrailingComment(cr);
//    ia.setNoExtractedMethods(true);
//    ia.setMethodOrder(InterfaceAttributes.METHOD_ORDER_ENCOUNTERED);
//    ia.setAlphabetizeInterfaces(false);
//    rs.addItem(ia, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult23NNNANXME.java");
//  }
//
//  public void testInterfaceNoNameNotAlphabeticalNoExcludeMethodInterfaceOrder() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest23.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(false);
//    InterfaceAttributes ia = new InterfaceAttributes();
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Header ****/");
//    ia.setPrecedingComment(cr);
//    cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Trailer ***/");
//    ia.setTrailingComment(cr);
//    ia.setNoExtractedMethods(true);
//    ia.setMethodOrder(InterfaceAttributes.METHOD_ORDER_INTERFACE_ORDER);
//    ia.setAlphabetizeInterfaces(false);
//    rs.addItem(ia, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult23NNNANXMI.java");
//  }
//
//  public void testInterfaceByNameNotAlphabeticalNoExcludeMethodEncountered() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest23.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(false);
//    InterfaceAttributes ia = new InterfaceAttributes();
//    ia.getNameAttr().setMatch(true);
//    ia.getNameAttr().setExpression("IFace1");
//    ia.setNoExtractedMethods(true);
//    ia.setMethodOrder(InterfaceAttributes.METHOD_ORDER_ENCOUNTERED);
//    ia.setAlphabetizeInterfaces(false);
//    rs.addItem(ia, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult23BNNANXME.java");
//  }
//
//  public void testInterfaceIsAlphabeticalNoExcludeMethodEncountered() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest23.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(false);
//    InterfaceAttributes ia = new InterfaceAttributes();
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Header ****/");
//    ia.setPrecedingComment(cr);
//    cr = new CommentRule();
//    cr.setCommentText("/**** Interface %IF% Trailer ***/");
//    ia.setTrailingComment(cr);
//    ia.setNoExtractedMethods(true);
//    ia.setMethodOrder(InterfaceAttributes.METHOD_ORDER_ENCOUNTERED);
//    ia.setAlphabetizeInterfaces(true);
//    rs.addItem(ia, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult23NNIANXME.java");
//  }
//
//  public void testNPE24() throws Exception {
//    // submitted by Nathan Brown.  Caused NPE in Rearranger plugin version 1.7.
//    int itemIndex = 0;
//    configureByFile("/com/wrq/rearranger/RearrangementTest24.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(true);
//    rs.setKeepOverloadedMethodsTogether(true);
////        rs.setAskBeforeRearranging(false);
//    rs.setOverloadedOrder(1);
//    rs.setGlobalCommentPattern("");
//    rs.setRemoveBlanksInsideCodeBlocks(false);
//    FieldAttributes fa;
//    fa = new FieldAttributes();
//    fa.getSortAttr().setByName(false);
//    fa.getPlAttr().setPlPublic(true);
//    fa.getPlAttr().setPlPrivate(true);
//    fa.getPlAttr().setPlProtected(true);
//    fa.getPlAttr().setPlPackage(true);
//    fa.getPlAttr().setInvertProtectionLevel(false);
//    fa.getStAttr().setValue(true);
//    fa.getStAttr().setInvert(false);
//    rs.addItem(fa, itemIndex++);
//    fa = new FieldAttributes();
//    fa.getStAttr().setValue(true);
//    fa.getStAttr().setInvert(true);
//    fa.getInitToAnonClassAttr().setValue(true);
//    fa.getInitToAnonClassAttr().setInvert(true);
//    rs.addItem(fa, itemIndex++);
//    fa = new FieldAttributes();
//    fa.getInitToAnonClassAttr().setValue(true);
//    rs.addItem(fa, itemIndex++);
//    MethodAttributes ma;
//    ma = new MethodAttributes();
//    ma.getStaticInitAttr().setValue(true);
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.setConstructorMethodType(true);
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.getNameAttr().setMatch(true);
//    ma.getNameAttr().setExpression("clone");
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.getNameAttr().setMatch(true);
//    ma.getNameAttr().setExpression("dispose");
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.getNameAttr().setMatch(true);
//    ma.getNameAttr().setExpression("_dispose");
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.getNameAttr().setMatch(true);
//    ma.getNameAttr().setExpression("build");
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.getAbstractAttr().setValue(true);
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.getStAttr().setValue(true);
//    ma.getStAttr().setInvert(true);
//    ma.getAbstractAttr().setValue(true);
//    ma.getAbstractAttr().setInvert(true);
//    ma.setOtherMethodType(true);
//    rs.addItem(ma, itemIndex++);
//    ma = new MethodAttributes();
//    ma.setGetterSetterMethodType(true);
//    rs.addItem(ma, itemIndex++);
//    InnerClassAttributes ica = new InnerClassAttributes();
//    rs.addItem(ica, itemIndex++);
//    ma = new MethodAttributes();
//    ma.getStAttr().setValue(true);
//    rs.addItem(ma, itemIndex);
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setBelowFirstCaller(false);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    rs.getExtractedMethodsSettings().setOrdering(0);
//    rs.getExtractedMethodsSettings().setNonPrivateTreatment(2);
//    rs.getExtractedMethodsSettings().setCommentType(0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//  }
//
//  public void testSpacingOptions() throws Exception {
//    /**
//     * From Thomas Singer:
//     * I've enabled
//     * - Force 0 blank lines before class close brace "}"
//     * - Force 0 blank lines before method close brace "}"
//     * - Remove initial and final blank lines inside code block
//     * but in the code below the blank lines don't get removed when invoking
//     * Rearrager from editor's context menu:
//     */
//    configureByFile("/com/wrq/rearranger/RearrangementTest25.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(0);
//    rs.getBeforeMethodRBrace().setForce(true);
//    rs.getBeforeMethodRBrace().setnBlankLines(0);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult25.java");
//  }
//
//  public void testPriority() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma;
//    ma = new MethodAttributes();
//    ma.setPriority(1);
//    rs.addItem(ma, 0);
//    ma = new MethodAttributes();
//    ma.setPriority(2);
//    ma.getNameAttr().setMatch(true);
//    ma.getNameAttr().setExpression("method.*");
//    rs.addItem(ma, 1);
//    ma = new MethodAttributes();
//    ma.setPriority(2);
//    ma.getNameAttr().setMatch(true);
//    ma.getNameAttr().setExpression(".*Method");
//    rs.addItem(ma, 2);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult1A.java");
//  }
//
//  public void testGSRuleWithClassInitializer() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest26.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma;
//    ma = new MethodAttributes();
//    ma.setGetterSetterMethodType(true);
//    rs.addItem(ma, 0);
//    rs.setKeepGettersSettersTogether(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult26.java");
//  }
//
//  public void testKeepGSTogetherAndExtractedMethods() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest27.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(true);
//    rs.setKeepOverloadedMethodsTogether(true);
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult27.java");
//  }
//
//  public void testRegexEscape() throws Exception {
//    String s = "// ********* start of fields *********";
//    String result = RegexUtil.escape(s);
//    assertEquals("sequence reduction failed", "// \\*{9} start of fields \\*{9}", result);
//    s = "// \\ backslash \n \t \\d [...] (^...$)";
//    result = RegexUtil.escape(s);
//    assertEquals("special character escape failed", "// \\\\ backslash \\n \\t \\\\d \\[\\.\\.\\.\\]" +
//                                                    " \\(\\^\\.\\.\\.\\$\\)", result);
//  }
//
//  public void testRegexCombine() throws Exception {
//    String p1 = RegexUtil.escape("// ********* start of fields *********");
//    String p2 = RegexUtil.escape("// ********* start of methods *********");
//    List<String> list = new ArrayList<String>();
//    list.add(p1);
//    list.add(p2);
//    String result = RegexUtil.combineExpressions(list);
//    assertEquals("combination failed", "// \\*{9} start of (fiel|metho)ds \\*{9}", result);
//    String p3 = RegexUtil.escape("// ***** start of interfaces *******");
//    list.add(p3);
//    result = RegexUtil.combineExpressions(list);
//    assertEquals("combination failed", "// (\\*{9} start of (fiel|metho)ds \\*{9}|" +
//                                       "\\*{5} start of interfaces \\*{7})", result);
//  }
//
//  public void testVariousComments() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest28.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(true);
//    rs.setKeepOverloadedMethodsTogether(true);
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("// start of fields");
//    cr.setEmitCondition(CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE);
//    cr.setAllSubsequentRules(true);
//    cr.setnSubsequentRulesToMatch(1);
//    rs.addItem(cr, 0);
//    FieldAttributes fa = new FieldAttributes();
//    rs.addItem(fa, 1);
//    cr = new CommentRule();
//    cr.setCommentText("// end of fields");
//    cr.setEmitCondition(CommentRule.EMIT_IF_ITEMS_MATCH_PRECEDING_RULE);
//    cr.setAllPrecedingRules(true);
//    cr.setnPrecedingRulesToMatch(1);
//    rs.addItem(cr, 2);
//    InterfaceAttributes ia = new InterfaceAttributes();
//    ia.setMethodOrder(InterfaceAttributes.METHOD_ORDER_ENCOUNTERED);
//    ia.setAlphabetizeInterfaces(false);
//    ia.setNoExtractedMethods(false);
//    cr = new CommentRule();
//    cr.setCommentText("// start of interface %IF%");
//    ia.setPrecedingComment(cr);
//    cr = new CommentRule();
//    cr.setCommentText("// end of interface %IF%");
//    ia.setTrailingComment(cr);
//    rs.addItem(ia, 3);
//    rs.getExtractedMethodsSettings().setBelowFirstCaller(false);
//    rs.getExtractedMethodsSettings().setMoveExtractedMethods(true);
//    rs.getExtractedMethodsSettings().setCommentType(RelatedMethodsSettings.COMMENT_TYPE_EACH_LEVEL);
//    rs.getExtractedMethodsSettings().setNonPrivateTreatment(RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ANY_CALLERS);
//    rs.getExtractedMethodsSettings().setDepthFirstOrdering(true);
//    cr = new CommentRule();
//    cr.setCommentText("// Level %LV% methods");
//    rs.getExtractedMethodsSettings().setPrecedingComment(cr);
//    cr = new CommentRule();
//    cr.setCommentText("// end Level %LV% methods");
//    rs.getExtractedMethodsSettings().setTrailingComment(cr);
//    // should work with or without the global comment pattern
////        rs.setGlobalCommentPattern("// (((start|end) of (fields|interface [A-Za-z_0-9]+))|(end|)Level [0-9]+ methods)");
//    rah.rearrangeDocument(getProject(), file, rs, doc); // note - blank lines end up "reversed"
//    // where a blank line, generated comment, and method occur in order; the generated comment is removed
//    // and the blank line precedes the method; when the new comment is generated, it is inserted before
//    // the blank line.
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult28.java");
//  }
//
//  public void testParseBugInfiniteLoop() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest29.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementTest29.java");
//  }
//
//  public void testSpacingBug() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest30.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(0);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getAfterClassRBrace().setnBlankLines(1);
//    rs.getAfterMethodLBrace().setForce(true);
//    rs.getAfterMethodLBrace().setnBlankLines(0);
//    rs.getAfterMethodRBrace().setForce(true);
//    rs.getAfterMethodRBrace().setnBlankLines(1);
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(0);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult30.java");
//  }
//
//  public void testSpacingBug2() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest31.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(0);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getAfterClassRBrace().setnBlankLines(1);
//    rs.getAfterMethodLBrace().setForce(true);
//    rs.getAfterMethodLBrace().setnBlankLines(0);
//    rs.getAfterMethodRBrace().setForce(true);
//    rs.getAfterMethodRBrace().setnBlankLines(1);
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(0);
//    rs.getBeforeMethodRBrace().setForce(true);
//    rs.getBeforeMethodRBrace().setnBlankLines(0);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult31.java");
//  }
//
//  public void testSpacingBug3() throws Exception {
//    configureByFile("/com/wrq/rearranger/DomainExpanderTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(0);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getAfterClassRBrace().setnBlankLines(1);
//    rs.getAfterMethodLBrace().setForce(true);
//    rs.getAfterMethodLBrace().setnBlankLines(0);
//    rs.getAfterMethodRBrace().setForce(true);
//    rs.getAfterMethodRBrace().setnBlankLines(0);
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(0);
//    rs.getBeforeMethodRBrace().setForce(true);
//    rs.getBeforeMethodRBrace().setnBlankLines(0);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/DomainExpanderResult.java");
//  }
//
//  /**
//   * Bug occurs when one or more blank lines precede a generated comment.
//   * When comment is removed, blank lines now precede the item.  Comment is inserted
//   * at the beginning (i.e. before the blank lines) and a newline character is
//   * prefixed to the comment.  Net effect is that new blank line(s) appear after the comment.
//   *
//   * @throws Exception test exception
//   */
//  public void testGeneratedCommentSpacingBug() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest32.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult32.java");
//  }
//
//  public void testGeneratedCommentSpacing() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest32.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult32.java");
//  }
//
//  public void testInnerClassComments() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest34.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.setRearrangeInnerClasses(true);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("// ----- OUTER CLASS -----\n");
//    rs.getClassOrderAttributeList().add(0, cr);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult34.java");
//  }
//
//  public void testInnerClassCommentsNoRearrangement() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest34.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.setRearrangeInnerClasses(false);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("// ----- OUTER CLASS -----\n");
//    rs.getClassOrderAttributeList().add(0, cr);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult34B.java");
//  }
//
//  public void testFirstInsertionOfComment() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest35.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    CommentRule cr = new CommentRule();
//    cr.setCommentText("// ----- FIELDS -----\n");
//    FieldAttributes fa = new FieldAttributes();
//    rs.addItem(cr, 0);
//    rs.addItem(fa, 1);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult35.java");
//  }
//
//  public void testExcludeFromExtraction() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest36.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementTest36.java");
//  }
//
//  public void testInterferingGSNames() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest37.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(true);
//    rs.getDefaultGSDefinition().setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_IMMATERIAL);
//    rs.getDefaultGSDefinition().setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    rs.getDefaultGSDefinition().setSetterBodyCriterion(GetterSetterDefinition.SETTER_BODY_IMMATERIAL);
//    rs.getDefaultGSDefinition().setSetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult37.java");
//  }
//
//  public void testInterferingGSNamesNoKGSTogether() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest37.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.setKeepGettersSettersTogether(false);
//    rs.getDefaultGSDefinition().setGetterBodyCriterion(GetterSetterDefinition.GETTER_BODY_IMMATERIAL);
//    rs.getDefaultGSDefinition().setGetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    rs.getDefaultGSDefinition().setSetterBodyCriterion(GetterSetterDefinition.SETTER_BODY_IMMATERIAL);
//    rs.getDefaultGSDefinition().setSetterNameCriterion(GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementTest37.java");
//  }
//
//  public void testRemoveBlankLineInsideMethodBug() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest38.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    File settingsFile = new File(InteractiveTest.DEFAULT_CONFIGURATION_ROOT +
//                                 "/test/testData/com/wrq/rearranger/RearrangementTest38cfg.xml");
//    rs = RearrangerSettings.getSettingsFromFile(settingsFile);
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementTest38.java");
//  }
//
//  public void testSortFieldsByTypeAndName() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest39.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getSortAttr().setByType(true);
//    fa.getSortAttr().setByName(true);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult39B.java");
//  }
//
//  public void testSortFieldsByType() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest39.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getSortAttr().setByType(true);
//    fa.getSortAttr().setByName(false);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult39C.java");
//  }
//
//  public void testSortFieldsByTypeICAndName() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest39.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getSortAttr().setByType(true);
//    fa.getSortAttr().setTypeCaseInsensitive(true);
//    fa.getSortAttr().setByName(true);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult39.java");
//  }
//
//  public void testSortFieldsByTypeIC() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest39.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getSortAttr().setByType(true);
//    fa.getSortAttr().setTypeCaseInsensitive(true);
//    fa.getSortAttr().setByName(false);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult39A.java");
//  }
//
//  /**
//   * test detection of method overrides/overridden/implements/implemented attributes.
//   *
//   * @throws Exception test exception
//   */
//  public void testOverImpl() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest40.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult40.java");
//  }
//
//  public final void testRemoveBlankLinesBeforeMethod() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest41.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rs.getAfterClassLBrace().setForce(true);
//    rs.getAfterClassLBrace().setnBlankLines(0);
//    rs.getBeforeMethodLBrace().setForce(true);
//    rs.getBeforeMethodLBrace().setnBlankLines(0);
//    rs.getAfterMethodLBrace().setForce(true);
//    rs.getAfterMethodLBrace().setnBlankLines(0);
//    rs.getBeforeMethodRBrace().setForce(true);
//    rs.getBeforeMethodRBrace().setnBlankLines(0);
//    rs.getAfterMethodRBrace().setForce(true);
//    rs.getAfterMethodRBrace().setnBlankLines(0);
//    rs.getBeforeClassRBrace().setForce(true);
//    rs.getBeforeClassRBrace().setnBlankLines(0);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getAfterClassRBrace().setnBlankLines(0);
//    rs.setRemoveBlanksInsideCodeBlocks(true);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult41.java");
//  }
//
//  public final void testEnumClass() throws Exception {
//    final Project project = getProject();
//    final LanguageLevelProjectExtension llpExtension = LanguageLevelProjectExtension.getInstance(project);
//    LanguageLevel oldLevel = llpExtension.getLanguageLevel();
//    llpExtension.setLanguageLevel(LanguageLevel.JDK_1_5);
//    configureByFile("/com/wrq/rearranger/RearrangementTest42.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult42.java");
//    llpExtension.setLanguageLevel(oldLevel);
//  }
//
//  public final void testNumParameters() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest43.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    MethodAttributes ma;
//    ma = new MethodAttributes();
//    ma.getMinParamsAttr().setMatch(true);
//    ma.getMinParamsAttr().setValue(2);
//    ma.getMaxParamsAttr().setMatch(true);
//    ma.getMaxParamsAttr().setValue(3);
//    rs.addItem(ma, 0);
//    ma = new MethodAttributes();
//    ma.getMinParamsAttr().setMatch(true);
//    ma.getMinParamsAttr().setValue(1);
//    rs.addItem(ma, 1);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult43.java");
//  }
//
//  public final void testGeneratedComment() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest44.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    CommentRule cr = new CommentRule();
//    CommentFillString cfs = cr.getCommentFillString();
//    cfs.setFillString("-+");
//    cfs.setUseProjectWidthForFill(false);
//    cfs.setFillWidth(30);
//    cr.setEmitCondition(CommentRule.EMIT_ALWAYS);
//    cr.setCommentText("// %FS% METHODS %FS%");
//    rs.addItem(cr, 0);
//    MethodAttributes ma;
//    ma = new MethodAttributes();  // match all methods
//    rs.addItem(ma, 1);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult44.java");
//  }
//
//  public void testEnum1() throws Exception {
//    final Project project = getProject();
//    final LanguageLevelProjectExtension llpExtension = LanguageLevelProjectExtension.getInstance(project);
//    LanguageLevel oldLevel = llpExtension.getLanguageLevel();
//    llpExtension.setLanguageLevel(LanguageLevel.JDK_1_5);
//    configureByFile("/com/wrq/rearranger/Enum1Test.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    rs.setRearrangeInnerClasses(true);
//    rs.getExtractedMethodsSettings().setBelowFirstCaller(true);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(project, file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/Enum1Result.java");
//    llpExtension.setLanguageLevel(oldLevel);
//  }
//
//  public void testEnum2() throws Exception {
//    final Project project = getProject();
//    final LanguageLevelProjectExtension llpExtension = LanguageLevelProjectExtension.getInstance(project);
//    LanguageLevel oldLevel = llpExtension.getLanguageLevel();
//    llpExtension.setLanguageLevel(LanguageLevel.JDK_1_5);
//    configureByFile("/com/wrq/rearranger/Enum2Test.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.setRearrangeInnerClasses(true);
//    rs.getAfterClassRBrace().setForce(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(project, file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/Enum2Result.java");
//    llpExtension.setLanguageLevel(oldLevel);
//  }
//
//  public void testEnum2A() throws Exception {
//    final Project project = getProject();
//    final LanguageLevelProjectExtension llpExtension = LanguageLevelProjectExtension.getInstance(project);
//    LanguageLevel oldLevel = llpExtension.getLanguageLevel();
//    llpExtension.setLanguageLevel(LanguageLevel.JDK_1_5);
//    configureByFile("/com/wrq/rearranger/Enum2Test.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.setRearrangeInnerClasses(false);
//    rs.getAfterClassRBrace().setForce(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(project, file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/Enum2AResult.java");
//    llpExtension.setLanguageLevel(oldLevel);
//  }
//
//  public void testRearrangementIncompleteLastLine() throws Exception {
//    configureByFile("/com/wrq/rearranger/IncompleteLineTest.java");
//    final PsiFile file = getFile();
//    Project project = getProject();
//    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(project, file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/IncompleteLineResult.java");
//  }
//
//  public void testOverloadedMethodCategorization() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest45.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File(InteractiveTest.DEFAULT_CONFIGURATION));
//    rs.setAskBeforeRearranging(false);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(1);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult45.java");
//  }
//
//  public void testSuperclassAlgorithm() throws Exception {
//    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(getProject());
//    final PsiClass aClass =
//      psiFacade.getElementFactory().
//        createClassFromText("public String toString() {return null;}", null);
//    final PsiMethod method = aClass.getMethods()[0];
//    final PsiMethod[] superMethods = method.findSuperMethods();
//    assertEquals(1, superMethods.length);
//    PsiClass psiClass = superMethods[0].getContainingClass();
//    if (psiClass == null) {
//      fail("missing containing class");
//    }
//    else {
//      assertEquals("java.lang.Object", psiClass.getQualifiedName());
//    }
//  }
//
//  public void testSortingOriginalOrder() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest46.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getPlAttr().setPlProtected(true);
//    fa.getSortAttr().setByModifiers(false);
//    fa.getSortAttr().setByType(false);
//    fa.getSortAttr().setTypeCaseInsensitive(false);
//    fa.getSortAttr().setByName(false);
//    fa.getSortAttr().setNameCaseInsensitive(false);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult46A.java");
//  }
//
//  public void testSortingModifierOrder() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest46.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getPlAttr().setPlProtected(true);
//    fa.getSortAttr().setByModifiers(true);
//    fa.getSortAttr().setByType(false);
//    fa.getSortAttr().setTypeCaseInsensitive(false);
//    fa.getSortAttr().setByName(false);
//    fa.getSortAttr().setNameCaseInsensitive(false);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult46B.java");
//  }
//
//  public void testSortingTypeOrder() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest46.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getPlAttr().setPlProtected(true);
//    fa.getSortAttr().setByModifiers(false);
//    fa.getSortAttr().setByType(true);
//    fa.getSortAttr().setTypeCaseInsensitive(false);
//    fa.getSortAttr().setByName(false);
//    fa.getSortAttr().setNameCaseInsensitive(false);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult46C.java");
//  }
//
//  public void testSortingTypeOrderCI() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest46.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getPlAttr().setPlProtected(true);
//    fa.getSortAttr().setByModifiers(false);
//    fa.getSortAttr().setByType(true);
//    fa.getSortAttr().setTypeCaseInsensitive(true);
//    fa.getSortAttr().setByName(false);
//    fa.getSortAttr().setNameCaseInsensitive(false);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult46D.java");
//  }
//
//  public void testSortingMTNOrderCI() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest46.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getPlAttr().setPlProtected(true);
//    fa.getSortAttr().setByModifiers(true);
//    fa.getSortAttr().setByType(true);
//    fa.getSortAttr().setTypeCaseInsensitive(true);
//    fa.getSortAttr().setByName(true);
//    fa.getSortAttr().setNameCaseInsensitive(true);
//    rs.addItem(fa, 0);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult46E.java");
//  }
//
//  public void testEnumSelection() throws Exception {
//    configureByFile("/com/wrq/rearranger/RearrangementTest47.java");
//    final PsiFile file = getFile();
//    final Project project = getProject();
//    final LanguageLevelProjectExtension llpExtension = LanguageLevelProjectExtension.getInstance(project);
//    LanguageLevel oldLevel = llpExtension.getLanguageLevel();
//    llpExtension.setLanguageLevel(LanguageLevel.JDK_1_5);
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    FieldAttributes fa = new FieldAttributes();
//    fa.getStAttr().setValue(true);
//    rs.addItem(fa, 0);
//    InnerClassAttributes ica = new InnerClassAttributes();
//    ica.getEnumAttr().setValue(true);
//    rs.addItem(ica, 1);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/RearrangementResult47.java");
//    llpExtension.setLanguageLevel(oldLevel);
//  }
//
//  public void testOverloaded1() throws Exception {
//    configureByFile("/com/wrq/rearranger/OverloadedTest1.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File("testData/com/wrq/rearranger/OverloadedConfig.xml"));
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/OverloadedResult1.java");
//  }
//
//  public void testGenerics1() throws Exception {
//    configureByFile("/com/wrq/rearranger/GenericsTest1.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File("testData/com/wrq/rearranger/OverloadedConfig.xml"));
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/GenericsResult1.java");
//  }
//
//  public void testInterface1() throws Exception {
//    configureByFile("/com/wrq/rearranger/InterfaceTest1.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    FieldAttributes fa = new FieldAttributes();
//    fa.getPlAttr().setPlPublic(true); // match all public fields; in an interface, all constants are regarded as public
//    fa.getStAttr().setValue(true); // match all static fields; in an interface, all constants are regarded as public
//    rs.addItem(fa, 0);
//    MethodAttributes ma = new MethodAttributes();
//    ma.getPlAttr().setPlPublic(true); // match all public methods; in an interface, all methods are regarded as public
//    rs.addItem(ma, 1);
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/InterfaceResult1.java");
//  }
//
//  public void testCorruptRespacing() throws Exception {
//    configureByFile("/com/wrq/rearranger/CorruptRespacingTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    rs = RearrangerSettings.getSettingsFromFile(new File("testData/com/wrq/rearranger/CorruptionConfig.xml"));
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/CorruptRespacingResult.java");
//  }
//
//  public void testCommentSeparator1() throws Exception {
//    configureByFile("/com/wrq/rearranger/CommentSeparatorTest1.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final CommentRule c = new CommentRule();
//    c.setCommentText("\n\t// Fields %FS%\n");
//    c.setEmitCondition(CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE);
//    CommentFillString cfs = new CommentFillString();
//    cfs.setFillString("=");
//    cfs.setUseProjectWidthForFill(false);
//    cfs.setFillWidth(80);
//    c.setCommentFillString(cfs);
//    rs.addItem(c, 0);
//    FieldAttributes fa = new FieldAttributes();
//    fa.getPlAttr().setPlPublic(true);
//    rs.addItem(fa, 1);
//    rs.setAskBeforeRearranging(false);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.setTabSize(4);
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/CommentSeparatorResult1.java");
//  }
//
//  public void testEmptySetter() throws Exception {
//    configureByFile("/com/wrq/rearranger/BitFieldTest.java");
//    final PsiFile file = getFile();
//    final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
//    final File physFile = new File(InteractiveTest.DEFAULT_CONFIGURATION);
//    rs = RearrangerSettings.getSettingsFromFile(physFile);
//    rs.setAskBeforeRearranging(false);
//    rs.getAfterClassRBrace().setnBlankLines(2);
//    rs.getAfterClassRBrace().setForce(true);
//    rs.getNewlinesAtEOF().setForce(true);
//    rs.getNewlinesAtEOF().setnBlankLines(3);
//    final RearrangerActionHandler rah = new RearrangerActionHandler();
//    rah.rearrangeDocument(getProject(), file, rs, doc);
//    super.checkResultByFile("/com/wrq/rearranger/BitFieldResult.java");
//  }

  private void doTest(@NotNull String srcFileName, @Nullable String expectedResultFileName, @Nullable Closure adjustment = null) {
    myFixture.configureByFile("${srcFileName}.java")
    if (adjustment) {
      adjustment.call()
    }
    ApplicationManager.application.runWriteAction {
      new RearrangerActionHandler().rearrangeDocument(myFixture.project, myFixture.file, mySettings, myFixture.editor.document);
    }
    myFixture.checkResultByFile("${expectedResultFileName}.java")
  }
}