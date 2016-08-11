/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import junit.framework.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/5/12
 * Time: 12:52 PM
 */
public class XmlSchemaTypeInheritanceTest extends CodeInsightFixtureTestCase {
  private final static String ourNs = "http://www.omg.org/spec/BPMN/20100524/MODEL";

  @Test
  public void testBuilder() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("Semantic.xsd");
    assert file != null;
    final FileInputStream is = new FileInputStream(new File(file.getPath()));
    final MultiMap<SchemaTypeInfo, SchemaTypeInfo> map = XsdComplexTypeInfoBuilder.parse(is);

    final Collection<SchemaTypeInfo> node = map.get(new SchemaTypeInfo("tConversationNode", true, ourNs));
    Assert.assertNotNull(node);
    Assert.assertEquals(3, node.size());

    final Set<SchemaTypeInfo> expected = new HashSet<>();
    expected.add(new SchemaTypeInfo("tConversation", true, ourNs));
    expected.add(new SchemaTypeInfo("tCallConversation", true, ourNs));
    expected.add(new SchemaTypeInfo("tSubConversation", true, ourNs));
    for (SchemaTypeInfo info : node) {
      expected.remove(info);
    }
    Assert.assertTrue(expected.isEmpty());
    //
    final Collection<SchemaTypeInfo> stringNode = map.get(new SchemaTypeInfo("string", true, "http://www.w3.org/2001/XMLSchema"));
    Assert.assertNotNull(stringNode);
    Assert.assertEquals(9, stringNode.size());
    Assert.assertTrue(stringNode.contains(new SchemaTypeInfo("tAdHocOrdering", true, ourNs)));
    Assert.assertTrue(stringNode.contains(new SchemaTypeInfo("tEventBasedGatewayType", true, ourNs)));
    //
    final Collection<SchemaTypeInfo> baseNode = map.get(new SchemaTypeInfo("tBaseElement", true, ourNs));
    Assert.assertNotNull(baseNode);
    Assert.assertEquals(39, baseNode.size());
    Assert.assertTrue(baseNode.contains(new SchemaTypeInfo("tAuditing", true, ourNs)));
    Assert.assertTrue(baseNode.contains(new SchemaTypeInfo("tDataInput", true, ourNs)));
    Assert.assertTrue(baseNode.contains(new SchemaTypeInfo("tDataOutput", true, ourNs)));
    Assert.assertTrue(baseNode.contains(new SchemaTypeInfo("tFlowElement", true, ourNs)));
  }

  @Test
  public void testIndex() throws Exception {
    myFixture.copyDirectoryToProject("", "");

    final Project project = getProject();
    final List<Set<SchemaTypeInfo>> childrenOfType = SchemaTypeInheritanceIndex.getWorker(project, null).convert("http://a.b.c", "baseSimpleType");
    Assert.assertNotNull(childrenOfType);

    final Set<SchemaTypeInfo> expected = new HashSet<>();
    expected.add(new SchemaTypeInfo("extSimple4", true, "http://a.b.c"));
    expected.add(new SchemaTypeInfo("extSimple1", true, "http://a.b.c"));
    expected.add(new SchemaTypeInfo("extComplex2", true, "http://a.b.c"));
    expected.add(new SchemaTypeInfo("extComplex2", true, "http://a.b.c.d"));

    for (Set<SchemaTypeInfo> infos : childrenOfType) {
      for (SchemaTypeInfo info : infos) {
        expected.remove(info);
      }
    }

    Assert.assertTrue(expected.isEmpty());
    //
    final List<Set<SchemaTypeInfo>> childrenOfSimple4Type = SchemaTypeInheritanceIndex.getWorker(project, null).convert("http://a.b.c", "extSimple4");
    Assert.assertNotNull(childrenOfSimple4Type);
    final Set<SchemaTypeInfo> expectedSimple4 = new HashSet<>();
    expectedSimple4.add(new SchemaTypeInfo("extSimple5", true, "http://a.b.c"));
    expectedSimple4.add(new SchemaTypeInfo("wiseElement", false, "http://a.b.c"));

    for (Set<SchemaTypeInfo> infos : childrenOfSimple4Type) {
      for (SchemaTypeInfo info : infos) {
        expectedSimple4.remove(info);
      }
    }

    Assert.assertTrue(expectedSimple4.isEmpty());
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/index";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}
