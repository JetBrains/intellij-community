package org.jetbrains.yaml.refactoring.rename;

import com.intellij.refactoring.rename.RenameInputValidatorEx;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class YamlKeyValueRenameInputValidatorTest extends LightPlatformCodeInsightFixtureTestCase {
  private static final RenameInputValidatorEx INPUT_VALIDATOR = new YamlKeyValueRenameInputValidator();

  public void testInvalidNames() {
    doInvalidTest("");
    doInvalidTest(" ");
    doInvalidTest(" name");
    doInvalidTest("name ");
    doInvalidTest("name\t");
    doInvalidTest("\nname");
    doInvalidTest("\tname");
    doInvalidTest("\rname");
    doInvalidTest("? name");
    doInvalidTest(": name");
    doInvalidTest("- name");
    doInvalidTest(",name");
    doInvalidTest("[name");
    doInvalidTest("]name");
    doInvalidTest("{name");
    doInvalidTest("}name");
    doInvalidTest("#name");
    doInvalidTest("&name");
    doInvalidTest("*name");
    doInvalidTest("!name");
    doInvalidTest("|name");
    doInvalidTest(">name");
    doInvalidTest("'name");
    doInvalidTest("\"name");
    doInvalidTest("%name");
    doInvalidTest("@name");
    doInvalidTest("`name");
    doInvalidTest("name:");
    doInvalidTest("na #me");
    doInvalidTest("na: me");
  }

  public void testValidNames() {
    doValidTest("name");
    doValidTest("name with spaces");
    doValidTest("?name");
    doValidTest(":name");
    doValidTest("-name");
    doValidTest("name#");
    doValidTest("name:with:colons");
  }

  private void doInvalidTest(@NotNull final String name) {
    assertNotNull("Expected \"" + name + "\" to be an invalid name", INPUT_VALIDATOR.getErrorMessage(name, getProject()));
  }

  private void doValidTest(@NotNull final String name) {
    assertNull("Expected \"" + name + "\" to be a valid name", INPUT_VALIDATOR.getErrorMessage(name, getProject()));
  }
}
