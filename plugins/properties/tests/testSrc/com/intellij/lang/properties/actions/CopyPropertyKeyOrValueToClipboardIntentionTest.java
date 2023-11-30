package com.intellij.lang.properties.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public final class CopyPropertyKeyOrValueToClipboardIntentionTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.configureByText(PropertiesFileType.INSTANCE, "<caret>message.text=Hello, World! This value is quite long to display in preview");
  }

  public void testCopyPropertyValue() throws IOException, UnsupportedFlavorException {
    final String actionText = PropertiesBundle.message("copy.property.value.to.clipboard.intention.family.name");
    final Property property = getProperty();
    final String expected = property.getUnescapedValue();

    doTest(actionText, expected, "Copy to clipboard the string &quot;Hello, World! This value is quite lon...in preview&quot;");
  }

  public void testCopyPropertyKey() throws IOException, UnsupportedFlavorException {
    final String actionText = PropertiesBundle.message("copy.property.key.to.clipboard.intention.family.name");
    final Property property = getProperty();
    final String expected = property.getUnescapedKey();

    doTest(actionText, expected, "Copy to clipboard the string &quot;message.text&quot;");
  }

  private void doTest(String actionText, String expected, @Language("HTML") String expectedPreview) 
    throws UnsupportedFlavorException, IOException {
    final IntentionAction action = getAction(actionText);
    myFixture.checkIntentionPreviewHtml(action, expectedPreview);
    action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    final Object actual = CopyPasteManager.getInstance().getContents().getTransferData(DataFlavor.stringFlavor);

    assertThat(actual).as("Property value should be present in the clipboard").isEqualTo(expected);
  }

  private @NotNull Property getProperty() {
    final Property property = CopyPropertyValueToClipboardIntention.getProperty(myFixture.getActionContext());
    assert property != null : "A property at the caret not found";
    return property;
  }

  @NotNull
  private IntentionAction getAction(String actionText) {
    final List<IntentionAction> intentions = CodeInsightTestFixtureImpl.getAvailableIntentions(myFixture.getEditor(), myFixture.getFile());
    final Optional<IntentionAction> maybeIntention = intentions.stream().filter(e -> e.getText().equals(actionText)).findAny();
    assert maybeIntention.isPresent() : "The '" + actionText + "' intention hasn't been found among available intentions. Available intentions: " + intentions;
    return maybeIntention.get();
  }
}
