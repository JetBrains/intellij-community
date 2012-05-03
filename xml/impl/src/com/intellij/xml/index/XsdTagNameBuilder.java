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
package com.intellij.xml.index;

import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class XsdTagNameBuilder extends NanoXmlUtil.IXMLBuilderAdapter {

  @Nullable
  public static Collection<String> computeTagNames(final InputStream is) {
    return computeTagNames(new InputStreamReader(is));
  }

  @Nullable
  public static Collection<String> computeTagNames(final Reader reader) {
    try {
      final XsdTagNameBuilder builder = new XsdTagNameBuilder();
      NanoXmlUtil.parse(reader, builder);
      return builder.myTagNames;
    }
    finally {
      try {
        if (reader != null) {
          reader.close();
        }
      }
      catch (IOException e) {
        // can never happen
      }
    }
  }

  private final Collection<String> myTagNames = new ArrayList<String>();
  private boolean myElementStarted;

  public void startElement(@NonNls final String name, @NonNls final String nsPrefix, @NonNls final String nsURI, final String systemID, final int lineNr)
      throws Exception {

    myElementStarted = nsPrefix != null && nsURI.equals("http://www.w3.org/2001/XMLSchema") && name.equals("element");
  }

  public void addAttribute(@NonNls final String key, final String nsPrefix, final String nsURI, final String value, final String type)
      throws Exception {
    if (myElementStarted && key.equals("name")) {
      myTagNames.add(value);
      myElementStarted = false;
    }
  }
}
