package org.jetbrains.plugins.ipnb.format;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
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
  public static IpnbFile parseIpnbFile(@NotNull final CharSequence fileText, String path) throws IOException {
    IpnbFileRaw rawFile = gson.fromJson(fileText.toString(), IpnbFileRaw.class);
    if (rawFile == null) return new IpnbFile(new IpnbFileRaw(), Lists.<IpnbCell>newArrayList(), path);
    List<IpnbCell> cells = new ArrayList<IpnbCell>();
    final IpnbWorksheet[] worksheets = rawFile.worksheets;
    if (worksheets == null) {
      for (IpnbCellRaw rawCell : rawFile.cells) {
        cells.add(rawCell.createCell());
      }
    }
    else {
      for (IpnbWorksheet worksheet : worksheets) {
        final List<IpnbCellRaw> rawCells = worksheet.cells;
        for (IpnbCellRaw rawCell : rawCells) {
          cells.add(rawCell.createCell());
        }
      }
    }
    return new IpnbFile(rawFile, cells, path);
  }

  @NotNull
  public static IpnbFile parseIpnbFile(@NotNull Document document, @NotNull final String path) throws IOException {
    return parseIpnbFile(document.getImmutableCharSequence(), path);
  }

  public static void saveIpnbFile(@NotNull final IpnbFilePanel ipnbPanel) {
    final String json = newDocumentText(ipnbPanel);
    if (json == null) return;
    writeToFile(ipnbPanel.getIpnbFile().getPath(), json);
  }

  public static String newDocumentText(@NotNull final IpnbFilePanel ipnbPanel) {
    final IpnbFile ipnbFile = ipnbPanel.getIpnbFile();
    if (ipnbFile == null) return null;
    for (IpnbEditablePanel panel : ipnbPanel.getIpnbPanels()) {
      if (panel.isModified()) {
        panel.updateCellSource();
      }
    }

    final IpnbFileRaw fileRaw = ipnbFile.getRawFile();
    if (fileRaw.nbformat == 4) {
      fileRaw.cells.clear();
      for (IpnbCell cell: ipnbFile.getCells()) {
        fileRaw.cells.add(IpnbCellRaw.fromCell(cell, fileRaw.nbformat));
      }
    }
    else {
      final IpnbWorksheet worksheet = new IpnbWorksheet();
      worksheet.cells.clear();
      for (IpnbCell cell : ipnbFile.getCells()) {
        worksheet.cells.add(IpnbCellRaw.fromCell(cell, fileRaw.nbformat));
      }
      fileRaw.worksheets = new IpnbWorksheet[]{worksheet};
    }
    final StringWriter stringWriter = new StringWriter();
    final JsonWriter writer = new JsonWriter(stringWriter);
    writer.setIndent(" ");
    gson.toJson(fileRaw, fileRaw.getClass(), writer);
    return stringWriter.toString();
  }

  private static void writeToFile(@NotNull final String path, @NotNull final String json) {
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
      } catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public static class IpnbFileRaw {
    IpnbWorksheet[] worksheets;
    List<IpnbCellRaw> cells = new ArrayList<IpnbCellRaw>();
    Map<String, Object> metadata = new HashMap<String, Object>();
    int nbformat = 3;
    int nbformat_minor;
  }

  private static class IpnbWorksheet {
    List<IpnbCellRaw> cells = new ArrayList<IpnbCellRaw>();
  }

  private static class IpnbCellRaw {
    String cell_type;
    Integer execution_count;
    Map<String, Object> metadata = new HashMap<String, Object>();
    Integer level;
    CellOutputRaw[] outputs;
    String[] source;
    String[] input;
    String language;
    Integer prompt_number;

    public static IpnbCellRaw fromCell(@NotNull final IpnbCell cell, int nbformat) {
      final IpnbCellRaw raw = new IpnbCellRaw();
      if (cell instanceof IpnbMarkdownCell) {
        raw.cell_type = "markdown";
        raw.source = ((IpnbMarkdownCell)cell).getSource();
      }
      else if (cell instanceof IpnbCodeCell) {
        raw.cell_type = "code";
        final ArrayList<CellOutputRaw> outputRaws = new ArrayList<CellOutputRaw>();
        for (IpnbOutputCell outputCell : ((IpnbCodeCell)cell).getCellOutputs()) {
          outputRaws.add(CellOutputRaw.fromOutput(outputCell, nbformat));
        }
        raw.outputs = outputRaws.toArray(new CellOutputRaw[outputRaws.size()]);
        final Integer promptNumber = ((IpnbCodeCell)cell).getPromptNumber();
        if (nbformat == 4) {
          raw.execution_count = promptNumber != null && promptNumber >= 0 ? promptNumber : null;
          raw.source = ((IpnbCodeCell)cell).getSource();
        }
        else {
          raw.prompt_number = promptNumber != null && promptNumber >= 0 ? promptNumber : null;
          raw.language = ((IpnbCodeCell)cell).getLanguage();
          raw.input = ((IpnbCodeCell)cell).getSource();
        }
      }
      else if (cell instanceof IpnbRawCell) {
        raw.cell_type = "raw";
      }
      else if (cell instanceof IpnbHeadingCell) {
        raw.cell_type = "heading";
        raw.source = ((IpnbHeadingCell)cell).getSource();
        raw.level = ((IpnbHeadingCell)cell).getLevel();
      }
      return raw;
    }

    public IpnbCell createCell() {
      final IpnbCell cell;
      if (cell_type.equals("markdown")) {
        cell = new IpnbMarkdownCell(source);
      }
      else if (cell_type.equals("code")) {
        final List<IpnbOutputCell> outputCells = new ArrayList<IpnbOutputCell>();
        for (CellOutputRaw outputRaw : outputs) {
          outputCells.add(outputRaw.createOutput());
        }
        cell = new IpnbCodeCell(language == null ? "python" : language, input == null ? source : input,
                                prompt_number == null ? execution_count : prompt_number, outputCells);
      }
      else if (cell_type.equals("raw")) {
        cell = new IpnbRawCell();
      }
      else if (cell_type.equals("heading")) {
        cell = new IpnbHeadingCell(source, level);
      }
      else {
        cell = null;
      }
      return cell;
    }
  }

  private static class CellOutputRaw {
    String ename;
    String name;
    String evalue;
    OutputDataRaw data;
    Integer execution_count;
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


    public static CellOutputRaw fromOutput(@NotNull final IpnbOutputCell outputCell, int nbformat) {
      final CellOutputRaw raw = new CellOutputRaw();

      if (outputCell instanceof IpnbPngOutputCell) {
        raw.png = ((IpnbPngOutputCell)outputCell).getBase64String();
        raw.text = outputCell.getText();
        raw.output_type = "display_data";
      }
      else if (outputCell instanceof IpnbSvgOutputCell) {
        raw.svg = ((IpnbSvgOutputCell)outputCell).getSvg();
        raw.text = outputCell.getText();
      }
      else if (outputCell instanceof IpnbJpegOutputCell) {
        raw.jpeg = ((IpnbJpegOutputCell)outputCell).getBase64String();
        raw.text = outputCell.getText();
      }
      else if (outputCell instanceof IpnbLatexOutputCell) {
        raw.latex = ((IpnbLatexOutputCell)outputCell).getLatex();
        raw.prompt_number = outputCell.getPromptNumber();
        raw.text = outputCell.getText();
      }
      else if (outputCell instanceof IpnbStreamOutputCell) {
        if (nbformat == 4) {
          raw.name = ((IpnbStreamOutputCell)outputCell).getStream();
        }
        else {
          raw.stream = ((IpnbStreamOutputCell)outputCell).getStream();
        }
        raw.output_type = "stream";
        raw.text = outputCell.getText();
      }
      else if (outputCell instanceof IpnbHtmlOutputCell) {
        raw.html = ((IpnbHtmlOutputCell)outputCell).getHtmls();
        raw.text = outputCell.getText();
      }
      else if (outputCell instanceof IpnbErrorOutputCell) {
        raw.output_type = "pyerr";
        raw.evalue = ((IpnbErrorOutputCell)outputCell).getEvalue();
        raw.ename = ((IpnbErrorOutputCell)outputCell).getEname();
        raw.traceback = outputCell.getText();
      }
      else if (outputCell instanceof IpnbOutOutputCell) {
        if (nbformat == 4) {
          raw.execution_count = outputCell.getPromptNumber();
          raw.output_type = "execute_result";
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.text = outputCell.getText();
          raw.data = dataRaw;
        }
        else {
          raw.output_type = "pyout";
          raw.prompt_number = outputCell.getPromptNumber();
          raw.text = outputCell.getText();
        }
      }
      return raw;
    }

    public IpnbOutputCell createOutput() {
      final IpnbOutputCell outputCell;
      if (png != null) {
        outputCell = new IpnbPngOutputCell(png, text, prompt_number);
      }
      else if (jpeg != null) {
        outputCell = new IpnbJpegOutputCell(jpeg, text, prompt_number);
      }
      else if (svg != null) {
        outputCell = new IpnbSvgOutputCell(svg, text, prompt_number);
      }
      else if (latex != null) {
        outputCell = new IpnbLatexOutputCell(latex, prompt_number, text);
      }
      else if (stream != null || name != null) {
        outputCell = new IpnbStreamOutputCell(stream == null ? name : stream, text, prompt_number);
      }
      else if (html != null) {
        outputCell = new IpnbHtmlOutputCell(html, text, prompt_number);
      }
      else if ("pyerr".equals(output_type)) {
        outputCell = new IpnbErrorOutputCell(evalue, ename, traceback, prompt_number);
      }
      else if ("pyout".equals(output_type)) {
        outputCell = new IpnbOutOutputCell(text, prompt_number);
      }
      else if ("execute_result".equals(output_type)) {
        outputCell = new IpnbOutOutputCell(data.text, execution_count);
      }
      else {
        outputCell = new IpnbOutputCell(text, prompt_number);
      }
      return outputCell;
    }
  }

  private static class OutputDataRaw {
    @SerializedName("text/plain") String[] text;
  }
}
