/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.pom.tree.events;

import com.intellij.lang.ASTNode;
import com.intellij.pom.event.PomChangeSet;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Oct 6, 2004
 * Time: 10:56:52 PM
 * To change this template use File | Settings | File Templates.
 */
public interface TreeChangeEvent extends PomChangeSet{
  ASTNode getRootElement();
  ASTNode[] getChangedElements();
  TreeChange getChangesByElement(ASTNode element);

  void addElementaryChange(ASTNode child, ChangeInfo change);
  void clear();
}
