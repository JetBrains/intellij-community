/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.xml.impl.dtd;

import com.intellij.psi.xml.XmlElementContentGroup;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementsGroupImpl implements XmlElementsGroup {

  private final XmlElementContentGroup myGroup;

  public XmlElementsGroupImpl(XmlElementContentGroup group) {
    myGroup = group;
  }

  @Override
  public int getMinOccurs() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public int getMaxOccurs() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public Type getGroupType() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public XmlElementsGroup getParentGroup() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public List<XmlElementsGroup> getSubGroups() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public XmlElementDescriptor getLeafDescriptor() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
