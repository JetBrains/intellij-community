// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.psi.LombokExtensionMethod;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.completion.CompletionType.BASIC;
import static com.intellij.codeInsight.completion.CompletionType.SMART;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NotNullByDefault
@TestApplication
public class LombokExtensionMethodCompletionContributorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/completion/extensionMethod/LombokExtensionMethodCompletionContributor");

  @Test
  public void testStringReceiverAndStringArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  @Test
  public void testObjectReceiverAndStringArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  @Test
  public void testStringReceiverAndObjectArgument() {
    doTestBothCompletionTypes();
  }

  @Test
  public void testWildcardListReceiverAndListArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  @Test
  public void testStringReceiverAndWildcardWithUpperBoundStringArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  @Test
  public void testMoreThanTwoArguments() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "(String b, String c)", "void"));
  }

  @Test
  public void testLowerBoundedListReceiver() {
    doTestBothCompletionTypes();
  }

  @Test
  public void testVarargReceiverAndArrayArgument() {
    doTestBothCompletionTypes(suggestion("myExtensionMethod", "()", "void"));
  }

  @Test
  public void testVarargReceiverAndSingleElemArgument() {
    doTestBothCompletionTypes();
  }

  @Test
  public void testMatchingExpressionType() {
    doTest(BASIC,
           suggestion("myIntegerMethod", "()", "Integer"),
           suggestion("myStringMethod", "()", "String"));
    doTest(SMART, suggestion("myIntegerMethod", "()", "Integer"));
  }

  @Test
  public void testMatchingExpressionTypeGenericResult() {
    doTest(BASIC, suggestion("myGenericMethod", "(Class<T> type)", "T"));
    doTest(SMART, suggestion("myGenericMethod", "(Class<T> type)", "T"));
  }

  @Test
  public void testMatchingExpressionTypeGenericResultWithWrongBounds() {
    doTest(BASIC, suggestion("myBoundedGenericMethod", "(Class<T> type)", "T"));
    doTest(SMART);
  }

  @Test
  public void testGenericReceiverAndParamContributingToGenericResult() {
    doTest(BASIC, suggestion("pairWith", "(S e2)", "Pair<String, S>"));
    doTest(SMART, suggestion("pairWith", "(S e2)", "Pair<String, S>"));
  }

  @Test
  public void testWithPrefix() {
    doTest(BASIC,
           suggestion("myExtensionMethod", "()", "void"),
           suggestion("myExtensionMethod2", "()", "void"));
    doTest(SMART,
           suggestion("myExtensionMethod", "()", "void"),
           suggestion("myExtensionMethod2", "()", "void"));
  }

  @Test
  public void testNonStaticExtensionMethod() {
    doTestBothCompletionTypes();
  }

  @Test
  public void testNonPublicExtensionMethod() {
    doTestBothCompletionTypes();
  }

  @Test
  public void testMalformedExtensionMethodWithoutReturnType() {
    doTestBothCompletionTypes();
  }

  @Test
  public void testSameNameButDifferentSignatureThanInstanceMethod() {
    doTestBothCompletionTypes(suggestion("myInstanceMethod", "(String value, int x)", "void"));
  }

  @Test
  public void testSameSignatureAsInstanceMethod() {
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(BASIC);
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(SMART);
  }

  @Test
  public void testSameSignatureAsSuperClassInstanceMethod() {
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(SMART);
    extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(BASIC);
  }

  private void extensionMethodIsNotSuggestedWhenReceiverSuperClassHasSameInstanceMethod(CompletionType completionType) {
    LookupElement[] lookupElements = configureAndGetAllCompletionSuggestions(completionType);
    List<String> allSuggestions = ContainerUtil.map(lookupElements, LookupElement::getLookupString);

    assertThat(allSuggestions).contains("myInstanceMethod");
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.complete(completionType);

    LookupElement[] lookupElements = myFixture.getLookupElements();
    assertNotNull(lookupElements, "Element should not be autocompleted as there should be more than one element.");
    return lookupElements;
  }

  private static Suggestion renderCompletionSuggestion(LookupElement lookupElement) {
    return ReadAction.computeBlocking(() -> {
      LookupElementPresentation presentation = new LookupElementPresentation();
      lookupElement.renderElement(presentation);
      return new Suggestion(
        lookupElement.getLookupString(),
        presentation.getItemText(),
        presentation.getTailText(),
        presentation.getTypeText()
      );
    });
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
