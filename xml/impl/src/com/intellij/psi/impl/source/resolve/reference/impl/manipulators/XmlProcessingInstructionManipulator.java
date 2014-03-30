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
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 2/20/13
 */
public class XmlProcessingInstructionManipulator extends AbstractElementManipulator<XmlProcessingInstruction> {

  @Override
  public XmlProcessingInstruction handleContentChange(@NotNull XmlProcessingInstruction element, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    return XmlAttributeValueManipulator.handleContentChange(element, range, newContent, XmlTokenType.XML_TAG_CHARACTERS);
  }
}
