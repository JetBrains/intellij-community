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
package com.intellij.util.xml.stubs;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomParentStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *         Date: 8/9/12
 */
public class StubParentStrategy implements DomParentStrategy {

  public static StubParentStrategy createAttributeStrategy(@Nullable AttributeStub stub, @NotNull DomStub parent) {
    if (stub == null) {
      return new StubParentStrategy(parent) {
        @Override
        public DomInvocationHandler getParentHandler() {
          return myStub.getHandler();
        }

        @Override
        public XmlElement getXmlElement() {
          return null;
        }
      };
    }
    else {
      return new StubParentStrategy(stub) {
        @Override
        public XmlElement getXmlElement() {
          XmlTag tag = myStub.getParentStub().getHandler().getXmlTag();
          return tag == null ? null : tag.getAttribute(myStub.getName());
        }
      };
    }
  }

  protected final DomStub myStub;

  public StubParentStrategy(DomStub stub) {
    myStub = stub;
  }

  @Override
  public DomInvocationHandler getParentHandler() {
    DomStub parentStub = myStub.getParentStub();
    return parentStub == null ? null : parentStub.getHandler();
  }

  @Override
  public XmlElement getXmlElement() {
    DomStub parentStub = myStub.getParentStub();
    if (parentStub == null) return null;
    int index = parentStub.getChildIndex(myStub);
    XmlTag tag = parentStub.getHandler().getXmlTag();
    if (tag == null) return null;
    XmlTag[] subTags = tag.findSubTags(myStub.getName());

    return index < 0 || index >= subTags.length ? null : subTags[index];
  }

  @NotNull
  @Override
  public DomParentStrategy refreshStrategy(DomInvocationHandler handler) {
    return this;
  }

  @NotNull
  @Override
  public DomParentStrategy setXmlElement(@NotNull XmlElement element) {
    return this;
  }

  @NotNull
  @Override
  public DomParentStrategy clearXmlElement() {
    return this;
  }

  @Override
  public String checkValidity() {
    return null;
  }

  @Override
  public XmlFile getContainingFile(DomInvocationHandler handler) {
    return getParentHandler().getFile();
  }
}
