// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.impl.DomTestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.util.xml.impl.DomTestFixtures.domModuleFixture;
import static com.intellij.util.xml.impl.DomTestFixtures.domTestFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a tag which the DOM writes respects {@link WriteBefore} and {@link WriteAfter}.
 */
@TestApplication
public class DomWriteOrderTest {
  private static final TestFixture<Module> moduleFixture = domModuleFixture();
  private final TestFixture<DomTestFixture> domFixture = domTestFixture(moduleFixture);

  @Test
  public void testWriteAfterKeepsTheAnchorFirst() {
    assertOrder("<a><groupId/></a>", element -> element.getArtifactId().ensureTagExists(), "groupId", "artifactId");
  }

  @Test
  public void testWriteAfterIsInvertedForTheAnchor() {
    // groupId declares no order, but artifactId declares @WriteAfter("groupId")
    assertOrder("<a><artifactId/></a>", element -> element.getGroupId().ensureTagExists(), "groupId", "artifactId");
  }

  @Test
  public void testWriteBeforeKeepsTheAnchorLast() {
    assertOrder("<a><scope/></a>", element -> element.getType().ensureTagExists(), "type", "scope");
  }

  @Test
  public void testWriteBeforeIsInvertedForTheAnchor() {
    // scope declares no order, but type declares @WriteBefore("scope")
    assertOrder("<a><type/><comment/></a>", element -> element.getScope().ensureTagExists(), "type", "scope", "comment");
  }

  @Test
  public void testTagGoesBetweenBothAnchors() {
    assertOrder("<a><groupId/><exclusion/></a>", element -> element.getVersion().ensureTagExists(),
                "groupId", "version", "exclusion");
  }

  @Test
  public void testTagIsAppendedWithoutAnAnchor() {
    assertOrder("<a><comment/></a>", element -> element.getVersion().ensureTagExists(), "comment", "version");
  }

  @Test
  public void testChildWithoutAnOrderIsAppended() {
    assertOrder("<a><version/></a>", element -> element.getComment().ensureTagExists(), "version", "comment");
  }

  @Test
  public void testSetValueRespectsTheOrder() {
    assertOrder("<a><scope/></a>", element -> element.getType().setStringValue("jar"), "type", "scope");
  }

  @Test
  public void testCollectionChildRespectsTheOrder() {
    assertOrder("<a><version/><scope/></a>", MyCoordinates::addExclusion, "version", "exclusion", "scope");
  }

  @Test
  public void testCollectionChildrenStayTogether() {
    assertOrder("<a><version/><scope/></a>", element -> {
      element.addExclusion();
      element.addExclusion();
    }, "version", "exclusion", "exclusion", "scope");
  }

  @Test
  public void testOrderOfTheExistingTagsDoesNotChange() {
    // the file holds the tags in the order that no annotation requests, and only the new tag moves
    assertOrder("<a><version/><groupId/></a>", element -> element.getArtifactId().ensureTagExists(),
                "version", "groupId", "artifactId");
  }

  private void assertOrder(String xml, Consumer<MyCoordinates> write, String... expectedTagNames) {
    runInEdtAndWait(() -> {
      MyCoordinates element = domFixture.get().createElement(xml, MyCoordinates.class);
      WriteCommandAction.writeCommandAction(domFixture.get().getProject()).run(() -> write.accept(element));
      XmlTag tag = element.getXmlTag();
      assertEquals(List.of(expectedTagNames), ContainerUtil.map(tag.getSubTags(), XmlTag::getName), tag.getText());
    });
  }

  @NameStrategy(JavaNameStrategy.class)
  public interface MyCoordinates extends DomElement {
    GenericDomValue<String> getGroupId();

    @WriteAfter("groupId")
    GenericDomValue<String> getArtifactId();

    @WriteAfter("artifactId")
    @WriteBefore("exclusion")
    GenericDomValue<String> getVersion();

    @WriteBefore("scope")
    GenericDomValue<String> getType();

    GenericDomValue<String> getScope();

    GenericDomValue<String> getComment();

    List<GenericDomValue<String>> getExclusions();

    GenericDomValue<String> addExclusion();
  }
}
