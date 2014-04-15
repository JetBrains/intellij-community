package org.jetbrains.plugins.ipnb.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.LatexCellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.PngCellOutput;
import org.jetbrains.plugins.ipnb.format.cells.output.StreamCellOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IpnbParser {
  private static final Logger LOG = Logger.getInstance(IpnbParser.class);
  private static final Gson gson = initGson();

  @NotNull
  private static Gson initGson() {
    final GsonBuilder builder = new GsonBuilder();
    return builder.create();
  }

  @NotNull
  public static IpnbFile parseIpnbFile(@NotNull String fileText) throws IOException {
    IpnbFileRaw rawFile = gson.fromJson(fileText, IpnbFileRaw.class);
    List<IpnbCell> cells = new ArrayList<IpnbCell>();
    final IpnbWorksheet[] worksheets = rawFile.worksheets;
    for (IpnbWorksheet worksheet : worksheets) {
      final IpnbCellRaw[] rawCells = worksheet.cells;
      for (IpnbCellRaw rawCell : rawCells) {
        cells.add(rawCell.createCell());
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
    String[] input;
    String language;
    int level;
    int prompt_number;
    CellOutputRaw[] outputs;

    public IpnbCell createCell() {
      final IpnbCell cell;
      if (cell_type.equals("markdown")) {
        cell = new MarkdownCell(source);
      }
      else if (cell_type.equals("code")) {
        final List<CellOutput> cellOutputs = new ArrayList<CellOutput>();
        for (CellOutputRaw outputRaw : outputs) {
          cellOutputs.add(outputRaw.createOutput());
        }
        cell = new CodeCell(language, input, prompt_number, cellOutputs);
      }
      else if (cell_type.equals("raw")) {
        cell = new RawCell();
      }
      else if (cell_type.equals("heading")) {
        cell = new HeadingCell(source, level);
      }
      else {
        cell = null;
      }
      return cell;
    }
  }

  private static class CellOutputRaw {
    String output_type;
    String[] text;
    String png;
    String[] latex;
    String stream;
    int prompt_number;

    public CellOutput createOutput() {
      final CellOutput cellOutput;
      if (png != null) {
        cellOutput = new PngCellOutput(png, text);
      }
      else if (latex != null) {
        cellOutput = new LatexCellOutput(latex, prompt_number, text);
      }
      else if (stream != null) {
        cellOutput = new StreamCellOutput(stream, text);
      }
      else {
        cellOutput = new CellOutput(text);
      }
      return cellOutput;
    }
  }
}
