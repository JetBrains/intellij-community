/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import java.util.*;

public class MultiValuesMap<Key, Value>{
  private final Map<Key, Collection<Value>> myBaseMap = new HashMap<Key, Collection<Value>>();

  public void put(Key key, Value value) {
    if (!myBaseMap.containsKey(key)) {
      myBaseMap.put(key, new HashSet<Value>());
    }

    myBaseMap.get(key).add(value);
  }

  public Collection<Value> get(Key key){
    return myBaseMap.get(key);
  }

  public Set<Key> keySet() {
    return myBaseMap.keySet();
  }

  public Collection<Value> values() {
    Set<Value> result = new HashSet<Value>();
    Iterator<Collection<Value>> lists = myBaseMap.values().iterator();
    while (lists.hasNext()) {
      result.addAll(lists.next());
    }

    return result;
  }

  public void remove(Key key, Value value) {
    if (!myBaseMap.containsKey(key)) return;
    myBaseMap.get(key).remove(value);
  }
}
