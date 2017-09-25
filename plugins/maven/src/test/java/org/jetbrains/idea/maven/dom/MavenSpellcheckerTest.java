/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;

/**
 * @author Sergey Evdokimov
 */
public class MavenSpellcheckerTest extends MavenDomTestCase {

  public void testSpell() {
    myFixture.enableInspections(SpellCheckingInspection.class);

    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +

                     "<description><TYPO>xxxxx</TYPO></description>\n" +

                     "<dependencies>\n" +
                     "  <dependency>\n" +
                     "    <groupId>xxxxx</groupId>\n" +
                     "    <artifactId>xxxxx</artifactId>\n" +
                     "    <version>4.0</version>\n" +
                     "    <type>pom</type>\n" +
                     "  </dependency>\n" +
                     "</dependencies>");

    checkHighlighting();
  }

}
