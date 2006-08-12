
package com.intellij.refactoring.migration;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.UniqueFileNamesProvider;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 */
public class MigrationMapSet implements ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationMapSet");

  private ArrayList<MigrationMap> myMaps = null;
  @NonNls private static final String MIGRATION_MAP = "migrationMap";
  @NonNls private static final String ENTRY = "entry";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String OLD_NAME = "oldName";
  @NonNls private static final String NEW_NAME = "newName";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String TYPE = "type";
  @NonNls private static final String PACKAGE_TYPE = "package";
  @NonNls private static final String CLASS_TYPE = "class";
  @NonNls private static final String RECURSIVE = "recursive";

  @NonNls private static final String[] DEFAULT_MAPS = new  String[] {
    "/com/intellij/refactoring/migration/res/Swing__1_0_3____1_1_.xml",
  };

  public MigrationMapSet() {
  }

  public File[] getExportFiles() {
    return new File[]{getMapDirectory()};
  }

  public String getPresentableName() {
    return RefactoringBundle.message("migration.map.set.migration.maps");
  }

  public String getComponentName() {
    return "MigrationManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void addMap(MigrationMap map) {
    if (myMaps == null){
      loadMaps();
    }
    myMaps.add(map);
//    saveMaps();
  }

  public void replaceMap(MigrationMap oldMap, MigrationMap newMap) {
    for(int i = 0; i < myMaps.size(); i++){
      if (myMaps.get(i) == oldMap){
        myMaps.set(i, newMap);
      }
    }
  }

  public void removeMap(MigrationMap map) {
    if (myMaps == null){
      loadMaps();
    }
    myMaps.remove(map);
  }

  public MigrationMap[] getMaps() {
    if (myMaps == null){
      loadMaps();
    }
    MigrationMap[] ret = new MigrationMap[myMaps.size()];
    for(int i = 0; i < myMaps.size(); i++){
      ret[i] = myMaps.get(i);
    }
    return ret;
  }

  private File getMapDirectory() {
    @NonNls String directoryPath = PathManager.getConfigPath() + File.separator + "migration";
    File dir = new File(directoryPath);

    if (!dir.exists()){
      if (!dir.mkdir()){
        LOG.error("cannot create directory: " + dir.getAbsolutePath());
        return null;
      }

      for (int i = 0; i < DEFAULT_MAPS.length; i++) {
        String defaultTemplate = DEFAULT_MAPS[i];
        java.net.URL url = MigrationMapSet.class.getResource(defaultTemplate);
        LOG.assertTrue(url != null);
        String fileName = defaultTemplate.substring(defaultTemplate.lastIndexOf("/") + 1);
        File targetFile = new File(dir, fileName);

        try {
          FileOutputStream outputStream = new FileOutputStream(targetFile);
          InputStream inputStream = url.openStream();

          try {
            FileUtil.copy(inputStream, outputStream);
          }
          finally {
            outputStream.close();
            inputStream.close();
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }

    return dir;
  }

  private File[] getMapFiles() {
    File dir = getMapDirectory();
    if (dir == null){
      return new File[0];
    }
    File[] ret = dir.listFiles(new FileFilter() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File file){
        return !file.isDirectory() && StringUtil.endsWithIgnoreCase(file.getName(), ".xml");
      }
    });
    if (ret == null){
      LOG.error("cannot read directory: " + dir.getAbsolutePath());
      return new File[0];
    }
    return ret;
  }

  private void loadMaps() {
    myMaps = new ArrayList<MigrationMap>();

    File[] files = getMapFiles();
    for(int i = 0; i < files.length; i++){
      try{
        MigrationMap map = readMap(files[i]);
        if (map != null){
          myMaps.add(map);
        }
      }
      catch(InvalidDataException e){
        LOG.error("Invalid data in file: " + files[i].getAbsolutePath());
      }
      catch (JDOMException e) {
        LOG.error("Invalid data in file: " + files[i].getAbsolutePath());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private MigrationMap readMap(File file) throws JDOMException, InvalidDataException, IOException {
    if (!file.exists()) return null;
    Document document = JDOMUtil.loadDocument(file);

    Element root = document.getRootElement();
    if (root == null || !MIGRATION_MAP.equals(root.getName())){
      throw new InvalidDataException();
    }

    MigrationMap map = new MigrationMap();

    for(Iterator i = root.getChildren().iterator(); i.hasNext(); ){
      Element node = (Element)i.next();
      if (NAME.equals(node.getName())){
        String name = node.getAttributeValue(VALUE);
        map.setName(name);
      }
      if (DESCRIPTION.equals(node.getName())){
        String description = node.getAttributeValue(VALUE);
        map.setDescription(description);
      }

      if (ENTRY.equals(node.getName())){
        MigrationMapEntry entry = new MigrationMapEntry();
        String oldName = node.getAttributeValue(OLD_NAME);
        if (oldName == null){
          throw new InvalidDataException();
        }
        entry.setOldName(oldName);
        String newName = node.getAttributeValue(NEW_NAME);
        if (newName == null){
          throw new InvalidDataException();
        }
        entry.setNewName(newName);
        String typeStr = node.getAttributeValue(TYPE);
        if (typeStr == null){
          throw new InvalidDataException();
        }
        entry.setType(MigrationMapEntry.CLASS);
        if (typeStr.equals(PACKAGE_TYPE)){
          entry.setType(MigrationMapEntry.PACKAGE);
          @NonNls String isRecursiveStr = node.getAttributeValue(RECURSIVE);
          if (isRecursiveStr != null && isRecursiveStr.equals("true")){
            entry.setRecursive(true);
          }
          else{
            entry.setRecursive(false);
          }
        }
        map.addEntry(entry);
      }
    }

    return map;
  }

  public void saveMaps() throws IOException{
    File dir = getMapDirectory();
    if (dir == null) {
      return;
    }

    File[] files = getMapFiles();

    @NonNls String[] filePaths = new String[myMaps.size()];
    Document[] documents = new Document[myMaps.size()];

    UniqueFileNamesProvider namesProvider = new UniqueFileNamesProvider();
    for(int i = 0; i < myMaps.size(); i++){
      MigrationMap map = myMaps.get(i);

      filePaths[i] = dir + File.separator + namesProvider.suggestName(map.getName()) + ".xml";
      documents[i] = saveMap(map);
    }

    JDOMUtil.updateFileSet(files, filePaths, documents, CodeStyleSettingsManager.getSettings(null).getLineSeparator());
  }

  private Document saveMap(MigrationMap map) {
    Element root = new Element(MIGRATION_MAP);

    Element nameElement = new Element(NAME);
    nameElement.setAttribute(VALUE, map.getName());
    root.addContent(nameElement);

    Element descriptionElement = new Element(DESCRIPTION);
    descriptionElement.setAttribute(VALUE, map.getDescription());
    root.addContent(descriptionElement);

    for(int i = 0; i < map.getEntryCount(); i++){
      MigrationMapEntry entry = map.getEntryAt(i);
      Element element = new Element(ENTRY);
      element.setAttribute(OLD_NAME, entry.getOldName());
      element.setAttribute(NEW_NAME, entry.getNewName());
      if (entry.getType() == MigrationMapEntry.PACKAGE){
        element.setAttribute(TYPE, PACKAGE_TYPE);
        element.setAttribute(RECURSIVE, Boolean.valueOf(entry.isRecursive()).toString());
      }
      else{
        element.setAttribute(TYPE, CLASS_TYPE);
      }
      root.addContent(element);
    }

    return new Document(root);
  }
}
