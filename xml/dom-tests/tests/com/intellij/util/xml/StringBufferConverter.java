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
package com.intellij.util.xml;

/**
 * @author peter
 */
public class StringBufferConverter extends Converter<StringBuffer> {
  @Override
  public StringBuffer fromString(final String s, final ConvertContext context) {
    return s==null?null:new StringBuffer(s);
  }

  @Override
  public String toString(final StringBuffer t, final ConvertContext context) {
    return t==null?null:t.toString();
  }

}
