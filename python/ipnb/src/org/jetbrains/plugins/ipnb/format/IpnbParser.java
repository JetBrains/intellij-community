package org.jetbrains.plugins.ipnb.format;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

  public static void saveIpnbFile(@NotNull final IpnbFilePanel ipnbPanel) {
    final IpnbFile ipnbFile = ipnbPanel.getIpnbFile();
    if (ipnbFile == null) return;
    for (IpnbEditablePanel panel : ipnbPanel.getIpnbPanels()) {
      if (panel.isModified()) {
        panel.updateCellSource();
      }
    }

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
      } catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public static class IpnbFileRaw {
    Map<String, String> metadata = new HashMap<String, String>();
    int nbformat = 3;
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
      if (cell instanceof IpnbMarkdownCell) {
        raw.cell_type = "markdown";
        raw.source = ((IpnbMarkdownCell)cell).getSource();
      }
      else if (cell instanceof IpnbCodeCell) {
        raw.cell_type = "code";
        final ArrayList<CellOutputRaw> outputRaws = new ArrayList<CellOutputRaw>();
        for (IpnbOutputCell outputCell : ((IpnbCodeCell)cell).getCellOutputs()) {
          outputRaws.add(CellOutputRaw.fromOutput(outputCell));
        }
        raw.outputs = outputRaws.toArray(new CellOutputRaw[outputRaws.size()]);
        raw.language = ((IpnbCodeCell)cell).getLanguage();
        raw.input = ((IpnbCodeCell)cell).getSource();
        final Integer promptNumber = ((IpnbCodeCell)cell).getPromptNumber();
        raw.prompt_number = promptNumber != null && promptNumber >= 0 ? promptNumber : null;
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
        cell = new IpnbCodeCell(language, input, prompt_number, outputCells);
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


    public static CellOutputRaw fromOutput(@NotNull final IpnbOutputCell outputCell) {
      final CellOutputRaw raw = new CellOutputRaw();

      if (outputCell instanceof IpnbPngOutputCell) {
        raw.png = ((IpnbPngOutputCell)outputCell).getBase64String();
        raw.text = outputCell.getText();
        //raw.output_type = "display_data";
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
        raw.stream = ((IpnbStreamOutputCell)outputCell).getStream();
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
        raw.output_type = "pyout";
        raw.text = outputCell.getText();
        raw.prompt_number = outputCell.getPromptNumber();
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
      else if (stream != null) {
        outputCell = new IpnbStreamOutputCell(stream, text, prompt_number);
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
      else {
        outputCell = new IpnbOutputCell(text, prompt_number);
      }
      return outputCell;
    }
  }
}
