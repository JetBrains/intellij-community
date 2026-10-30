package org.jetbrains.yaml.refactoring.rename;

import com.intellij.refactoring.rename.RenameInputValidatorEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

public final class YamlKeyValueRenameInputValidatorTest extends BasePlatformTestCase {
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
    doValidTest("name##");
    doValidTest("name###");
    doValidTest("na##me");
  }

  public void testLongNames() {
    var longName = "1".repeat(100_000);
    doValidTest(longName);
    doValidTest("name ".repeat(20_000) + "name");
    doInvalidTest(longName + " ");
  }

  private void doInvalidTest(@NotNull final String name) {
    assertNotNull("Expected \"" + name + "\" to be an invalid name", INPUT_VALIDATOR.getErrorMessage(name, getProject()));
  }

  private void doValidTest(@NotNull final String name) {
    assertNull("Expected \"" + name + "\" to be a valid name", INPUT_VALIDATOR.getErrorMessage(name, getProject()));
  }
}
