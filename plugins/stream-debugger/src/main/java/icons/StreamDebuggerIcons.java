/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamDebuggerIcons {
  Icon RUN_STREAM_DEBUG_ACTION= IconLoader.getIcon("debugger_action.png");
  Icon VALUE_HIGHLIGHTED_ICON = IconLoader.getIcon("value_highlighting.png");
  Icon STREAM_CALL_TAB_ICON = IconLoader.getIcon("tab.png");
}
