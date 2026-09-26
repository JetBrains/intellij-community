// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.psi.LombokExtensionMethod;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.completion.CompletionType.BASIC;
import static com.intellij.codeInsight.completion.CompletionType.SMART;

@NotNullByDefault
public class LombokExtensionMethodCompletionContributorTest extends AbstractLombokLightCodeInsightTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/completion/extensionMethod/LombokExtensionMethodCompletionContributor";
  }

  public void testStringReceiverAndStringArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  public void testObjectReceiverAndStringArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  public void testStringReceiverAndObjectArgument() {
    doTestBothCompletionTypes();
  }

  public void testWildcardListReceiverAndListArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  public void testStringReceiverAndWildcardWithUpperBoundStringArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  public void testMoreThanTwoArguments() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "(String b, String c)", "void"));
  }

  public void testLowerBoundedListReceiver() {
    doTestBothCompletionTypes();
  }

  public void testVarargReceiverAndArrayArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  public void testVarargReceiverAndSingleElemArgument() {
    doTestBothCompletionTypes();
  }

  public void testMatchingExpressionType() {
    doTest(BASIC,
           suggestion("myIntegerMethod", "()", "Integer"),
           suggestion("myStringMethod", "()", "String"));
    doTest(SMART, suggestion("myIntegerMethod", "()", "Integer"));
  }

  public void testMatchingExpressionTypeGenericResult() {
    doTest(BASIC, suggestion("myGenericMethod", "(Class<T> type)", "T"));
    doTest(SMART, suggestion("myGenericMethod", "(Class<T> type)", "T"));
  }

  public void testMatchingExpressionTypeGenericResultWithWrongBounds() {
    doTest(BASIC, suggestion("myBoundedGenericMethod", "(Class<T> type)", "T"));
    doTest(SMART);
  }

  public void testGenericReceiverAndParamContributingToGenericResult() {
    doTest(BASIC, suggestion("pairWith", "(S e2)", "Pair<String, S>"));
    doTest(SMART, suggestion("pairWith", "(S e2)", "Pair<String, S>"));
  }

  public void testWithPrefix() {
    doTest(BASIC,
           suggestion("myExtensionMethod", "()", "void"),
           suggestion("myExtensionMethod2", "()", "void"));
    doTest(SMART,
           suggestion("myExtensionMethod", "()", "void"),
           suggestion("myExtensionMethod2", "()", "void"));
  }

  public void testNonStaticExtensionMethod() {
    doTestBothCompletionTypes();
  }

  public void testNonPublicExtensionMethod() {
    doTestBothCompletionTypes();
  }

  public void testMalformedExtensionMethodWithoutReturnType() {
    doTestBothCompletionTypes();
  }

  public void testSameNameButDifferentSignatureThanInstanceMethod() {
    doTestBothCompletionTypes(suggestion("myInstanceMethod", "(String value, int x)", "void"));
  }

  public void testSameSignatureAsInstanceMethod() {
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(BASIC);
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(SMART);
  }

  public void testSameSignatureAsSuperClassInstanceMethod() {
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(SMART);
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(BASIC);
  }

  private void extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(CompletionType completionType) {
    LookupElement[] lookupElements = configureAndGetAllCompletionSuggestions(completionType);
    List<String> allSuggestions = ContainerUtil.map(lookupElements, LookupElement::getLookupString);

    assertContainsElements(allSuggestions, "myInstanceMethod");
    assertEquals(Set.of(), filterLombokSuggestions(lookupElements));
  }

  private void doTestBothCompletionTypes(Suggestion... expectedSuggestions) {
    doTest(BASIC, expectedSuggestions);
    doTest(SMART, expectedSuggestions);
  }

  /// Verifies that all Lombok-generated suggestions in code completion exactly match `expectedSuggestions`.
  private void doTest(CompletionType completionType, Suggestion... expectedSuggestions) {
    Set<Suggestion> actualSuggestions = configureAndGetLombokCompletionSuggestions(completionType);
    Set<Suggestion> expectedSuggestionSet = Set.of(expectedSuggestions);
    assertEquals(expectedSuggestionSet, actualSuggestions);
  }

  private Set<Suggestion> configureAndGetLombokCompletionSuggestions(CompletionType completionType) {
    LookupElement[] elements = configureAndGetAllCompletionSuggestions(completionType);
    return filterLombokSuggestions(elements);
  }

  private LookupElement[] configureAndGetAllCompletionSuggestions(CompletionType completionType) {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(completionType);

    LookupElement[] lookupElements = myFixture.getLookupElements();
    assertNotNull("Element should not be autocompleted as there should be more than one element.", lookupElements);
    return lookupElements;
  }

  private static Suggestion renderCompletionSuggestion(LookupElement lookupElement) {
    LookupElementPresentation presentation = new LookupElementPresentation();
    lookupElement.renderElement(presentation);
    return new Suggestion(
      lookupElement.getLookupString(),
      presentation.getItemText(),
      presentation.getTailText(),
      presentation.getTypeText()
    );
  }

  private static Set<Suggestion> filterLombokSuggestions(LookupElement[] lookupElements) {
    return Arrays.stream(lookupElements)
      .filter(lookupElement -> lookupElement.getObject() instanceof LombokExtensionMethod)
      .map(LombokExtensionMethodCompletionContributorTest::renderCompletionSuggestion)
      .collect(Collectors.toSet());
  }

  private static Suggestion suggestion(String lookupString, String tailText, String typeText) {
    return new Suggestion(lookupString, lookupString, tailText, typeText);
  }

  private record Suggestion(String lookupString, String itemText, String tailText, String typeText) {
  }
}
