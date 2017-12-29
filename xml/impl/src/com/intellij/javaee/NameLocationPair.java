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
package com.intellij.javaee;

/**
* @author Dmitry Avdeev
*/
public class NameLocationPair implements Comparable {
  final String myName;
  final String myLocation;
  boolean myShared;

  public NameLocationPair(String name, String location, boolean shared) {
    myName = name;
    myLocation = location;
    myShared = shared;
  }

  @Override
  public int compareTo(Object o) {
    return myName.compareTo(((NameLocationPair)o).myName);
  }

  public boolean equals(Object obj) {
    if (! (obj instanceof NameLocationPair)) return false;
    return compareTo(obj) == 0;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public String getName() {
    return myName;
  }

  public String getLocation(){
    return myLocation;
  }
}
