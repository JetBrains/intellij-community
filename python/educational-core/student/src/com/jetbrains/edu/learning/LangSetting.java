/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.edu.learning;

import java.util.HashSet;
import java.util.Set;

public class LangSetting {
  private String currentLang;
  private Set<String> supportLangs ;


  public LangSetting() {
    supportLangs = new HashSet<>();
  }

  public LangSetting(String currentLang) {
    this.currentLang = currentLang;
    supportLangs = new HashSet<>();
    supportLangs.add(currentLang);
  }

  public LangSetting(String currentLang, Set<String> supportLangs) {
    this.currentLang = currentLang;
    this.supportLangs = new HashSet<>();
    this.supportLangs.addAll(supportLangs);
  }

  public LangSetting(Set<String> supportLangs) {
    this.supportLangs = new HashSet<>();
    this.supportLangs.addAll(supportLangs);
  }

  public String getCurrentLang() {
    return currentLang;
  }

  public void setCurrentLang(String currentLang) {
    this.currentLang = currentLang;
  }

  public Set<String> getSupportLangs() {
    return supportLangs;
  }

  public void setSupportLangs(Set<String> supportLangs) {
    this.supportLangs = supportLangs;
  }

}
