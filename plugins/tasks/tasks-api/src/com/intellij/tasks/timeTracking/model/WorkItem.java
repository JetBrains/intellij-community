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
package com.intellij.tasks.timeTracking.model;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.Date;

/**
 * User: evgeny.zakrevsky
 * Date: 12/27/12
 */
@Tag("workItem")
public class WorkItem {
  @Attribute("from")
  public Date from;
  @Attribute("duration")
  public long duration;

  /** For serialization */
  @SuppressWarnings({"UnusedDeclaration"})
  public WorkItem() {
  }

  public WorkItem(final Date from) {
    this.from = from;
  }
}
