package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ArrayUtil;
import org.apache.velocity.runtime.parser.ParseException;

import java.io.*;
import java.util.Map;
import java.util.Properties;

/**
 * @author MYakovlev
 * Date: Jul 24, 2002
 */
public class FileTemplateImpl implements FileTemplate, Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateImpl");

  private VirtualFile myDescription;
  private String myContent;
  private String myName;
  private String myExtension;
  private File myTemplateFile;      // file to save in
  private VirtualFile myTemplateURL;
  boolean myRenamed = false;
  private boolean myModified = false;
  private boolean myReadOnly = false;
  private boolean myAdjust = true;


  private boolean myIsInternal = false;

  /** Creates new template. This template is marked as 'new', i.e. it will be saved to new file at IDEA end. */
  FileTemplateImpl(String content, String name, String extension){
    if(content != null){
      content = StringUtil.convertLineSeparators(content);
    }
    myContent = content;
    myName = replaceFileSeparatorChar(name);
    myExtension = extension;
    myModified = true;
  }

  FileTemplateImpl(File templateFile, String name, String extension, boolean isReadOnly) {
    myTemplateFile = templateFile;
    myName = replaceFileSeparatorChar(name);
    myExtension = extension;
    myModified = false;
    myReadOnly = isReadOnly;
  }

  FileTemplateImpl(VirtualFile templateURL, String name, String extension) {
    myTemplateURL = templateURL;
    myName = name;
    myExtension = extension;
    myModified = false;
    myReadOnly = true;
  }

  public Object clone(){
    try{
      FileTemplate clon = (FileTemplate)super.clone();
      return clon;
    }
    catch (CloneNotSupportedException e){
      // Should not be here
      throw new RuntimeException(e);
    }
  }

  public String[] getUnsetAttributes(Properties properties) throws ParseException{
    String content;
    try{
      content = getContent();
    }
    catch (IOException e){
      LOG.error("Unable to read template \""+myName+"\"", e);
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return FileTemplateUtil.calculateAttributes(content, properties, false);
  }

  public boolean isDefault(){
    return myReadOnly;
  }

  public String getDescription(){
    try{
      return myDescription != null ? new String(myDescription.contentsToCharArray()) : "";
    }
    catch (IOException e){
      LOG.error(e);
    }
    return "";
  }

  void setDescription(VirtualFile file){
    myDescription = file;
  }

  public String getName(){
    return myName;
  }

  public boolean isJavaClassTemplate(){
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(myExtension);
    return fileType.equals(StdFileTypes.JAVA) || fileType.equals(StdFileTypes.ASPECT);
  }

  public String getExtension(){
    return myExtension;
  }

  public String getText(){
    String text = "";
    try{
      text = getContent();
    }
    catch (IOException e){
      LOG.error("Unable to read template \""+myName+"\"", e);
    }
    return text;
  }

  public void setText(String text){
    // for read-only template we will save it later in user-defined templates
    if(text == null){
      text = "";
    }
    text = StringUtil.convertLineSeparators(text);
    if(text.equals(getText())){
      return;
    }
    myContent = text;
    myModified = true;
    if(myReadOnly){
      myTemplateFile = null;
      myTemplateURL = null;
      myReadOnly = false;
    }
  }

  boolean isModified(){
    return myModified;
  }

  /** Read template from file. */
  private String readExternal(File file) throws IOException{
    FileInputStream inputStream = new FileInputStream(file);
    String result = readExternal(inputStream);
    inputStream.close();
    return result;
  }

  /** Read template from URL. */
  private String readExternal(VirtualFile url) throws IOException{
    InputStream inputStream = url.getInputStream();
    String result = readExternal(inputStream);
    inputStream.close();
    return result;
  }

  /** Read template from stream. Stream does not closed after reading. */
  private String readExternal(InputStream inputStream) throws IOException{
    StringWriter wr = new StringWriter();
    Reader fr = new InputStreamReader(inputStream, ourEncoding);
    BufferedReader br = new BufferedReader(fr);
    int currChar;
    do{
      currChar = br.read();
      if (currChar != -1){
        wr.write(currChar);
      }
    }
    while (currChar != -1);
    br.close();
    fr.close();
    return wr.toString();
  }

  /** Removes template file.
   */
  void removeFromDisk() {
    if(myReadOnly){
      return;
    }
    else if(myTemplateFile != null){
      if(myTemplateFile.delete()){
        myModified = false;
      }
    }
  }

  /** Save template to file. If template is new, it is saved to specified directory. Otherwise it is saved to file from which it was read.
   *  If template was not modified, it is not saved.
   */
  void writeExternal(File defaultDir) throws IOException{
    if(!myModified){
      if(!myRenamed){
        return;
      }
    }
    if(myRenamed){
      LOG.assertTrue(myTemplateFile != null);
      LOG.assertTrue(myTemplateFile.delete());
      myTemplateFile = null;
      myRenamed = false;
    }
    File templateFile = myReadOnly ? null : myTemplateFile;
    if(templateFile == null){
      LOG.assertTrue(defaultDir.isDirectory());
      templateFile = new File(defaultDir, myName+"."+myExtension);
    }
    FileOutputStream fileOutputStream = new FileOutputStream(templateFile);
    OutputStreamWriter outputStreamWriter;
    try{
      outputStreamWriter = new OutputStreamWriter(fileOutputStream, ourEncoding);
    }
    catch (UnsupportedEncodingException e){
      Messages.showMessageDialog("Unable to save File Template \""+getName()+"\" using " + ourEncoding, "Error", Messages.getErrorIcon());
      outputStreamWriter = new OutputStreamWriter(fileOutputStream);
    }
    String content = getContent();
    Project project = ProjectManagerEx.getInstanceEx().getDefaultProject();
    String lineSeparator = CodeStyleSettingsManager.getSettings(project).getLineSeparator();

    if (!lineSeparator.equals("\n")){
      content = StringUtil.convertLineSeparators(content, lineSeparator);
    }

    outputStreamWriter.write(content);
    outputStreamWriter.close();
    fileOutputStream.close();

//    StringReader reader = new StringReader(getContent());
//    FileWriter fileWriter = new FileWriter(templateFile);
//    BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
//    for(int currChar = reader.read(); currChar != -1; currChar = reader.read()){
//      bufferedWriter.write(currChar);
//    }
//    bufferedWriter.close();
//    fileWriter.close();
    myModified = false;
    myTemplateFile = templateFile;
  }

  public String getText(Map attributes) throws IOException{
    return StringUtil.convertLineSeparators(FileTemplateUtil.mergeTemplate(attributes, getContent()));
  }

  public String getText(Properties attributes) throws IOException{
    return StringUtil.convertLineSeparators(FileTemplateUtil.mergeTemplate(attributes, getContent()));
  }

  public String toString(){
    return getName();
  }

  private String getContent() throws IOException{
    if(myContent == null){
      if(myTemplateFile != null){
        myContent = StringUtil.convertLineSeparators(readExternal(myTemplateFile));
      }
      else if(myTemplateURL != null){
        myContent = StringUtil.convertLineSeparators(readExternal(myTemplateURL));
      }
      else{
        myContent = "";
      }
    }
    return myContent;
  }

  void invalidate(){
    if(!myReadOnly){
      if(myTemplateFile != null || myTemplateURL != null){
        myContent = null;
      }
    }
  }

  boolean isNew(){
    return myTemplateFile == null && myTemplateURL == null;
  }

  public void setName(String name){
    if(name == null){
      name = "";
    }
    else {
      name = replaceFileSeparatorChar(name.trim());
    }
    if(!myName.equals(name)){
      LOG.assertTrue(!myReadOnly);
      myName = name;
      myRenamed = true;
      myModified = true;
    }
  }

  public void setExtension(String extension){
    if(extension == null){
      extension = "";
    }
    extension = extension.trim();
    if(!myExtension.equals(extension)){
      LOG.assertTrue(!myReadOnly);
      myExtension = extension;
      myRenamed = true;
      myModified = true;
    }
  }

  public boolean isAdjust(){
    return myAdjust;
  }

  public void setAdjust(boolean adjust){
    myAdjust = adjust;
  }

  public void resetToDefault() {
    LOG.assertTrue(!isDefault());
    VirtualFile file = FileTemplateManager.getInstance().getDefaultTemplate(myName, myExtension);
    if (file == null) return;
    try {
      String text = readExternal(file);
      setText(text);
      myReadOnly = true;
    } catch (IOException e) {
      LOG.error ("Error reading template");
    }
  }

   private String replaceFileSeparatorChar(String s) {
    StringBuffer buffer = new StringBuffer();
    char[] chars = s.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == File.separatorChar) buffer.append("$");
      else buffer.append(chars[i]);
    }
    return buffer.toString();
  }

  public void setInternal(boolean isInternal) {
    myIsInternal = true;
  }

  public boolean isInternal() {
    return myIsInternal;
  }
}
