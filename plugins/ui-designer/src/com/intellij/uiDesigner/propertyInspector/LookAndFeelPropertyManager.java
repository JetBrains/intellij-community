// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.project.Project;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import static java.lang.ClassLoader.getSystemClassLoader;

/**
 * @author NecroRayder
 */
public class LookAndFeelPropertyManager {

  private static ArrayList<LookAndFeelStruct> lafList = null;
  private static ArrayList<LookAndFeelStruct> standardLafList = null;

  private static final String STYLES_FILENAME = "styles.xml";
  private static String STYLES_FOLDER = null;

  private static final String LookAndFeelListTag = "LookAndFeelList";
  private static final String StyleItemTag = "item";
  private static final String StyleClassAttribute = "class";
  private static final String StylePathAttribute = "path";

  private static URLClassLoader LookAndFeelLoader = null;
  private static URL[] lafURLs = null;

  private static Project project;

  public static boolean isStyleFileAvailable(Project proj){
    project = proj;
    if(STYLES_FOLDER == null) STYLES_FOLDER = project.getProjectFile().getParent().getPath() + System.getProperty("file.separator");
    File f = new File(STYLES_FOLDER + STYLES_FILENAME);
    return f.exists();
  }

  private static boolean isStyleFileAvailable(){

    File f = new File(STYLES_FOLDER + STYLES_FILENAME);
    return f.exists();

  }

