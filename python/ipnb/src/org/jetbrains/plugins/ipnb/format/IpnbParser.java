package org.jetbrains.plugins.ipnb.format;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.VersionComparatorUtil;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.*;

public class IpnbParser {
  private static final Logger LOG = Logger.getInstance(IpnbParser.class);
  private static final Gson gson = initGson();

  @NotNull
  private static Gson initGson() {
    final GsonBuilder builder =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().registerTypeAdapter(IpnbCellRaw.class, new RawCellAdapter())
        .registerTypeAdapter(IpnbFileRaw.class, new FileAdapter()).registerTypeAdapter(CellOutputRaw.class, new OutputsAdapter())
        .registerTypeAdapter(OutputDataRaw.class, new OutputDataAdapter())
        .registerTypeAdapter(CellOutputRaw.class, new CellOutputDeserializer())
        .registerTypeAdapter(OutputDataRaw.class, new OutputDataDeserializer())
        .registerTypeAdapter(IpnbCellRaw.class, new CellRawDeserializer()).serializeNulls();
    return builder.create();
  }

  @NotNull
  public static IpnbFile parseIpnbFile(@NotNull final CharSequence fileText, @NotNull final VirtualFile virtualFile) throws IOException {
    final String path = virtualFile.getPath();
    IpnbFileRaw rawFile = gson.fromJson(fileText.toString(), IpnbFileRaw.class);
    if (rawFile == null) {
      int nbformat = isIpythonNewFormat(virtualFile) ? 4 : 3;
      return new IpnbFile(Collections.emptyMap(), nbformat, Lists.newArrayList(), path);
    }
    List<IpnbCell> cells = new ArrayList<>();
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
    return new IpnbFile(rawFile.metadata, rawFile.nbformat, cells, path);
  }

