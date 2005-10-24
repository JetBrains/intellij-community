package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.*;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.jsp.el.ELLanguage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.PendingEventDispatcher;
import com.intellij.util.UniqueFileNamesProvider;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Yura Cangea
 */
public class FileTypeManagerImpl extends FileTypeManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl");
  private final static int VERSION = 2;

  private final Set<FileType> myDefaultTypes = new THashSet<FileType>();
  private SetWithArray myFileTypes = new SetWithArray(new THashSet<FileType>());
  private final ArrayList<FakeFileType> mySpecialFileTypes = new ArrayList<FakeFileType>();
  private final ArrayList<Pattern> myIgnorePatterns = new ArrayList<Pattern>();

  private Map<String, FileType> myExtToFileTypeMap = new THashMap<String, FileType>();
  private final Set<String> myIgnoredFileMasksSet = new LinkedHashSet<String>();
  private final Set<String> myNotIgnoredFiles = Collections.synchronizedSet(new THashSet<String>());
  private final Set<String> myIgnoredFiles = Collections.synchronizedSet(new THashSet<String>());
  private final PendingEventDispatcher<FileTypeListener> myDispatcher = PendingEventDispatcher.create(FileTypeListener.class);
  private final Map<FileType, SyntaxTable> myDefaultTables = new THashMap<FileType, SyntaxTable>();
  private final Map<String, FileType> myInitialAssociations = new THashMap<String, FileType>();
  private Map<String, String> myUnresolvedMappings = new THashMap<String, String>();
  @NonNls private static final String ELEMENT_FILETYPE = "filetype";
  @NonNls private static final String ELEMENT_FILETYPES = "filetypes";
  @NonNls private static final String ELEMENT_IGNOREFILES = "ignoreFiles";
  @NonNls private static final String ATTRIBUTE_LIST = "list";
  @NonNls private static final String ELEMENT_EXTENSIONMAP = "extensionMap";
  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_EXT = "ext";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";
  @NonNls private static final String ELEMENT_REMOVED_MAPPING = "removed_mapping";
  @NonNls private static final String IGNORE_DOT_SVN = ".svn";
  @NonNls private static final String ATTRIBUTE_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_DESCRIPTION = "description";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String ATTRIBUTE_EXTENSIONS = "extensions";
  @NonNls private static final String ELEMENT_HIGHLIGHTING = "highlighting";
  @NonNls private static final String ELEMENT_OPTIONS = "options";
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String VALUE_LINE_COMMENT = "LINE_COMMENT";
  @NonNls private static final String VALUE_COMMENT_START = "COMMENT_START";
  @NonNls private static final String VALUE_COMMENT_END = "COMMENT_END";
  @NonNls private static final String VALUE_HEX_PREFIX = "HEX_PREFIX";
  @NonNls private static final String VALUE_NUM_POSTFIXES = "NUM_POSTFIXES";
  @NonNls private static final String VALUE_HAS_BRACES = "HAS_BRACES";
  @NonNls private static final String VALUE_HAS_BRACKETS = "HAS_BRACKETS";
  @NonNls private static final String VALUE_HAS_PARENS = "HAS_PARENS";
  @NonNls private static final String ELEMENT_KEYWORDS = "keywords";
  @NonNls private static final String ATTRIBUTE_IGNORE_CASE = "ignore_case";
  @NonNls private static final String ELEMENT_KEYWORD = "keyword";
  @NonNls private static final String ELEMENT_KEYWORDS2 = "keywords2";
  @NonNls private static final String ELEMENT_KEYWORDS3 = "keywords3";
  @NonNls private static final String ELEMENT_KEYWORDS4 = "keywords4";
  @NonNls private static final String ATTRIBUTE_BINARY = "binary";
  @NonNls private static final String ATTRIBUTE_DEFAULT_EXTENSION = "default_extension";
  @NonNls private static final String XML_EXTENSION = ".xml";

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public FileTypeManagerImpl() {
    registerStandardFileTypes();
    boolean standardFileTypeRead = loadAllFileTypes();
    if (standardFileTypeRead) {
      restoreStandardFileExtensions();
    }
  }

  public File[] getExportFiles() {
    return new File[]{getFileTypesDir(true), PathManager.getOptionsFile(this)};
  }

  public String getPresentableName() {
    return FileTypesBundle.message("filetype.settings.component");
  }
  // -------------------------------------------------------------------------
  // ApplicationComponent interface implementation
  // -------------------------------------------------------------------------

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public void save() {
    try {
      saveAllFileTypes();
    }
    catch (IOException e) {
      Messages.showErrorDialog(FileTypesBundle.message("filetype.settings.cannot.save.error", e.getLocalizedMessage()),
                               FileTypesBundle.message("filetype.settings.cannot.save.title"));
    }
  }

  // -------------------------------------------------------------------------
  // Implementation of abstract methods
  // -------------------------------------------------------------------------

  @NotNull
  public FileType getFileTypeByFileName(String fileName) {
    String ext = getExtension(fileName);
    return getFileTypeByExtension(ext);
  }

  @NotNull
  public FileType getFileTypeByFile(VirtualFile file) {
    // first let file recognize its type
    for (FakeFileType fileType : mySpecialFileTypes) {
      if (fileType.isMyFileType(file)) return fileType;
    }

    String extension = file.getExtension();
    if (extension == null) extension = "";
    return getFileTypeByExtension(extension);
  }

  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    FileType type = myExtToFileTypeMap.get(extension);
    if (type != null) return type;
    type = myExtToFileTypeMap.get(extension.toLowerCase());
    return type == null ? StdFileTypes.UNKNOWN : type;
  }

  public void registerFileType(FileType fileType) {
    registerFileType(fileType, null);
  }

  public void unregisterFileType(FileType fileType) {
    fireBeforeFileTypesChanged();
    unregisterFileTypeWithoutNotification(fileType);
    fireFileTypesChanged();
  }

  private void unregisterFileTypeWithoutNotification(FileType fileType) {
    removeAllAssociations(fileType);
    myFileTypes.remove(fileType);
    if (fileType instanceof FakeFileType) {
      final FakeFileType fakeFileType = (FakeFileType)fileType;
      mySpecialFileTypes.remove(fakeFileType);
    }
  }

  public FileType[] getRegisteredFileTypes() {
    return myFileTypes.toArray();
  }

  public String getExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  public String getIgnoredFilesList() {
    StringBuffer sb = new StringBuffer();
    for (String ignoreMask : myIgnoredFileMasksSet) {
      sb.append(ignoreMask);
      sb.append(';');
    }
    return sb.toString();
  }

  public void setIgnoredFilesList(String list) {
    fireBeforeFileTypesChanged();
    setIgnoredFilesListWithoutNotification(list);

    fireFileTypesChanged();
  }

  private void setIgnoredFilesListWithoutNotification(String list) {
    myIgnoredFileMasksSet.clear();
    myIgnorePatterns.clear();

    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      String ignoredFile = tokenizer.nextToken();
      if (ignoredFile != null && !myIgnoredFileMasksSet.contains(ignoredFile)) {
        if (!myIgnoredFileMasksSet.contains(ignoredFile)) {
          myIgnorePatterns.add(PatternUtil.fromMask(ignoredFile));
        }
        myIgnoredFileMasksSet.add(ignoredFile);
      }
    }

    //[mike]
    //To make async delete work. See FileUtil.asyncDelete.
    //Quite a hack, but still we need to have some name, which
    //won't be catched by VF for sure.
    //noinspection HardCodedStringLiteral
    Pattern p = Pattern.compile(".*\\.__del__");
    myIgnorePatterns.add(p);
  }

  public boolean isIgnoredFilesListEqualToCurrent(String list) {
    Set<String> tempSet = new THashSet<String>();
    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      tempSet.add(tokenizer.nextToken());
    }
    return tempSet.equals(myIgnoredFileMasksSet);
  }

  public boolean isFileIgnored(String name) {
    if (myNotIgnoredFiles.contains(name)) return false;
    if (myIgnoredFiles.contains(name)) return true;

    for (Pattern pattern : myIgnorePatterns) {
      if (pattern.matcher(name).matches()) {
        myIgnoredFiles.add(name);
        return true;
      }
    }

    myNotIgnoredFiles.add(name);
    return false;
  }

  public String[] getAssociatedExtensions(FileType type) {
    Map<String, FileType> extMap = myExtToFileTypeMap;
    return getAssociatedExtensions(extMap, type);
  }

  private static String[] getAssociatedExtensions(Map<String, FileType> extMap, FileType type) {
    List<String> exts = new ArrayList<String>();
    for (String ext : extMap.keySet()) {
      if (extMap.get(ext) == type) {
        exts.add(ext);
      }
    }
    return exts.toArray(new String[exts.size()]);
  }

  public void associateExtension(FileType type, String extension) {
    associateExtension(type, extension, true);
  }

  private void removeAllAssociations(FileType type) {
    Set<String> exts = myExtToFileTypeMap.keySet();
    String[] extsStrings = exts.toArray(new String[exts.size()]);
    for (String s : extsStrings) {
      if (myExtToFileTypeMap.get(s) == type) myExtToFileTypeMap.remove(s);
    }
  }

  public void dispatchPendingEvents(FileTypeListener listener) {
    myDispatcher.dispatchPendingEvent(listener);
  }

  public void fireBeforeFileTypesChanged() {
    FileTypeEvent event = new FileTypeEvent(this);
    myDispatcher.getMulticaster().beforeFileTypesChanged(event);
  }

  public void fireFileTypesChanged() {
    myNotIgnoredFiles.clear();
    myIgnoredFiles.clear();

    myDispatcher.getMulticaster().fileTypesChanged(new FileTypeEvent(this));
  }

  public void addFileTypeListener(FileTypeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeFileTypeListener(FileTypeListener listener) {
    myDispatcher.removeListener(listener);
  }

  private void saveAllFileTypes() throws IOException {
    File dir = getFileTypesDir(true);
    if (dir == null) return;

    File[] files = getFileTypeFiles();

    ArrayList<String> filePaths = new ArrayList<String>();
    ArrayList<Document> documents = new ArrayList<Document>();

    UniqueFileNamesProvider namesProvider = new UniqueFileNamesProvider();
    Iterator<FileType> iterator = myFileTypes.iterator();
    while (iterator.hasNext()) {
      FileType fileType = iterator.next();

      if (!(fileType instanceof CustomFileType) || !shouldSave(fileType)) continue;
      if (myDefaultTypes.contains(fileType) && !isDefaultModified(fileType)) continue;

      Element root = new Element(ELEMENT_FILETYPE);

      writeHeader(root, fileType);

      writeSyntaxTableData(root, fileType);

      String name = namesProvider.suggestName(fileType.getName());
      String filePath = dir.getAbsolutePath() + File.separator + name + XML_EXTENSION;
      filePaths.add(filePath);
      documents.add(new Document(root));
    }

    JDOMUtil.updateFileSet(files,
                           filePaths.toArray(new String[filePaths.size()]),
                           documents.toArray(new Document[documents.size()]),
                           CodeStyleSettingsManager.getSettings(null).getLineSeparator());
  }

  private boolean isDefaultModified(FileType fileType) {
    if (fileType instanceof CustomFileType) {
      return !Comparing.equal(myDefaultTables.get(fileType), ((CustomFileType)fileType).getSyntaxTable());
    }
    return true; //TODO?
  }

  // -------------------------------------------------------------------------
  // Implementation of NamedExternalizable interface
  // -------------------------------------------------------------------------

  public String getExternalFileName() {
    return "filetypes";
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    int savedVersion = getVersion(parentNode);
    for (final Object o : parentNode.getChildren()) {
      final Element e = (Element)o;
      if (ELEMENT_FILETYPES.equals(e.getName())) {
        List children = e.getChildren(ELEMENT_FILETYPE);
        for (final Object aChildren : children) {
          Element element = (Element)aChildren;
          loadFileType(element, true);
        }
      }
      else if (ELEMENT_IGNOREFILES.equals(e.getName())) {
        setIgnoredFilesListWithoutNotification(e.getAttributeValue(ATTRIBUTE_LIST));
      }
      else if (ELEMENT_EXTENSIONMAP.equals(e.getName())) {
        List mappings = e.getChildren(ELEMENT_MAPPING);

        for (Object mapping1 : mappings) {
          Element mapping = (Element)mapping1;
          String ext = mapping.getAttributeValue(ATTRIBUTE_EXT);
          String name = mapping.getAttributeValue(ATTRIBUTE_TYPE);
          FileType type = getFileTypeByName(name);

          if (type != null) {
            associateExtension(type, ext, false);
          }
          else {
            // Not yet loaded plugin could add the file type later.
            myUnresolvedMappings.put(ext, name);
          }
        }

        List removedMappings = e.getChildren(ELEMENT_REMOVED_MAPPING);
        for (Object removedMapping : removedMappings) {
          Element mapping = (Element)removedMapping;
          String ext = mapping.getAttributeValue(ATTRIBUTE_EXT);
          String name = mapping.getAttributeValue(ATTRIBUTE_TYPE);
          FileType type = getFileTypeByName(name);

          if (type != null) {
            removeAssociation(type, ext, false);
          }
        }
      }
    }

    if (savedVersion == 0) {
      if (!myIgnoredFileMasksSet.contains(IGNORE_DOT_SVN)) {
        myIgnorePatterns.add(PatternUtil.fromMask(IGNORE_DOT_SVN));
        myIgnoredFileMasksSet.add(IGNORE_DOT_SVN);
      }
    }
    if (savedVersion < VERSION) {
      restoreStandardFileExtensions();
    }
  }

  private void restoreStandardFileExtensions() {
    restoreStandardFileExtensions(StdFileTypes.JSP);
    restoreStandardFileExtensions(StdFileTypes.JSPX);
    restoreStandardFileExtensions(StdFileTypes.DTD);
    restoreStandardFileExtensions(StdFileTypes.HTML);
    restoreStandardFileExtensions(StdFileTypes.PROPERTIES);
    restoreStandardFileExtensions(StdFileTypes.XHTML);
  }

  private void restoreStandardFileExtensions(FileType fileType) {
    String[] extensions = getAssociatedExtensions(fileType);

    for (String ext : extensions) {
      FileType defaultFileType = myInitialAssociations.get(ext);
      if (defaultFileType != null && defaultFileType != fileType) {
        removeAssociation(fileType, ext, false);
        associateExtension(defaultFileType, ext, false);
      }
    }
    for (String ext : myInitialAssociations.keySet()) {
      FileType defaultFileType = myInitialAssociations.get(ext);
      if (defaultFileType == fileType) {
        associateExtension(defaultFileType, ext, false);
      }
    }
  }

  private static int getVersion(final Element node) {
    final String verString = node.getAttributeValue(ATTRIBUTE_VERSION);
    if (verString == null) return 0;
    try {
      return Integer.parseInt(verString);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    parentNode.setAttribute(ATTRIBUTE_VERSION, String.valueOf(VERSION));

    Element element = new Element(ELEMENT_IGNOREFILES);
    parentNode.addContent(element);
    element.setAttribute(ATTRIBUTE_LIST, getIgnoredFilesList());
    Element map = new Element(ELEMENT_EXTENSIONMAP);
    parentNode.addContent(map);

    Map<String, FileType> defaultMappings = new THashMap<String, FileType>(myInitialAssociations);

    for (String ext : myExtToFileTypeMap.keySet()) {
      FileType type = myExtToFileTypeMap.get(ext);
      if (type != null) {
        if (defaultMappings.get(ext) == type) {
          defaultMappings.remove(ext);
        }
        else if (shouldSave(type)) {
          Element mapping = new Element(ELEMENT_MAPPING);
          mapping.setAttribute(ATTRIBUTE_EXT, ext);
          mapping.setAttribute(ATTRIBUTE_TYPE, type.getName());
          map.addContent(mapping);
        }
      }
    }

    for (String ext : defaultMappings.keySet()) {
      FileType type = defaultMappings.get(ext);
      Element mapping = new Element(ELEMENT_REMOVED_MAPPING);
      mapping.setAttribute(ATTRIBUTE_EXT, ext);
      mapping.setAttribute(ATTRIBUTE_TYPE, type.getName());
      map.addContent(mapping);
    }
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  private FileType getFileTypeByName(String name) {
    Iterator<FileType> itr = myFileTypes.iterator();
    while (itr.hasNext()) {
      FileType fileType = itr.next();
      if (fileType.getName().equals(name)) return fileType;
    }
    return null;
  }

  public void registerFileType(FileType type, String[] defaultAssociatedExtensions) {
    fireBeforeFileTypesChanged();
    registerFileTypeWithoutNotification(type, defaultAssociatedExtensions);
    fireFileTypesChanged();
  }

  private void registerStandardFileTypes() {
    Language elLanguage = ELLanguage.INSTANCE; // Do not remove. This loads StdLanguage.
    if (StdFileTypes.ARCHIVE != null) return;
    registerFileTypeWithoutNotification(StdFileTypes.ARCHIVE = new ArchiveFileType(), parse("zip;jar;war;ear"));
    registerFileTypeWithoutNotification(StdFileTypes.CLASS = new JavaClassFileType(), new String[] {"class"});
    registerFileTypeWithoutNotification(StdFileTypes.HTML = new HtmlFileType(), parse("html;htm;sht;shtm;shtml"));
    registerFileTypeWithoutNotification(StdFileTypes.XHTML = new XHtmlFileType(), parse("xhtml"));
    registerFileTypeWithoutNotification(StdFileTypes.JAVA = new JavaFileType(), new String[] {"java"});
    if (ApplicationManagerEx.getApplicationEx().isAspectJSupportEnabled()) {
      registerFileTypeWithoutNotification(StdFileTypes.ASPECT = new AspectFileType(), new String[] {"aj"});
    }
    registerFileTypeWithoutNotification(StdFileTypes.JSP = new NewJspFileType(), parse("xjsp;jsp;jsf;jspf;tag;tagf"));
    registerFileTypeWithoutNotification(StdFileTypes.JSPX = new JspxFileType(), parse ("jspx;tagx"));
    registerFileTypeWithoutNotification(StdFileTypes.PLAIN_TEXT = new PlainTextFileType(), parse("txt;sh;bat;cmd;policy;log;cgi;pl;MF;sql;jad;jam"));
    registerFileTypeWithoutNotification(StdFileTypes.XML = new XmlFileType(), parse("xml;xsd;tld;xsl;jnlp;wsdl;hs;jhm"));
    registerFileTypeWithoutNotification(StdFileTypes.DTD = new DTDFileType(), parse("dtd;ent;mod"));
    registerFileTypeWithoutNotification(StdFileTypes.GUI_DESIGNER_FORM = new GuiFormFileType(), new String[] {"form"});
    registerFileTypeWithoutNotification(StdFileTypes.IDEA_WORKSPACE = new WorkspaceFileType(), new String[] {"iws"});
    registerFileTypeWithoutNotification(StdFileTypes.IDEA_PROJECT = new ProjectFileType(), new String[]{"ipr"});
    registerFileTypeWithoutNotification(StdFileTypes.IDEA_MODULE = new ModuleFileType(), new String[]{"iml"});
    registerFileTypeWithoutNotification(StdFileTypes.UNKNOWN = new UnknownFileType(), null);
    registerFileTypeWithoutNotification(StdFileTypes.PROPERTIES = PropertiesFileType.FILE_TYPE, new String[] {"properties"});
  }

  private static String[] parse(@NonNls String semicolonDelimited) {
    if (semicolonDelimited == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, ";", false);
    ArrayList<String> list = new ArrayList<String>();
    while (tokenizer.hasMoreTokens()) {
      list.add(tokenizer.nextToken().trim());
    }
    return list.toArray(new String[list.size()]);
  }

  /**
   * Registers a standard file type. Doesn't notifyListeners any change events.
   */
  private void registerFileTypeWithoutNotification(FileType fileType, @NonNls String[] extensions) {
    myFileTypes.add(fileType);
    if (extensions != null) {
      for (String extension : extensions) {
        myExtToFileTypeMap.put(extension, fileType);
        myInitialAssociations.put(extension, fileType);
      }
    }
    if (fileType instanceof FakeFileType) {
      mySpecialFileTypes.add((FakeFileType)fileType);
    }

    // Resolve unresolved mappings initialized before certain plugin initialized.
    for (String ext : new THashSet<String>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(ext);
      if (Comparing.equal(name, fileType.getName())) {
        myExtToFileTypeMap.put(ext, fileType);
        myUnresolvedMappings.remove(ext);
      }
    }
  }

  private static File[] getFileTypeFiles() {
    File fileTypesDir = getFileTypesDir(true);
    if (fileTypesDir == null) return new File[0];

    File[] files = fileTypesDir.listFiles(new FileFilter() {
      public boolean accept(File file) {
        return !file.isDirectory() && file.getName().toLowerCase().endsWith(XML_EXTENSION);
      }
    });
    if (files == null) {
      LOG.error("Cannot read directory: " + fileTypesDir.getAbsolutePath());
      return new File[0];
    }
//    return files;
    ArrayList<File> fileList = new ArrayList<File>();
    for (File file : files) {
      if (!file.isDirectory()) {
        fileList.add(file);
      }
    }
    return fileList.toArray(new File[fileList.size()]);
  }

  // returns true if at least one standard file type has been read
  private boolean loadAllFileTypes() {
    File[] files = getFileTypeFiles();
    boolean standardFileTypeRead = false;
    for (File file : files) {
      try {
        FileType fileType = loadFileType(file);
        standardFileTypeRead |= myInitialAssociations.values().contains(fileType);
      }
      catch (JDOMException e) {
      }
      catch (InvalidDataException e) {
      }
      catch (IOException e) {
      }
    }
    return standardFileTypeRead;
  }

  private FileType loadFileType(File file) throws JDOMException, InvalidDataException, IOException {
    Document document = JDOMUtil.loadDocument(file);
    if (document == null) {
      throw new InvalidDataException();
    }
    Element root = document.getRootElement();
    if (root == null || !ELEMENT_FILETYPE.equals(root.getName())) {
      throw new InvalidDataException();
    }
    return loadFileType(root, false);
  }

  private FileType loadFileType(Element typeElement, boolean isDefaults) {
    String fileTypeName = typeElement.getAttributeValue(ATTRIBUTE_NAME);
    String fileTypeDescr = typeElement.getAttributeValue(ATTRIBUTE_DESCRIPTION);
    String iconPath = typeElement.getAttributeValue(ATTRIBUTE_ICON);
    String extensionsStr = typeElement.getAttributeValue(ATTRIBUTE_EXTENSIONS);

    SyntaxTable table = null;
    Element element = typeElement.getChild(ELEMENT_HIGHLIGHTING);
    if (element != null) {
      table = readSyntaxTable(element);
    }

    FileType type = getFileTypeByName(fileTypeName);

    String[] exts = parse(extensionsStr);
    if (type != null) {
      if (extensionsStr != null) {
        removeAllAssociations(type);
        for (String ext : exts) {
          associateExtension(type, ext, false);
        }
      }

      if (table != null && type instanceof CustomFileType) {
        ((CustomFileType)type).setSyntaxTable(table);
      }
    }
    else {
      if (table != null) {
        type = new CustomFileType(table);
        ((CustomFileType)type).initSupport();
      }
      else {
        type = new UserBinaryFileType();
      }
      registerFileTypeWithoutNotification(type, exts);
    }

    if (type instanceof UserFileType) {
      UserFileType ft = (UserFileType)type;
      if (iconPath != null && !"".equals(iconPath.trim())) {
        Icon icon = IconLoader.getIcon(iconPath);
        if (icon != null) ft.setIcon(icon);
      }

      if (fileTypeDescr != null) ft.setDescription(fileTypeDescr);
      if (fileTypeName != null) ft.setName(fileTypeName);
    }

    if (isDefaults) {
      myDefaultTypes.add(type);
      if (table != null) {
        myDefaultTables.put(type, table);
      }
    }

    return type;
  }

  private static SyntaxTable readSyntaxTable(Element root) {
    SyntaxTable table = new SyntaxTable();

    for (final Object o : root.getChildren()) {
      Element element = (Element)o;

      if (ELEMENT_OPTIONS.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_OPTION)) {
          Element e = (Element)o1;
          String name = e.getAttributeValue(ATTRIBUTE_NAME);
          String value = e.getAttributeValue(ATTRIBUTE_VALUE);
          if (VALUE_LINE_COMMENT.equals(name)) {
            table.setLineComment(value);
          }
          else if (VALUE_COMMENT_START.equals(name)) {
            table.setStartComment(value);
          }
          else if (VALUE_COMMENT_END.equals(name)) {
            table.setEndComment(value);
          }
          else if (VALUE_HEX_PREFIX.equals(name)) {
            table.setHexPrefix(value);
          }
          else if (VALUE_NUM_POSTFIXES.equals(name)) {
            table.setNumPostfixChars(value);
          }
          else if (VALUE_HAS_BRACES.equals(name)) {
            table.setHasBraces(Boolean.valueOf(value).booleanValue());
          }
          else if (VALUE_HAS_BRACKETS.equals(name)) {
            table.setHasBrackets(Boolean.valueOf(value).booleanValue());
          }
          else if (VALUE_HAS_PARENS.equals(name)) {
            table.setHasParens(Boolean.valueOf(value).booleanValue());
          }
        }
      }
      else if (ELEMENT_KEYWORDS.equals(element.getName())) {
        boolean ignoreCase = Boolean.valueOf(element.getAttributeValue(ATTRIBUTE_IGNORE_CASE)).booleanValue();
        table.setIgnoreCase(ignoreCase);
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword1(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
      else if (ELEMENT_KEYWORDS2.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword2(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
      else if (ELEMENT_KEYWORDS3.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword3(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
      else if (ELEMENT_KEYWORDS4.equals(element.getName())) {
        for (final Object o1 : element.getChildren(ELEMENT_KEYWORD)) {
          Element e = (Element)o1;
          table.addKeyword4(e.getAttributeValue(ATTRIBUTE_NAME));
        }
      }
    }

    return table;
  }

  private static File getFileTypesDir(boolean create) {
    String directoryPath = PathManager.getConfigPath() + File.separator + ELEMENT_FILETYPES;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        LOG.error("Could not create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }

  private static boolean shouldSave(FileType fileType) {
    return fileType != StdFileTypes.UNKNOWN && !fileType.isReadOnly();
  }

  private static void writeHeader(Element root, FileType fileType) {
    root.setAttribute(ATTRIBUTE_BINARY, String.valueOf(fileType.isBinary()));
    final String defaultExtension = fileType.getDefaultExtension();
    if (defaultExtension != null) {
      root.setAttribute(ATTRIBUTE_DEFAULT_EXTENSION, defaultExtension);
    }

    root.setAttribute(ATTRIBUTE_DESCRIPTION, fileType.getDescription());
    root.setAttribute(ATTRIBUTE_NAME, fileType.getName());
  }

  private static void writeSyntaxTableData(Element root, FileType fileType) {
    if (!(fileType instanceof CustomFileType)) return;

    SyntaxTable table = ((CustomFileType)fileType).getSyntaxTable();
    Element highlightingElement = new Element(ELEMENT_HIGHLIGHTING);

    Element optionsElement = new Element(ELEMENT_OPTIONS);

    Element lineComment = new Element(ELEMENT_OPTION);
    lineComment.setAttribute(ATTRIBUTE_NAME, VALUE_LINE_COMMENT);
    lineComment.setAttribute(ATTRIBUTE_VALUE, table.getLineComment());
    optionsElement.addContent(lineComment);

    Element commentStart = new Element(ELEMENT_OPTION);
    commentStart.setAttribute(ATTRIBUTE_NAME, VALUE_COMMENT_START);
    commentStart.setAttribute(ATTRIBUTE_VALUE, table.getStartComment());
    optionsElement.addContent(commentStart);

    Element commentEnd = new Element(ELEMENT_OPTION);
    commentEnd.setAttribute(ATTRIBUTE_NAME, VALUE_COMMENT_END);
    commentEnd.setAttribute(ATTRIBUTE_VALUE, table.getEndComment());
    optionsElement.addContent(commentEnd);

    Element hexPrefix = new Element(ELEMENT_OPTION);
    hexPrefix.setAttribute(ATTRIBUTE_NAME, VALUE_HEX_PREFIX);
    hexPrefix.setAttribute(ATTRIBUTE_VALUE, table.getHexPrefix());
    optionsElement.addContent(hexPrefix);

    Element numPostfixes = new Element(ELEMENT_OPTION);
    numPostfixes.setAttribute(ATTRIBUTE_NAME, VALUE_NUM_POSTFIXES);
    numPostfixes.setAttribute(ATTRIBUTE_VALUE, table.getNumPostfixChars());
    optionsElement.addContent(numPostfixes);

    Element supportBraces = new Element(ELEMENT_OPTION);
    supportBraces.setAttribute(ATTRIBUTE_NAME, VALUE_HAS_BRACES);
    supportBraces.setAttribute(ATTRIBUTE_VALUE, String.valueOf(table.isHasBraces()));
    optionsElement.addContent(supportBraces);

    Element supportBrackets = new Element(ELEMENT_OPTION);
    supportBrackets.setAttribute(ATTRIBUTE_NAME, VALUE_HAS_BRACKETS);
    supportBrackets.setAttribute(ATTRIBUTE_VALUE, String.valueOf(table.isHasBrackets()));
    optionsElement.addContent(supportBrackets);

    Element supportParens = new Element(ELEMENT_OPTION);
    supportParens.setAttribute(ATTRIBUTE_NAME, VALUE_HAS_PARENS);
    supportParens.setAttribute(ATTRIBUTE_VALUE, String.valueOf(table.isHasParens()));
    optionsElement.addContent(supportParens);

    highlightingElement.addContent(optionsElement);

    Element keywordsElement = new Element(ELEMENT_KEYWORDS);
    keywordsElement.setAttribute(ATTRIBUTE_IGNORE_CASE, String.valueOf(table.isIgnoreCase()));
    writeKeywords(table.getKeywords1(), keywordsElement);
    highlightingElement.addContent(keywordsElement);

    Element keywordsElement2 = new Element(ELEMENT_KEYWORDS2);
    writeKeywords(table.getKeywords2(), keywordsElement2);
    highlightingElement.addContent(keywordsElement2);

    Element keywordsElement3 = new Element(ELEMENT_KEYWORDS3);
    writeKeywords(table.getKeywords3(), keywordsElement3);
    highlightingElement.addContent(keywordsElement3);

    Element keywordsElement4 = new Element(ELEMENT_KEYWORDS4);
    writeKeywords(table.getKeywords4(), keywordsElement4);
    highlightingElement.addContent(keywordsElement4);

    root.addContent(highlightingElement);
  }

  private static void writeKeywords(Set keywords, Element keywordsElement) {
    for (final Object keyword : keywords) {
      Element e = new Element(ELEMENT_KEYWORD);
      e.setAttribute(ATTRIBUTE_NAME, (String)keyword);
      keywordsElement.addContent(e);
    }
  }

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------

  public String getComponentName() {
    return "FileTypeManager";
  }

  public Map<String, FileType> getExtensionMap() {
    return myExtToFileTypeMap;
  }

  public void setExtensionMap(Set<FileType> fileTypes, Map<String, FileType> extension2TypeMap) {
    fireBeforeFileTypesChanged();
    myFileTypes = new SetWithArray(fileTypes);
    myExtToFileTypeMap = new THashMap<String, FileType>(extension2TypeMap.size());
    for (final String ext : extension2TypeMap.keySet()) {
      associateExtension(extension2TypeMap.get(ext), ext, false);
    }
    fireFileTypesChanged();
  }

  private static class SetWithArray {
    private final Set<FileType> mySet;
    private FileType[] myArray;

    public SetWithArray(Set<FileType> set) {
      mySet = set;
    }

    public void add(FileType element) {
      myArray = null;
      mySet.add(element);
    }

    public void remove(FileType element) {
      myArray = null;
      mySet.remove(element);
    }

    public Iterator<FileType> iterator() {
      final Iterator<FileType> iterator = mySet.iterator();
      return new Iterator<FileType>() {
        public boolean hasNext() {
          return iterator.hasNext();
        }

        public FileType next() {
          return iterator.next();
        }

        public void remove() {
          myArray = null;
          iterator.remove();
        }
      };
    }

    public FileType[] toArray() {
      if (myArray == null) myArray = mySet.toArray(new FileType[mySet.size()]);
      FileType[] array = new FileType[myArray.length];
      System.arraycopy(myArray, 0, array, 0, array.length);
      return array;
    }
  }

  public void associateExtension(FileType fileType, String extension, boolean fireChange){
    //if ("".equals(extension)) {
    //  return; // do not allow empty extensions
    //}
    if (myExtToFileTypeMap.get(extension) != fileType){
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myExtToFileTypeMap.put(extension, fileType);
      if (fireChange){
        fireFileTypesChanged();
      }
    }
  }

  public void removeAssociatedExtension(FileType type, String extension) {
    removeAssociation(type, extension, true);
  }

  public void removeAssociation(FileType fileType, String extension, boolean fireChange){
    if (myExtToFileTypeMap.get(extension) == fileType){
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myExtToFileTypeMap.remove(extension);
      if (fireChange){
        fireFileTypesChanged();
      }
    }
  }
  @NotNull
  public FileType getKnownFileTypeOrAssociate(VirtualFile file) {
    return FileTypeChooser.getKnownFileTypeOrAssociate(file);
  }
}
