package org.jetbrains.plugins.ipnb.format;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IpnbParser {
  private static final Logger LOG = Logger.getInstance(IpnbParser.class);
  private static final Gson gson = initGson();

  @NotNull
  private static Gson initGson() {
    final GsonBuilder builder =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().registerTypeAdapter(IpnbCellRaw.class, new RawCellAdapter())
    .registerTypeAdapter(IpnbFileRaw.class, new FileAdapter()).registerTypeAdapter(CellOutputRaw.class, new OutputsAdapter()).registerTypeAdapter(OutputDataRaw.class, new OutputDataAdapter()).serializeNulls();
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

  @Nullable
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
    try {
      final FileOutputStream fileOutputStream = new FileOutputStream(file);
      final OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, Charset.forName("UTF-8").newEncoder());
      try {
        writer.write(json);
      } catch (IOException e) {
        LOG.error(e);
      }
      finally {
        try {
          writer.close();
          fileOutputStream.close();
        } catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
  }

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unused")
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
        final Integer prompt = prompt_number != null ? prompt_number : execution_count;
        cell = new IpnbCodeCell(language == null ? "python" : language, input == null ? source : input,
                                prompt, outputCells);
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
    Map<String, Object> metadata;

    public static CellOutputRaw fromOutput(@NotNull final IpnbOutputCell outputCell, int nbformat) {
      final CellOutputRaw raw = new CellOutputRaw();
      if (!(outputCell instanceof IpnbStreamOutputCell) && !(outputCell instanceof IpnbErrorOutputCell)) {
        raw.metadata = new HashMap<String, Object>();
      }

      if (outputCell instanceof IpnbPngOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.png = ((IpnbPngOutputCell)outputCell).getBase64String();
          raw.data = dataRaw;
        }
        else {
          raw.png = ((IpnbPngOutputCell)outputCell).getBase64String();
        }
        raw.text = outputCell.getText();
        raw.output_type = "display_data";
      }
      else if (outputCell instanceof IpnbSvgOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.svg = ((IpnbSvgOutputCell)outputCell).getSvg();
          raw.data = dataRaw;
        }
        else {
          raw.svg = ((IpnbSvgOutputCell)outputCell).getSvg();
        }
        raw.text = outputCell.getText();
      }
      else if (outputCell instanceof IpnbJpegOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.jpeg = new String[]{((IpnbJpegOutputCell)outputCell).getBase64String()};
          raw.data = dataRaw;
        }
        else {
          raw.jpeg = ((IpnbJpegOutputCell)outputCell).getBase64String();
        }
        raw.text = outputCell.getText();
      }
      else if (outputCell instanceof IpnbLatexOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.latex = ((IpnbLatexOutputCell)outputCell).getLatex();
          raw.data = dataRaw;
        }
        else {
          raw.latex = ((IpnbLatexOutputCell)outputCell).getLatex();
        }
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
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.html = ((IpnbHtmlOutputCell)outputCell).getHtmls();
          raw.data = dataRaw;
          raw.execution_count = outputCell.getPromptNumber();
        }
        else {
          raw.html = ((IpnbHtmlOutputCell)outputCell).getHtmls();
        }
        raw.output_type = nbformat == 4 ? "execute_result" : "pyout";
      }
      else if (outputCell instanceof IpnbErrorOutputCell) {
        raw.output_type = nbformat == 4 ? "error" : "pyerr";
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
      if (png != null || (data != null && data.png != null)) {
        outputCell = new IpnbPngOutputCell(png == null ? StringUtil.join(data.png) : png, text, prompt_number);
      }
      else if (jpeg != null || (data != null && data.jpeg != null)) {
        outputCell = new IpnbJpegOutputCell(jpeg == null ? StringUtil.join(data.jpeg) : jpeg, text, prompt_number);
      }
      else if (svg != null || (data != null && data.svg != null)) {
        outputCell = new IpnbSvgOutputCell(svg == null ? data.svg : svg, text, prompt_number);
      }
      else if (latex != null || (data != null && data.latex != null)) {
        outputCell = new IpnbLatexOutputCell(latex == null ? data.latex : latex, prompt_number, text);
      }
      else if (stream != null || name != null) {
        outputCell = new IpnbStreamOutputCell(stream == null ? name : stream, text, prompt_number);
      }
      else if (html != null || (data != null && data.html != null)) {
        outputCell = new IpnbHtmlOutputCell(html == null ? data.html : html, text, prompt_number);
      }
      else if ("pyerr".equals(output_type) || "error".equals(output_type)) {
        outputCell = new IpnbErrorOutputCell(evalue, ename, traceback, prompt_number);
      }
      else if ("pyout".equals(output_type)) {
        outputCell = new IpnbOutOutputCell(text, prompt_number);
      }
      else if ("execute_result".equals(output_type) && data != null) {
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
    @SerializedName("text/html") String[] html;
    @SerializedName("image/svg+xml") String[] svg;
    @SerializedName("image/png") String png;
    @SerializedName("image/jpeg") String[] jpeg;
    @SerializedName("text/latex") String[] latex;
  }

  static class RawCellAdapter implements JsonSerializer<IpnbCellRaw> {
    @Override
    public JsonElement serialize(IpnbCellRaw cellRaw, Type typeOfSrc, JsonSerializationContext context) {
      final JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("cell_type", cellRaw.cell_type);
      if ("code".equals(cellRaw.cell_type)) {
        final Integer count = cellRaw.execution_count;
        if (count == null) {
          jsonObject.add("execution_count", JsonNull.INSTANCE);
        }
        else {
          jsonObject.addProperty("execution_count", count);
        }
      }
      final JsonElement metadata = gson.toJsonTree(cellRaw.metadata);
      jsonObject.add("metadata", metadata);
      if (cellRaw.level != null) {
        jsonObject.addProperty("level", cellRaw.level);
      }

      if (cellRaw.outputs != null) {
        final JsonElement outputs = gson.toJsonTree(cellRaw.outputs);
        jsonObject.add("outputs", outputs);
      }
      if (cellRaw.source != null) {
        final JsonElement source = gson.toJsonTree(cellRaw.source);
        jsonObject.add("source", source);
      }
      if (cellRaw.input != null) {
        final JsonElement input = gson.toJsonTree(cellRaw.input);
        jsonObject.add("input", input);
      }
      if (cellRaw.language != null) {
        jsonObject.addProperty("language", cellRaw.language);
      }
      if (cellRaw.prompt_number != null) {
        jsonObject.addProperty("prompt_number", cellRaw.prompt_number);
      }

      return jsonObject;
    }
  }
  static class FileAdapter implements JsonSerializer<IpnbFileRaw> {
    @Override
    public JsonElement serialize(IpnbFileRaw fileRaw, Type typeOfSrc, JsonSerializationContext context) {
      final JsonObject jsonObject = new JsonObject();
      if (fileRaw.worksheets != null) {
        final JsonElement worksheets = gson.toJsonTree(fileRaw.worksheets);
        jsonObject.add("worksheets", worksheets);
      }
      if (fileRaw.cells != null) {
        final JsonElement cells = gson.toJsonTree(fileRaw.cells);
        jsonObject.add("cells", cells);
      }
      final JsonElement metadata = gson.toJsonTree(fileRaw.metadata);
      jsonObject.add("metadata", metadata);

      jsonObject.addProperty("nbformat", fileRaw.nbformat);
      jsonObject.addProperty("nbformat_minor", fileRaw.nbformat_minor);

      return jsonObject;
    }
  }

  static class OutputsAdapter implements JsonSerializer<CellOutputRaw> {
    @Override
    public JsonElement serialize(CellOutputRaw cellRaw, Type typeOfSrc, JsonSerializationContext context) {
      final JsonObject jsonObject = new JsonObject();
      if (cellRaw.ename != null) {
        jsonObject.addProperty("ename", cellRaw.ename);
      }
      if (cellRaw.name != null) {
        jsonObject.addProperty("name", cellRaw.name);
      }
      if (cellRaw.evalue != null) {
        jsonObject.addProperty("evalue", cellRaw.evalue);
      }

      if (cellRaw.data != null) {
        final JsonElement data = gson.toJsonTree(cellRaw.data);
        jsonObject.add("data", data);
      }
      if (cellRaw.execution_count != null) {
        jsonObject.addProperty("execution_count", cellRaw.execution_count);
      }
      if (cellRaw.output_type != null) {
        jsonObject.addProperty("output_type", cellRaw.output_type);
      }
      if (cellRaw.png != null) {
        jsonObject.addProperty("png", cellRaw.png);
      }

      if (cellRaw.stream != null) {
        jsonObject.addProperty("stream", cellRaw.stream);
      }

      if (cellRaw.jpeg != null) {
        jsonObject.addProperty("jpeg", cellRaw.jpeg);
      }

      if (cellRaw.html != null) {
        final JsonElement html = gson.toJsonTree(cellRaw.html);
        jsonObject.add("html", html);
      }
      if (cellRaw.latex != null) {
        final JsonElement latex = gson.toJsonTree(cellRaw.latex);
        jsonObject.add("latex", latex);
      }

      if (cellRaw.svg != null) {
        final JsonElement svg = gson.toJsonTree(cellRaw.svg);
        jsonObject.add("svg", svg);
      }
      if (cellRaw.prompt_number != null) {
        jsonObject.addProperty("prompt_number", cellRaw.prompt_number);
      }
      if (cellRaw.text != null) {
        final JsonElement text = gson.toJsonTree(cellRaw.text);
        jsonObject.add("text", text);
      }
      if (cellRaw.traceback != null) {
        final JsonElement traceback = gson.toJsonTree(cellRaw.traceback);
        jsonObject.add("traceback", traceback);
      }

      if (cellRaw.metadata != null) {
        final JsonElement metadata = gson.toJsonTree(cellRaw.metadata);
        jsonObject.add("metadata", metadata);
      }

      return jsonObject;
    }
  }

  static class OutputDataAdapter implements JsonSerializer<OutputDataRaw> {
    @Override
    public JsonElement serialize(OutputDataRaw cellRaw, Type typeOfSrc, JsonSerializationContext context) {
      final JsonObject jsonObject = new JsonObject();

      if (cellRaw.text != null) {
        final JsonElement text = gson.toJsonTree(cellRaw.text);
        jsonObject.add("text/plain", text);
      }
      if (cellRaw.html != null) {
        final JsonElement html = gson.toJsonTree(cellRaw.html);
        jsonObject.add("text/html", html);
      }
      if (cellRaw.svg != null) {
        final JsonElement svg = gson.toJsonTree(cellRaw.svg);
        jsonObject.add("image/svg+xml", svg);
      }
      if (cellRaw.png != null) {
        jsonObject.addProperty("image/png", cellRaw.png);
      }
      if (cellRaw.jpeg != null) {
        final JsonElement jpeg = gson.toJsonTree(cellRaw.jpeg);
        jsonObject.add("image/jpeg", jpeg);
      }
      if (cellRaw.latex != null) {
        final JsonElement latex = gson.toJsonTree(cellRaw.latex);
        jsonObject.add("text/latex", latex);
      }

      return jsonObject;
    }
  }
}
