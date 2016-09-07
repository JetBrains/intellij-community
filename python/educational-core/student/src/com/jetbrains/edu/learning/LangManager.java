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

import com.jetbrains.edu.learning.courseFormat.Task;

import java.util.HashMap;
import java.util.Map;

public class LangManager  {
  private Map<Integer, LangSetting> langSettingsMap;

  public LangManager() {
    langSettingsMap = new HashMap<>();
  }

  public Map<Integer, LangSetting> getLangSettingsMap() {
    return langSettingsMap;
  }

  public void setLangSettingsMap(Map<Integer, LangSetting> langSettingsMap) {
    this.langSettingsMap = langSettingsMap;
  }

  public LangSetting getLangSetting(Task task){
    return getLangSetting(task.getStepikId());
  }

  public LangSetting getLangSetting(int stepId){
    return langSettingsMap.get(stepId);
  }

  public void setLangSetting(Task task, LangSetting langSetting){
    setLangSetting(task.getStepikId(), langSetting);
  }

  public void setLangSetting(int stepId, LangSetting langSetting){
    langSettingsMap.put(stepId, langSetting);
  }
}
