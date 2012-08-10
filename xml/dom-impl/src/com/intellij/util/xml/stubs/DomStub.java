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

import com.intellij.openapi.util.Condition;
import com.intellij.psi.stubs.ObjectStubBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import com.intellij.util.xml.EvaluatedXmlNameImpl;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.DomChildDescriptionImpl;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 8/2/12
 */
public abstract class DomStub extends ObjectStubBase<DomStub> {

  protected final StringRef myName;
  private DomInvocationHandler myHandler;

  public DomStub(DomStub parent, StringRef name) {
    super(parent);
    if (parent != null) {
      ((ElementStub)parent).addChild(this);
    }
    myName = name;
  }

  public abstract List<DomStub> getChildrenStubs();

  public int getChildIndex(DomStub child) {
    List<DomStub> stubs = getChildrenByName(child.getName());
    return stubs.indexOf(child);
  }

  public String getName() {
    return myName.getString();
  }

  public List<DomStub> getChildrenByName(final String localName) {
    return ContainerUtil.filter(getChildrenStubs(), new Condition<DomStub>() {
      @Override
      public boolean value(DomStub stub) {
        return stub.getName().equals(localName);
      }
    });
  }

  @Nullable
  public AttributeStub getAttributeStub(final XmlName name) {
    return (AttributeStub)ContainerUtil.find(getChildrenStubs(), new Condition<DomStub>() {
      @Override
      public boolean value(DomStub o) {
        return o instanceof AttributeStub && o.getName().equals(name.getLocalName());
      }
    });
  }

  public synchronized DomInvocationHandler getOrCreateHandler(DomChildDescriptionImpl description, DomManagerImpl manager) {
    if (myHandler == null) {
      XmlName name = description.getXmlName();
      EvaluatedXmlNameImpl evaluatedXmlName = EvaluatedXmlNameImpl.createEvaluatedXmlName(name, name.getNamespaceKey(), true);
      myHandler = new DomInvocationHandler(description.getType(), new StubParentStrategy(this), evaluatedXmlName, description, manager, false, this) {
        @Override
        protected void undefineInternal() {
        }

        @Override
        protected XmlTag setEmptyXmlTag() {
          return null;
        }
      };
    }
    return myHandler;
  }

  public DomInvocationHandler getHandler() {
    return myHandler;
  }

  public void setHandler(DomInvocationHandler handler) {
    myHandler = handler;
  }
}