  public static boolean isIpythonNewFormat(@NotNull final VirtualFile virtualFile) {
    final Project project = ProjectUtil.guessProjectForFile(virtualFile);
    if (project != null) {
      final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(virtualFile);
      if (module != null) {
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          // It should be called first before IpnbConnectionManager#startIpythonServer()
          final List<PyPackage> packages = PyPackageUtil.refreshAndGetPackagesModally(sdk);
          final PyPackage ipython = packages != null ? PyPackageUtil.findPackage(packages, "ipython") : null;
          final PyPackage jupyter = packages != null ? PyPackageUtil.findPackage(packages, "jupyter") : null;
          if (jupyter == null && ipython != null && VersionComparatorUtil.compare(ipython.getVersion(), "3.0") <= 0) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @NotNull
  public static IpnbFile parseIpnbFile(@NotNull Document document, @NotNull final VirtualFile virtualFile) throws IOException {
    return parseIpnbFile(document.getImmutableCharSequence(), virtualFile);
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

    final IpnbFileRaw fileRaw = new IpnbFileRaw();
    fileRaw.metadata = ipnbFile.getMetadata();
    if (ipnbFile.getNbformat() == 4) {
      for (IpnbCell cell: ipnbFile.getCells()) {
        fileRaw.cells.add(IpnbCellRaw.fromCell(cell, ipnbFile.getNbformat()));
      }
    }
    else {
      final IpnbWorksheet worksheet = new IpnbWorksheet();
      worksheet.cells.clear();
      for (IpnbCell cell : ipnbFile.getCells()) {
        worksheet.cells.add(IpnbCellRaw.fromCell(cell, ipnbFile.getNbformat()));
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
    List<IpnbCellRaw> cells = new ArrayList<>();
    Map<String, Object> metadata = new HashMap<>();
    int nbformat = 4;
    int nbformat_minor;
  }

  private static class IpnbWorksheet {
    List<IpnbCellRaw> cells = new ArrayList<>();
  }

  @SuppressWarnings("unused")
  private static class IpnbCellRaw {
    String cell_type;
    Integer execution_count;
    Map<String, Object> metadata = new HashMap<>();
    Integer level;
    List<CellOutputRaw> outputs;
    List<String> source;
    List<String> input;
    String language;
    Integer prompt_number;

    public static IpnbCellRaw fromCell(@NotNull final IpnbCell cell, int nbformat) {
      final IpnbCellRaw raw = new IpnbCellRaw();
      if (cell instanceof IpnbEditableCell) {
        raw.metadata = ((IpnbEditableCell)cell).getMetadata();
      }
      if (cell instanceof IpnbMarkdownCell) {
        raw.cell_type = "markdown";
        raw.source = ((IpnbMarkdownCell)cell).getSource();
      }
      else if (cell instanceof IpnbCodeCell) {
        raw.cell_type = "code";
        final ArrayList<CellOutputRaw> outputRaws = new ArrayList<>();
        for (IpnbOutputCell outputCell : ((IpnbCodeCell)cell).getCellOutputs()) {
          outputRaws.add(CellOutputRaw.fromOutput(outputCell, nbformat));
        }
        raw.outputs = outputRaws;
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
        raw.source = ((IpnbRawCell)cell).getSource();
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
        cell = new IpnbMarkdownCell(source, metadata);
      }
      else if (cell_type.equals("code")) {
        final List<IpnbOutputCell> outputCells = new ArrayList<>();
        for (CellOutputRaw outputRaw : outputs) {
          outputCells.add(outputRaw.createOutput());
        }
        final Integer prompt = prompt_number != null ? prompt_number : execution_count;
        cell = new IpnbCodeCell(language == null ? "python" : language, input == null ? source : input,
                                prompt, outputCells, metadata);
      }
      else if (cell_type.equals("raw")) {
        cell = new IpnbRawCell(source);
      }
      else if (cell_type.equals("heading")) {
        cell = new IpnbHeadingCell(source, level, metadata);
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
    String png;
    String stream;
    String jpeg;
    List<String> html;
    List<String> latex;
    List<String> svg;
    Integer prompt_number;
    List<String> traceback;
    Map<String, Object> metadata;
    String output_type;
    List<String> text;

    public static CellOutputRaw fromOutput(@NotNull final IpnbOutputCell outputCell, int nbformat) {
      final CellOutputRaw raw = new CellOutputRaw();
      raw.metadata = outputCell.getMetadata();
      if (raw.metadata == null && !(outputCell instanceof IpnbStreamOutputCell) && !(outputCell instanceof IpnbErrorOutputCell)) {
        raw.metadata = Collections.emptyMap();
      }

      if (outputCell instanceof IpnbPngOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.png = ((IpnbPngOutputCell)outputCell).getBase64String();
          dataRaw.text = outputCell.getText();
          raw.data = dataRaw;
          raw.execution_count = outputCell.getPromptNumber();
          raw.output_type = outputCell.getPromptNumber() != null ? "execute_result" : "display_data";
        }
        else {
          raw.png = ((IpnbPngOutputCell)outputCell).getBase64String();
          raw.text = outputCell.getText();
        }
      }
      else if (outputCell instanceof IpnbSvgOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.text = outputCell.getText();
          dataRaw.svg = ((IpnbSvgOutputCell)outputCell).getSvg();
          raw.data = dataRaw;
          raw.execution_count = outputCell.getPromptNumber();
          raw.output_type = outputCell.getPromptNumber() != null ? "execute_result" : "display_data";
        }
        else {
          raw.svg = ((IpnbSvgOutputCell)outputCell).getSvg();
          raw.text = outputCell.getText();
        }
      }
      else if (outputCell instanceof IpnbJpegOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.text = outputCell.getText();
          dataRaw.jpeg = Lists.newArrayList(((IpnbJpegOutputCell)outputCell).getBase64String());
          raw.data = dataRaw;
        }
        else {
          raw.jpeg = ((IpnbJpegOutputCell)outputCell).getBase64String();
          raw.text = outputCell.getText();
        }
      }
      else if (outputCell instanceof IpnbLatexOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.text = outputCell.getText();
          dataRaw.latex = ((IpnbLatexOutputCell)outputCell).getLatex();
          raw.data = dataRaw;
          raw.execution_count = outputCell.getPromptNumber();
          raw.output_type = "execute_result";
        }
        else {
          raw.latex = ((IpnbLatexOutputCell)outputCell).getLatex();
          raw.text = outputCell.getText();
          raw.prompt_number = outputCell.getPromptNumber();
        }
      }
      else if (outputCell instanceof IpnbStreamOutputCell) {
        if (nbformat == 4) {
          raw.name = ((IpnbStreamOutputCell)outputCell).getStream();
        }
        else {
          raw.stream = ((IpnbStreamOutputCell)outputCell).getStream();
        }
        raw.text = outputCell.getText();
        raw.output_type = "stream";
      }
      else if (outputCell instanceof IpnbHtmlOutputCell) {
        if (nbformat == 4) {
          final OutputDataRaw dataRaw = new OutputDataRaw();
          dataRaw.html = ((IpnbHtmlOutputCell)outputCell).getHtmls();
          dataRaw.text = outputCell.getText();
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
      else {
        raw.text = outputCell.getText();
      }
      return raw;
    }

    public IpnbOutputCell createOutput() {
      List<String> text = this.text != null ? this.text : data != null ? data.text : Lists.newArrayList();
      Integer prompt = execution_count != null ? execution_count : prompt_number;
      final IpnbOutputCell outputCell;
      if (png != null || (data != null && data.png != null)) {
        outputCell = new IpnbPngOutputCell(png == null ? StringUtil.join(data.png) : png, text, prompt, metadata);
      }
      else if (jpeg != null || (data != null && data.jpeg != null)) {
        outputCell = new IpnbJpegOutputCell(jpeg == null ? StringUtil.join(data.jpeg, "") : jpeg, text, prompt, metadata);
      }
      else if (svg != null || (data != null && data.svg != null)) {
        outputCell = new IpnbSvgOutputCell(svg == null ? data.svg : svg, text, prompt, metadata);
      }
      else if (html != null || (data != null && data.html != null)) {
        outputCell = new IpnbHtmlOutputCell(html == null ? data.html : html, text, prompt, metadata);
      }
      else if (latex != null || (data != null && data.latex != null)) {
        outputCell = new IpnbLatexOutputCell(latex == null ? data.latex : latex, prompt, text, metadata);
      }
      else if (stream != null || name != null) {
        outputCell = new IpnbStreamOutputCell(stream == null ? name : stream, text, prompt, metadata);
      }
      else if ("pyerr".equals(output_type) || "error".equals(output_type)) {
        outputCell = new IpnbErrorOutputCell(evalue, ename, traceback, prompt, metadata);
      }
      else if ("pyout".equals(output_type)) {
        outputCell = new IpnbOutOutputCell(text, prompt, metadata);
      }
      else if ("execute_result".equals(output_type) && data != null) {
        outputCell = new IpnbOutOutputCell(data.text, prompt, metadata);
      }
      else if ("display_data".equals(output_type)){
        outputCell = new IpnbPngOutputCell(null, text, prompt, metadata);
      }
      else {
        outputCell = new IpnbOutputCell(text, prompt, metadata);
      }
      return outputCell;
    }
  }

  private static class OutputDataRaw {
    @SerializedName("image/png") String png;
    @SerializedName("text/html") List<String> html;
    @SerializedName("image/svg+xml") List<String> svg;
    @SerializedName("image/jpeg") List<String> jpeg;
    @SerializedName("text/latex") List<String> latex;
    @SerializedName("text/plain") List<String> text;
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
      if (cellRaw.metadata != null) {
        final JsonElement metadata = gson.toJsonTree(cellRaw.metadata);
        jsonObject.add("metadata", metadata);
      }
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

  static class CellRawDeserializer implements JsonDeserializer<IpnbCellRaw> {

    @Override
    public IpnbCellRaw deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
      final JsonObject object = json.getAsJsonObject();
      final IpnbCellRaw cellRaw = new IpnbCellRaw();
      final JsonElement cell_type = object.get("cell_type");
      if (cell_type != null) {
        cellRaw.cell_type = cell_type.getAsString();
      }
      final JsonElement count = object.get("execution_count");
      if (count != null) {
        cellRaw.execution_count = count.isJsonNull() ? null : count.getAsInt();
      }
      final JsonElement metadata = object.get("metadata");
      if (metadata != null) {
        cellRaw.metadata = gson.fromJson(metadata, Map.class);
      }
      final JsonElement level = object.get("level");
      if (level != null) {
        cellRaw.level = level.getAsInt();
      }

      final JsonElement outputsElement = object.get("outputs");
      if (outputsElement != null) {
        final JsonArray outputs = outputsElement.getAsJsonArray();
        cellRaw.outputs = Lists.newArrayList();
        for (JsonElement output : outputs) {
          cellRaw.outputs.add(gson.fromJson(output, CellOutputRaw.class));
        }
      }
      cellRaw.source = getStringOrArray("source", object);
      cellRaw.input = getStringOrArray("input", object);
      final JsonElement language = object.get("language");
      if (language != null) {
        cellRaw.language = language.getAsString();
      }
      final JsonElement number = object.get("prompt_number");
      if (number != null) {
        cellRaw.prompt_number = number.getAsInt();
      }
      return cellRaw;
    }
  }

  static class OutputDataDeserializer implements JsonDeserializer<OutputDataRaw> {

    @Override
    public OutputDataRaw deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
      final JsonObject object = json.getAsJsonObject();
      final OutputDataRaw dataRaw = new OutputDataRaw();
      final JsonElement png = object.get("image/png");
      if (png != null) {
        dataRaw.png = png.getAsString();
      }
      dataRaw.html = getStringOrArray("text/html", object);
      dataRaw.svg = getStringOrArray("image/svg+xml", object);
      dataRaw.jpeg = getStringOrArray("image/jpeg", object);
      dataRaw.latex = getStringOrArray("text/latex", object);
      dataRaw.text = getStringOrArray("text/plain", object);
      return dataRaw;
    }
  }
  static class CellOutputDeserializer implements JsonDeserializer<CellOutputRaw> {

    @Override
    public CellOutputRaw deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
      final JsonObject object = json.getAsJsonObject();
      final CellOutputRaw cellOutputRaw = new CellOutputRaw();
      final JsonElement ename = object.get("ename");
      if (ename != null) {
        cellOutputRaw.ename = ename.getAsString();
      }
      final JsonElement name = object.get("name");
      if (name != null) {
        cellOutputRaw.name = name.getAsString();
      }
      final JsonElement evalue = object.get("evalue");
      if (evalue != null) {
        cellOutputRaw.evalue = evalue.getAsString();
      }
      final JsonElement data = object.get("data");
      if (data != null) {
        cellOutputRaw.data = gson.fromJson(data, OutputDataRaw.class);
      }

      final JsonElement count = object.get("execution_count");
      if (count != null) {
        cellOutputRaw.execution_count = count.getAsInt();
      }
      final JsonElement outputType = object.get("output_type");
      if (outputType != null) {
        cellOutputRaw.output_type = outputType.getAsString();
      }
      final JsonElement png = object.get("png");
      if (png != null) {
        cellOutputRaw.png = png.getAsString();
      }
      final JsonElement stream = object.get("stream");
      if (stream != null) {
        cellOutputRaw.stream = stream.getAsString();
      }
      final JsonElement jpeg = object.get("jpeg");
      if (jpeg != null) {
        cellOutputRaw.jpeg = jpeg.getAsString();
      }

      cellOutputRaw.html = getStringOrArray("html", object);
      cellOutputRaw.latex = getStringOrArray("latex", object);
      cellOutputRaw.svg = getStringOrArray("svg", object);
      final JsonElement promptNumber = object.get("prompt_number");
      if (promptNumber != null) {
        cellOutputRaw.prompt_number = promptNumber.getAsInt();
      }
      cellOutputRaw.text = getStringOrArray("text", object);
      cellOutputRaw.traceback = getStringOrArray("traceback", object);
      final JsonElement metadata = object.get("metadata");
      if (metadata != null) {
        cellOutputRaw.metadata = gson.fromJson(metadata, Map.class);
      }

      return cellOutputRaw;
    }
  }

  @Nullable
  private static ArrayList<String> getStringOrArray(String name, JsonObject object) {
    final JsonElement jsonElement = object.get(name);
    final ArrayList<String> strings = Lists.newArrayList();
    if (jsonElement == null) return null;
    if (jsonElement.isJsonArray()) {
      final JsonArray array = jsonElement.getAsJsonArray();
      for (JsonElement element : array) {
        strings.add(element.getAsString());
      }
    }
    else {
      strings.add(jsonElement.getAsString());
    }
    return strings;
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
      if (cellRaw.traceback != null) {
        final JsonElement traceback = gson.toJsonTree(cellRaw.traceback);
        jsonObject.add("traceback", traceback);
      }

      if (cellRaw.metadata != null) {
        final JsonElement metadata = gson.toJsonTree(cellRaw.metadata);
        jsonObject.add("metadata", metadata);
      }
      if (cellRaw.output_type != null) {
        jsonObject.addProperty("output_type", cellRaw.output_type);
      }
      if (cellRaw.text != null) {
        final JsonElement text = gson.toJsonTree(cellRaw.text);
        jsonObject.add("text", text);
      }

      return jsonObject;
    }
  }

  static class OutputDataAdapter implements JsonSerializer<OutputDataRaw> {
    @Override
    public JsonElement serialize(OutputDataRaw cellRaw, Type typeOfSrc, JsonSerializationContext context) {
      final JsonObject jsonObject = new JsonObject();

      if (cellRaw.png != null) {
        jsonObject.addProperty("image/png", cellRaw.png);
      }
      if (cellRaw.html != null) {
        final JsonElement html = gson.toJsonTree(cellRaw.html);
        jsonObject.add("text/html", html);
      }
      if (cellRaw.svg != null) {
        final JsonElement svg = gson.toJsonTree(cellRaw.svg);
        jsonObject.add("image/svg+xml", svg);
      }
      if (cellRaw.jpeg != null) {
        final JsonElement jpeg = gson.toJsonTree(cellRaw.jpeg);
        jsonObject.add("image/jpeg", jpeg);
      }
      if (cellRaw.latex != null) {
        final JsonElement latex = gson.toJsonTree(cellRaw.latex);
        jsonObject.add("text/latex", latex);
      }
      if (cellRaw.text != null) {
        final JsonElement text = gson.toJsonTree(cellRaw.text);
        jsonObject.add("text/plain", text);
      }

      return jsonObject;
    }
  }
}
