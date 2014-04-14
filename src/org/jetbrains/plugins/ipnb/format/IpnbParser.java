package org.jetbrains.plugins.ipnb.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IpnbParser {
  private static final Logger LOG = Logger.getInstance(IpnbParser.class);
  private static final Gson gson = initGson();

  @NotNull
  private static Gson initGson() {
    GsonBuilder builder = new GsonBuilder();
    return builder.create();
  }

  @NotNull
  public static CodeCell parseIpnbCell(@NotNull String item)
    throws IOException {
    CodeCell ipnbCell = gson.fromJson(item, CodeCell.class);
    return ipnbCell;
  }

  @NotNull
  public static IpnbFile parseIpnbFile(@NotNull String fileText) throws IOException {
    IpnbFileRaw rawFile = gson.fromJson(fileText, IpnbFileRaw.class);
    List<IpnbCell> cells = new ArrayList<IpnbCell>();
    final IpnbWorksheet[] worksheets = rawFile.worksheets;
    for (IpnbWorksheet worksheet : worksheets) {
      final IpnbCellRaw[] rawCells = worksheet.cells;
      for (IpnbCellRaw rawCell : rawCells) {
        if (rawCell.cell_type.equals("markdown")) {
          final IpnbCell cell = new MarkdownCell();
          cells.add(cell);
        }
        else if (rawCell.cell_type.equals("code")) {
          final IpnbCell cell = new CodeCell();
          cells.add(cell);
        }
        else if (rawCell.cell_type.equals("raw")) {
          final IpnbCell cell = new RawCell();
          cells.add(cell);
        }
        else if (rawCell.cell_type.equals("heading")) {
          final IpnbCell cell = new HeadingCell();
          cells.add(cell);
        }
      }
    }
    return new IpnbFile(cells);
  }

  private static class IpnbFileRaw {
    IpnbWorksheet[] worksheets;
  }

  private static class IpnbWorksheet {
    IpnbCellRaw[] cells;
  }

  private static class IpnbCellRaw {
    String cell_type;
    String[] source;
    int level;
  }
}
