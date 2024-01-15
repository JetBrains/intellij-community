// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonBySchemaHeavyCompletionTest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class YamlByJsonSchemaHeavyCompletionTest extends JsonBySchemaHeavyCompletionTest {
  @NotNull
  @Override
  public String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data/completion";
  }

  @Override
  protected String getBasePath() {
    return ""; // unused
  }

  @Override
  protected String getExtensionWithoutDot() {
    return "yml";
  }

  @Override
  public void testEditingSchemaAffectsCompletion() throws Exception {
    baseTest(getTestName(true), "testEditing", () -> {
      complete();
      assertContains("preserve", "react", "react-native");
      final PsiFile schema = myFixture.getFile().getParent().findFile("Schema.json");
      CommandProcessor.getInstance().runUndoTransparentAction(
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
      );
      VfsUtil.markDirtyAndRefresh(false, false, true, schema.getVirtualFile());
      schema.getVirtualFile().refresh(false, false);
      complete();
      assertContains("completelyChanged", "preserve", "react");
    });
  }

  @Override
  public void testOneOfWithNotFilledPropertyValue() throws Exception {
    baseTest("oneOfWithEnumValue", "oneOfWithEmptyPropertyValue", () -> {
      complete();
      assertContains("business", "home");
    });
  }

  @Override
  public void testObjectLiteral() throws Exception {
    baseInsertTest("insertArrayOrObjectLiteral", "objectLiteral");
    complete();
    assertStringItems("insideTopObject1", "insideTopObject2");
  }

  @Override
  public void testRequiredPropsFirst() {
    // do nothing
  }

  @Override
  public void testRequiredPropsLast() {
    // do nothing
  }

  @Override
  public void testWhitespaceAfterColon() {
    // do nothing
  }

  public void testInsertArrayItemNested() throws Exception {
    baseInsertTest("insertArrayItemNested", "test");
  }

  public void testInsertColonAfterPropName() throws Exception {
    baseInsertTest("insertColonAfterPropName", "test");
  }

  protected void assertContains(String... strings) {
    assertNotNull(myItems);
    List<String> actual = ContainerUtil.map(myItems, element -> element.getLookupString());
    assertContainsOrdered(actual, strings);
  }

  public void testPreserveColonWs() throws Exception {
    baseReplaceTest("preserveColon", "testReplaceStringAndWhitespace");
  }

  public void testPreserveColonInsertWsString() throws Exception {
    baseInsertTest("preserveColon", "testInsertStringAndWhitespace");
  }

  public void testPreserveColonInsertWsNonString() throws Exception {
    baseInsertTest("preserveColon", "testInsertNonStringAndWhitespace");
  }

  public void testInsertNumericValue() throws Exception {
    baseInsertTest("insertNumericValue", "testInsertNumericValue");
  }
}
