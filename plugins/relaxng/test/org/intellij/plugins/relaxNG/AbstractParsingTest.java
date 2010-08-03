/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.ParsingTestCase;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 24.08.2007
*/
public abstract class AbstractParsingTest extends ParsingTestCase {
  public AbstractParsingTest(String s) {
    super(s, "rnc");
  }

  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("relaxng") + "/testData/parsing";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new WriteAction() {
      protected void run(Result result) throws Throwable {
        new ApplicationLoader().initComponent();
        ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("urn:test:undefined");
      }
    }.execute();
  }
}