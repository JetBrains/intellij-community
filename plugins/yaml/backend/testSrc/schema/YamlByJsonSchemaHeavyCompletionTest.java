// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonBySchemaHeavyCompletionTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/yaml/backend/testData/org/jetbrains/yaml/schema/data/completion")
public class YamlByJsonSchemaHeavyCompletionTest extends JsonBySchemaHeavyCompletionTest {
  @Override
  protected String getExtensionWithoutDot() {
    return "yml";
  }

  @Test
  @Override
  public void testEditingSchemaAffectsCompletion() {
    mySchemaFixture.baseTest(mySchemaFixture.getTestName(true), "testEditing", () -> {
      mySchemaFixture.complete();
      assertContains("preserve", "react", "react-native");
      final PsiFile schema = ReadAction.computeBlocking(() -> myFixture.getFile().getParent().findFile("Schema.json"));
      EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().runUndoTransparentAction(
        () -> WriteAction.run(
          () -> {
            try {
              schema.getVirtualFile().setBinaryContent(
                """
                  {
                    "properties": {
                      "jsx": {
                        "oneOf": [
                          {"enum": [ "preserve", "react" ]},
                          {"enum": [ "completelyChanged" ]}
                        ]
                      }
                    }
                  }
                  """.getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        )
      ));
      VfsUtil.markDirtyAndRefresh(false, false, true, schema.getVirtualFile());
      schema.getVirtualFile().refresh(false, false);
      mySchemaFixture.complete();
      assertContains("completelyChanged", "preserve", "react");
    });
  }

  @Test
  @Override
  public void testOneOfWithNotFilledPropertyValue() {
    mySchemaFixture.baseTest("oneOfWithEnumValue", "oneOfWithEmptyPropertyValue", () -> {
      mySchemaFixture.complete();
      assertContains("business", "home");
    });
  }

  @Test
  @Override
  public void testObjectLiteral() {
    mySchemaFixture.baseInsertTest("insertArrayOrObjectLiteral", "objectLiteral");
    mySchemaFixture.complete();
    mySchemaFixture.assertStringItems("insideTopObject1", "insideTopObject2");
  }

  @Test
  @Override
  public void testRequiredPropsFirst() {
    // do nothing
  }

  @Test
  @Override
  public void testRequiredPropsLast() {
    // do nothing
  }

  @Test
  @Override
  public void testWhitespaceAfterColon() {
    // do nothing
  }

  @Test
  public void testInsertArrayItemNested() {
    mySchemaFixture.baseInsertTest("insertArrayItemNested", "test");
  }

  @Test
  public void testInsertColonAfterPropName() {
    mySchemaFixture.baseInsertTest("insertColonAfterPropName", "test");
  }

  protected void assertContains(String... strings) {
    LookupElement[] items = mySchemaFixture.getItems();
    Assertions.assertNotNull(items);
    List<String> expected = List.of(strings);
    List<String> actual = ContainerUtil.filter(ContainerUtil.map(items, element -> element.getLookupString()), expected::contains);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void testPreserveColonWs() {
    mySchemaFixture.baseReplaceTest("preserveColon", "testReplaceStringAndWhitespace");
  }

  @Test
  public void testPreserveColonInsertWsString() {
    mySchemaFixture.baseInsertTest("preserveColon", "testInsertStringAndWhitespace");
  }

  @Test
  public void testPreserveColonInsertWsNonString() {
    mySchemaFixture.baseInsertTest("preserveColon", "testInsertNonStringAndWhitespace");
  }

  @Test
  public void testInsertNumericValue() {
    mySchemaFixture.baseInsertTest("insertNumericValue", "testInsertNumericValue");
  }

  @Test
  @Override
  public void testEnumOrderSensitive() {
    mySchemaFixture.baseCompletionTest(mySchemaFixture.getTestName(true), "testValue", "c", "b", "a");
  }
}
