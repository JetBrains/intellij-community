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
package org.jetbrains.plugins.ipnb.format;

import com.google.common.collect.Lists;
import org.jetbrains.plugins.ipnb.Data;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.MarkdownCell;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;

import java.util.List;

/**
 * @author traff
 */
public class TestData {

  public static final MarkdownCell M1 = markdown(Data.MARKDOWN1.split("\n"));
  public static final CodeCell C1 = new CodeCellBuilder().input(Data.CODE1.split("\n")).output("1")
    .build();
  public static final IpnbFile IPNB_FILE1 = new IpnbFile(Lists.newArrayList(M1,
                                                                            C1, M1, C1, M1, C1, M1, C1
  ));

  public static MarkdownCell markdown(String... lines) {
    return new MarkdownCell(lines);
  }

  static class CodeCellBuilder {
    private String[] input;
    private List<CellOutput> output;

    CodeCellBuilder input(String... lines) {
      input = lines;
      return this;
    }

    CodeCellBuilder output(String... lines) {
      output = Lists.newArrayList();
      output.add(new CellOutput(lines));
      return this;
    }

    public CodeCell build() {
      return new CodeCell("python", input, 1, output);
    }
  }
}