  public static void createStyleFile(Project proj){

    project = proj;

    if(STYLES_FOLDER == null) STYLES_FOLDER = project.getProjectFile().getParent().getPath() + System.getProperty("file.separator");
    final String lafFileName = STYLES_FILENAME;
    File f = new File(STYLES_FOLDER + STYLES_FILENAME);
    try {
      FileWriter writer = new FileWriter(f, true);
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + System.getProperty("line.separator"));
      writer.write("<" + LookAndFeelListTag + ">" + System.getProperty("line.separator"));
      for(int i = 0; i < getLafList().size(); i++){
        writer.write("  <" + StyleItemTag + " " + StyleClassAttribute + "=\"" + getLafList().get(i).getLookAndFeel().getClass().getName() + "\" " + StylePathAttribute + "=\"" + getLafList().get(i).getPath() + "\" />" + System.getProperty("line.separator"));
      }
      writer.write("</" + LookAndFeelListTag + ">");
      writer.close();
    } catch (Exception e){
      e.printStackTrace();
    }

  }

  private static void createStyleFile(){

    final String lafFileName = STYLES_FILENAME;
    File f = new File(STYLES_FOLDER + STYLES_FILENAME);
    try {
      FileWriter writer = new FileWriter(f);
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + System.getProperty("line.separator"));
      writer.write("<" + LookAndFeelListTag + ">" + System.getProperty("line.separator"));
      for(int i = 0; i < getLafList().size(); i++){
        writer.write("  <" + StyleItemTag + " " + StyleClassAttribute + "=\"" + getLafList().get(i).getLookAndFeel().getClass().getName() + "\" " + StylePathAttribute + "=\"" + getLafList().get(i).getPath() + "\" />" + System.getProperty("line.separator"));
      }
      writer.write("</" + LookAndFeelListTag + ">");
      writer.close();
    } catch (Exception e){
      e.printStackTrace();
    }

  }

  public static URLClassLoader getLookAndFeelClassLoader(){
    return LookAndFeelLoader;
  }

  public static URL[] getLafURLs(){ return lafURLs; }

  //
  public static void updateStylesFromFile(){

    ArrayList<URL> urls = new ArrayList<>();

    File f = new File(STYLES_FOLDER + STYLES_FILENAME);
    if(!f.exists()){
      createStyleFile();
    }
    try {

      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder documentBuilder = docBuilderFactory.newDocumentBuilder();
      Document document = documentBuilder.parse(f);

      for (int i = 0; i < document.getElementsByTagName(StyleItemTag).getLength(); i++) {

        Node tmp = document.getElementsByTagName(StyleItemTag).item(i);
        if (tmp.getParentNode().getNodeName().equals(LookAndFeelListTag)) {

          String tmpClassName = "";
          String tmpPath = "";

          if(tmp.getAttributes().getNamedItem(StyleClassAttribute) != null)
            tmpClassName = tmp.getAttributes().getNamedItem(StyleClassAttribute).getNodeValue();

          if(tmp.getAttributes().getNamedItem(StylePathAttribute) != null)
            tmpPath = tmp.getAttributes().getNamedItem(StylePathAttribute).getNodeValue();

          //If current look and feel is not present in list.
          if (!tmpClassName.isEmpty() && getLookAndFeelFromClassName(tmpClassName) == null) {

            File jar = new File(project.getProjectFile().getParent().getParent().getPath() +
                                System.getProperty("file.separator") + tmpPath);

            //System.out.println(jar.getAbsolutePath());

            URLClassLoader loader1 = URLClassLoader.newInstance(
              new URL[]{jar.toURI().toURL()},
              getSystemClassLoader()
            );

            Class<?> cls = loader1.loadClass(tmpClassName);
            Constructor<?> cons = cls.getConstructor();
            Object obj = cons.newInstance();

            if(obj instanceof LookAndFeel){
              LookAndFeelStruct struct = new LookAndFeelStruct((LookAndFeel)obj, tmpPath);
              struct.setAbsolutePath(jar.getAbsolutePath());
              struct.setURL(jar.toURI().toURL());
              lafList.add(struct);
              urls.add(jar.toURI().toURL());
              //System.out.println(tmpClassName + " added. " + jar.toURI().toURL() + "; " + obj.getClass().getClassLoader());
            }

          }

        }

        URL[] urlsArray = new URL[urls.toArray().length];
        lafURLs = urls.toArray(urlsArray);

      }

    } catch (Exception e){
      e.printStackTrace();
    }
  }

  private static ArrayList<LookAndFeelStruct> populateLafList() {
    ArrayList<LookAndFeelStruct> lafList = new ArrayList<>();

    //Add default "installed" and uiDesigner Look and Feels to LookAndFeels list.
    lafList.addAll(getDefaultLaf());

    return lafList;
  }

  private static ArrayList<LookAndFeelStruct> getLafList() {
    if(lafList == null) return getDefaultLaf();
    return lafList;
  }

  //get default "installed" Look and Feels.
  private static ArrayList<LookAndFeelStruct> getDefaultLaf() {

    if(standardLafList != null && standardLafList.size() > 0)
      return standardLafList;

    ArrayList<LookAndFeelStruct> lafList = new ArrayList<>();

    try {

      for (int i = 0; i < UIManager.getInstalledLookAndFeels().length; i++) {

        Class lnfClass = Class.forName(UIManager.getInstalledLookAndFeels()[i].getClassName());
        LookAndFeel curLaf = (LookAndFeel)lnfClass.newInstance();
        String path = "";
        LookAndFeelStruct tmp = new LookAndFeelStruct(curLaf, path);

        if (!lafList.contains(tmp) && !isAlreadyInList(tmp))
          lafList.add(tmp);

      }

    }
    catch (Exception err) {
      err.printStackTrace();
    }

    standardLafList = lafList;

    return lafList;
  }

  public static String[] getLookAndFeelNames() {

    if(lafList == null) lafList = populateLafList();

    String[] names = new String[lafList.size()];

    for (int i = 0; i < names.length; i++) {
      names[i] = lafList.get(i).getLookAndFeel().getName();
    }

    return names;

  }

  //returns the look and feel provided by name. If no look and feel is found
  //this method returns null.
  public static LookAndFeelStruct getLookAndFeelFromName(String name) {

    if(lafList == null) lafList = populateLafList();

    for (LookAndFeelStruct tmp : lafList) {
      if (tmp.getLookAndFeel().getName().equals(name)) return tmp;
    }

    return null;

  }

  //returns the look and feel provided by ClassName. If no look and feel is found
  //this method returns null.
  public static LookAndFeelStruct getLookAndFeelFromClassName(String ClassName) {

    if(lafList == null) lafList = populateLafList();

    for (LookAndFeelStruct tmp : lafList) {
      if (tmp.getLookAndFeel().getClass().getName().equals(ClassName)) return tmp;
    }

    return null;

  }

  public static LookAndFeelStruct[] getLookAndFeelList() {
    if(lafList == null) lafList = populateLafList();
    return (LookAndFeelStruct[])lafList.toArray();
  }

  public static boolean isStandardLaf(String lafClassName){
    ArrayList<LookAndFeelStruct> list = getDefaultLaf();
    LookAndFeelStruct tmp = getLookAndFeelFromClassName(lafClassName);
    if(tmp == null) return false;
    if(list.contains(tmp)) return true;
    return false;
  }

  private static boolean isAlreadyInList(LookAndFeelStruct lafs){
    if(lafList != null && lafList.size() > 0) {
      for (LookAndFeelStruct tmp : lafList) {
        if ( (tmp.getLookAndFeel().getClass().getName().equals(lafs.getLookAndFeel().getClass().getName()) ||
            tmp.getLookAndFeel().getName().trim().equals(lafs.getLookAndFeel().getName().trim())) &&
             tmp.getPath().equals(lafs.getPath()))
          return true;
      }
    }
    return false;
  }

  public static class LookAndFeelStruct {

    private LookAndFeel _lookAndFeel;
    private String _path;
    private String _absolutePath;
    private URL _url;

    public LookAndFeelStruct(LookAndFeel laf, String path){
      _lookAndFeel = laf;
      _path = path;
    }

    public String getPath(){
      return _path;
    }

    public void setPath(String path){
      _path = path;
    }

    public LookAndFeel getLookAndFeel(){
      return _lookAndFeel;
    }

    public void setLookAndFeel(LookAndFeel laf){
      _lookAndFeel = laf;
    }

    public void setAbsolutePath(String path){ this._absolutePath = path; }

    public String getAbsolutePath(){ return this._absolutePath; }

    public void setURL(URL url){
      this._url = url;
    }

    public URL getURL(){
      return this._url;
    }

  }

}
