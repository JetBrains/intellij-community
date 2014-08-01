/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.properties.ExternalsDefinitionParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Konstantin Kolosovsky.
 */
public class ExternalsDefinitionParserTest {

  @Test
  public void test_no_space_directory_with_revision() throws Exception {
    assertRelativeDirectory("third-party/skins", "-r148 http://svn.example.com/skinproj third-party/skins");
  }

  @Test
  public void test_no_space_directory_with_peg_revision() throws Exception {
    assertRelativeDirectory("third-party/skins", "http://svn.example.com/skinproj@148 third-party/skins");
  }

  @Test
  public void test_no_space_directory_without_revision() throws Exception {
    assertRelativeDirectory("third-party/sounds", "      http://svn.example.com/repos/sounds third-party/sounds");
  }

  @Test
  public void test_quoted_no_space_directory() throws Exception {
    assertRelativeDirectory("third-party/skins", "-r148 http://svn.example.com/skinproj \"third-party/skins\"");
  }

  @Test
  public void test_quoted_with_space_directory() throws Exception {
    assertRelativeDirectory("My Project", "http://svn.thirdparty.com/repos/My%20Project \"My Project\"");
  }

  @Test
  public void test_escaped_with_space_and_quotes_directory() throws Exception {
    assertRelativeDirectory("\"Quotes Too\"", "http://svn.thirdparty.com/repos/%22Quotes%20Too%22 \\\"Quotes\\ Too\\\"");
  }

  private static void assertRelativeDirectory(@NotNull String expected, @NotNull String external) throws Exception {
    assertEquals(expected, ExternalsDefinitionParser.parseRelativeDirectory(external));
  }
}
