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

package org.intellij.plugins.relaxNG.convert;

import org.intellij.plugins.relaxNG.validation.MessageViewHelper;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

import com.thaiopensource.xml.sax.ErrorHandlerImpl;
import org.xml.sax.SAXParseException;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 16.11.2007
*/
public class IdeaErrorHandler extends ErrorHandlerImpl {
  private static final String CONTENT_NAME = "Convert Schema";
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("CONVERSION_ERRORS");

  private final MessageViewHelper myMessageViewHelper;

  public IdeaErrorHandler(Project project) {
    myMessageViewHelper = new MessageViewHelper(project, CONTENT_NAME, KEY);
  }

  @Override
  public void warning(SAXParseException e) throws SAXParseException {
    myMessageViewHelper.processError(e, true);
  }

  @Override
  public void error(SAXParseException e) {
    myMessageViewHelper.processError(e, false);
  }

  @Override
  public void fatalError(SAXParseException e) throws SAXParseException {
    myMessageViewHelper.processError(e, false);
  }
}
