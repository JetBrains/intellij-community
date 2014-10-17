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
package com.jetbrains.python.inspections;

import javax.swing.*;
import java.awt.*;

/**
 * User : catherine
 */
public class PyEncodingUtil {

  private PyEncodingUtil() {
  }

  public static String[] POSSIBLE_ENCODINGS = new String[]{"ascii", "big5", "big5hkscs", "cp037", "cp424", "cp437", "cp500", "cp720", "cp737",
    "cp775", "cp850", "cp852", "cp855", "cp856", "cp857", "cp858", "cp860", "cp861", "cp862", "cp863", "cp864", "cp865", "cp866", "cp869", "cp874",
    "cp875", "cp932", "cp949", "cp950", "cp1006", "cp1026", "cp1140", "cp1250", "cp1251", "cp1252", "cp1253", "cp1254", "cp1255", "cp1256", "cp1257",
    "cp1258", "euc-jp", "euc-jis-2004", "euc-kr", "gb2312", "gbk", "gb18030", "hz", "iso2022-jp", "iso2022-jp-1", "iso2022-jp-2", "iso2022-jp-2004",
    "iso2022-jp-3", "iso2022-jp-ext", "iso2022-kr", "latin-1", "iso8859-2", "iso8859-3", "iso8859-4", "iso8859-5", "iso8859-6", "iso8859-7",
    "iso8859-8", "iso8859-9", "iso8859-10", "iso8859-13", "iso8859-14", "iso8859-15", "iso8859-16", "johab", "koi8-r", "koi8-u", "mac-cyrillic",
    "mac-greek", "mac-iceland", "mac-latin2", "mac-roman", "mac-turkish", "ptcp154", "shift-jis", "shift-jis-2004", "shift-jisx0213", "utf-32",
    "utf-32-be", "utf-32-le", "utf-16", "utf-16-be", "utf-16-le", "utf-7", "utf-8", "utf-8-sig"};

  public static String[] ENCODING_FORMAT = new String[]{"# coding=<encoding name>", "# -*- coding: <encoding name> -*-", "# vim: set fileencoding=<encoding name> :"};
  public static String[] ENCODING_FORMAT_PATTERN = new String[]{"# coding=%s", "# -*- coding: %s -*-", "# vim: set fileencoding=%s :"};

  public static JComponent createEncodingOptionsPanel(JComboBox defaultEncoding, JComboBox encodingFormat) {
    final JPanel optionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();

    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTH;
    c.gridx = 0;
    c.gridy = 0;
    final JLabel encodingLabel = new JLabel("Select default encoding: ");
    final JPanel encodingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    encodingPanel.add(encodingLabel);
    optionsPanel.add(encodingPanel, c);

    c.gridx = 1;
    c.gridy = 0;
    optionsPanel.add(defaultEncoding, c);

    c.gridx = 0;
    c.gridy = 1;
    c.weighty = 1;
    final JLabel formatLabel = new JLabel("Encoding comment format:");
    final JPanel formatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    formatPanel.add(formatLabel);
    optionsPanel.add(formatPanel, c);

    c.gridx = 1;
    c.gridy = 1;
    optionsPanel.add(encodingFormat, c);

    return optionsPanel;
  }
}
