/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.branchConfig;

public enum InfoReliability {
  empty() {
    @Override
    protected final InfoReliability[] getOverriddenBy() {
      return new InfoReliability[] {defaultValues, setByUser};
    }},
  defaultValues() {
    @Override
    protected final InfoReliability[] getOverriddenBy() {
      return new InfoReliability[] {setByUser};
    }},
  setByUser() {
    @Override
    protected final InfoReliability[] getOverriddenBy() {
      return new InfoReliability[] {setByUser};
    }};

  protected abstract InfoReliability[] getOverriddenBy();

  public boolean shouldOverride(final InfoReliability other) {
    for (InfoReliability info : other.getOverriddenBy()) {
      if (info.equals(this)) return true;
    }
    return false;
  }
}
