package org.jetbrains.plugins.ipnb.format;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IpnbParser {
  private static final Logger LOG = Logger.getInstance(IpnbParser.class);
  private static final Gson gson = initGson();

  @NotNull
  private static Gson initGson() {
    final GsonBuilder builder = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping();
    return builder.create();
  }

  @NotNull
  public static IpnbFile parseIpnbFile(@NotNull String fileText, String path) throws IOException {
    IpnbFileRaw rawFile = gson.fromJson(fileText, IpnbFileRaw.class);
    if (rawFile == null) return new IpnbFile(new IpnbFileRaw(), Lists.<IpnbCell>newArrayList(), path);
    List<IpnbCell> cells = new ArrayList<IpnbCell>();
    final IpnbWorksheet[] worksheets = rawFile.worksheets;
    for (IpnbWorksheet worksheet : worksheets) {
      final IpnbCellRaw[] rawCells = worksheet.cells;
      for (IpnbCellRaw rawCell : rawCells) {
        cells.add(rawCell.createCell());
      }
    }
    return new IpnbFile(rawFile, cells, path);
  }

  @NotNull
  public static IpnbFile parseIpnbFile(@NotNull VirtualFile virtualFile) throws IOException {
    final String fileText = new String(virtualFile.contentsToByteArray(), CharsetToolkit.UTF8);
    return parseIpnbFile(fileText, virtualFile.getPath());
  }

  public static void saveIpnbFile(@NotNull final IpnbFile ipnbFile) {
    final IpnbFileRaw fileRaw = ipnbFile.getRawFile();
    final IpnbWorksheet worksheet = new IpnbWorksheet();
    final ArrayList<IpnbCellRaw> cellRaws = new ArrayList<IpnbCellRaw>();
    for (IpnbCell cell: ipnbFile.getCells()) {
      cellRaws.add(IpnbCellRaw.fromCell(cell));
    }
    worksheet.cells = cellRaws.toArray(new IpnbCellRaw[cellRaws.size()]);
    fileRaw.worksheets = new IpnbWorksheet[]{worksheet};
    final String json = gson.toJson(fileRaw);
    final String path = ipnbFile.getPath();
    final File file = new File(path);
    FileWriter writer = null;
    try {
      writer = new FileWriter(file);
      writer.write(json);
    } catch (IOException e) {
      LOG.error(e);
    }
    finally {
      try {
        if (writer != null)
          writer.close();
      } catch (IOException e1) {
        LOG.error(e1);
      }
    }
  }

  public static class IpnbFileRaw {
    Map<String, String> metadata;
    int nbformat;
    int nbformat_minor;
    IpnbWorksheet[] worksheets;
  }

  private static class IpnbWorksheet {
    IpnbCellRaw[] cells;
  }
  private static class IpnbCellRaw {
    String cell_type;
    Integer level;
    String[] source;
    String[] input;
    String language;
    CellOutputRaw[] outputs;
    Integer prompt_number;

    public static IpnbCellRaw fromCell(@NotNull final IpnbCell cell) {
      final IpnbCellRaw raw = new IpnbCellRaw();
      if (cell instanceof MarkdownCell) {
        raw.cell_type = "markdown";
        raw.source = ((MarkdownCell)cell).getSource();
      }
      else if (cell instanceof CodeCell) {
        raw.cell_type = "code";
        final ArrayList<CellOutputRaw> outputRaws = new ArrayList<CellOutputRaw>();
        for (CellOutput cellOutput : ((CodeCell)cell).getCellOutputs()) {
          outputRaws.add(CellOutputRaw.fromOutput(cellOutput));
        }
        raw.outputs = outputRaws.toArray(new CellOutputRaw[outputRaws.size()]);
        raw.language = ((CodeCell)cell).getLanguage();
        raw.input = ((CodeCell)cell).getSource();
        raw.prompt_number = ((CodeCell)cell).getPromptNumber();
      }
      else if (cell instanceof RawCell) {
        raw.cell_type = "raw";
      }
      else if (cell instanceof HeadingCell) {
        raw.cell_type = "heading";
        raw.source = ((HeadingCell)cell).getSource();
        raw.level = ((HeadingCell)cell).getLevel();
      }
      return raw;
    }

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
    String ename;
    String evalue;
    String output_type;
    String png;
    String stream;
    String jpeg;
    String[] html;
    String[] latex;
    String[] svg;
    Integer prompt_number;
    String[] text;
    String[] traceback;


    public static CellOutputRaw fromOutput(@NotNull final CellOutput cellOutput) {
      final CellOutputRaw raw = new CellOutputRaw();

      if (cellOutput instanceof PngCellOutput) {
        raw.png = ((PngCellOutput)cellOutput).getBase64String();
        raw.text = cellOutput.getText();
        //raw.output_type = "display_data";
      }
      else if (cellOutput instanceof SvgCellOutput) {
        raw.svg = ((SvgCellOutput)cellOutput).getSvg();
        raw.text = cellOutput.getText();
      }
      else if (cellOutput instanceof JpegCellOutput) {
        raw.jpeg = ((JpegCellOutput)cellOutput).getBase64String();
        raw.text = cellOutput.getText();
      }
      else if (cellOutput instanceof LatexCellOutput) {
        raw.latex = ((LatexCellOutput)cellOutput).getLatex();
        raw.prompt_number = ((LatexCellOutput)cellOutput).getPromptNumber();
        raw.text = cellOutput.getText();
      }
      else if (cellOutput instanceof StreamCellOutput) {
        raw.stream = ((StreamCellOutput)cellOutput).getStream();
        raw.output_type = "stream";
        raw.text = cellOutput.getText();
      }
      else if (cellOutput instanceof HtmlCellOutput) {
        raw.html = ((HtmlCellOutput)cellOutput).getHtmls();
        raw.text = cellOutput.getText();
      }
      else if (cellOutput instanceof ErrorCellOutput) {
        raw.output_type = "pyerr";
        raw.evalue = ((ErrorCellOutput)cellOutput).getEvalue();
        raw.ename = ((ErrorCellOutput)cellOutput).getEname();
        raw.traceback = cellOutput.getText();
      }
      else if (cellOutput instanceof OutCellOutput) {
        raw.output_type = "pyout";
        raw.text = cellOutput.getText();
        raw.prompt_number = ((OutCellOutput)cellOutput).getPromptNumber();
      }
      return raw;
    }

    public CellOutput createOutput() {
      final CellOutput cellOutput;
      if (png != null) {
        cellOutput = new PngCellOutput(png, text);
      }
      else if (jpeg != null) {
        cellOutput = new JpegCellOutput(jpeg, text);
      }
      else if (svg != null) {
        cellOutput = new SvgCellOutput(svg, text);
      }
      else if (latex != null) {
        cellOutput = new LatexCellOutput(latex, prompt_number, text);
      }
      else if (stream != null) {
        cellOutput = new StreamCellOutput(stream, text);
      }
      else if (html != null) {
        cellOutput = new HtmlCellOutput(html, text);
      }
      else if ("pyerr".equals(output_type)) {
        cellOutput = new ErrorCellOutput(evalue, ename, traceback);
      }
      else if ("pyout".equals(output_type)) {
        cellOutput = new OutCellOutput(text, prompt_number);
      }
      else {
        cellOutput = new CellOutput(text);
      }
      return cellOutput;
    }
  }
}
