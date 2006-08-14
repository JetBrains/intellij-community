package com.intellij.ide.fileTemplates;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.impl.FileTemplateImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.log.LogSystem;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.ASTReference;
import org.apache.velocity.runtime.parser.node.Node;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author MYakovlev
 */
public class FileTemplateUtil{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.FileTemplateUtil");
  private static boolean ourVelocityInitialized = false;
  @NonNls public static final String PACKAGE_ATTR = "PACKAGE_NAME";

  public static String[] calculateAttributes(String templateContent, Properties properties, boolean includeDummies) throws ParseException {
    initVelocity();
    final Set<String> unsetAttributes = new HashSet<String>();
    //noinspection HardCodedStringLiteral
    addAttributesToVector(unsetAttributes, RuntimeSingleton.parse(new StringReader(templateContent), "MyTemplate"), properties, includeDummies);
    return unsetAttributes.toArray(new String[unsetAttributes.size()]);
  }

  private static void addAttributesToVector(Set<String> references, Node apacheNode, Properties properties, boolean includeDummies){
    int childCount = apacheNode.jjtGetNumChildren();
    for(int i = 0; i < childCount; i++){
      Node apacheChild = apacheNode.jjtGetChild(i);
      addAttributesToVector(references, apacheChild, properties, includeDummies);
      if(apacheChild instanceof ASTReference){
        ASTReference apacheReference = (ASTReference)apacheChild;
        String s = apacheReference.literal();
        s = referenceToAttribute(s, includeDummies);
        if (s != null && s.length() > 0 && properties.getProperty(s) == null) references.add(s);
      }
    }
  }


  /**
   * Removes each two leading '\', removes leading $, removes {}
   * Examples:
   * $qqq   -> qqq
   * \$qqq  -> qqq if dummy attributes are collected too, null otherwise
   * \\$qqq -> qqq
   * ${qqq} -> qqq
   */
  private static String referenceToAttribute(String attrib, boolean includeDummies) {
    while (attrib.startsWith("\\\\")) {
      attrib = attrib.substring(2);
    }
    if (attrib.startsWith("\\$")) {
      if (includeDummies) {
        attrib = attrib.substring(1);
      }
      else return null;
    }
    if (!StringUtil.startsWithChar(attrib, '$')) {
      LOG.error("Invalid attribute: " + attrib);
    }
    attrib = attrib.substring(1);
    if (StringUtil.startsWithChar(attrib, '{')) {
      String cleanAttribute = null;
      for (int i = 1; i < attrib.length(); i++) {
        char currChar = attrib.charAt(i);
        if (currChar == '{' || currChar == '.') {
          // Invalid match
          cleanAttribute = null;
          break;
        }
        else if (currChar == '}') {
          // Valid match
          cleanAttribute = attrib.substring(1, i);
          break;
        }
      }
      attrib = cleanAttribute;
    }
    else {
      for (int i = 0; i < attrib.length(); i++) {
        char currChar = attrib.charAt(i);
        if (currChar == '{' || currChar == '}' || currChar == '.') {
          attrib = attrib.substring(0, i);
          break;
        }
      }
    }
    return attrib;
  }

  public static String mergeTemplate(Map attributes, String content) throws IOException{
    initVelocity();
    VelocityContext context = new VelocityContext();
    for (final Object o : attributes.keySet()) {
      String name = (String)o;
      context.put(name, attributes.get(name));
    }
    return mergeTemplate(content, context);
  }

  public static String mergeTemplate(Properties attributes, String content) throws IOException{
    initVelocity();
    VelocityContext context = new VelocityContext();
    Enumeration<?> names = attributes.propertyNames();
    while (names.hasMoreElements()){
      String name = (String)names.nextElement();
      context.put(name, attributes.getProperty(name));
    }
    return mergeTemplate(content, context);
  }

  private static String mergeTemplate(String templateContent, final VelocityContext context) throws IOException{
    initVelocity();
    StringWriter stringWriter = new StringWriter();
    try {
      Velocity.evaluate(context, stringWriter, "", templateContent);
    } catch (VelocityException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(IdeBundle.message("error.parsing.file.template"),
                                     IdeBundle.message("title.velocity.error"));
          }
        });
    }
    return stringWriter.toString();
  }

  public static FileTemplate cloneTemplate(FileTemplate template){
    FileTemplateImpl templateImpl = (FileTemplateImpl) template;
    return (FileTemplate)templateImpl.clone();
  }

  public static void copyTemplate(FileTemplate src, FileTemplate dest){
    dest.setExtension(src.getExtension());
    dest.setName(src.getName());
    dest.setText(src.getText());
    dest.setAdjust(src.isAdjust());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static synchronized void initVelocity(){
    try{
      if (ourVelocityInitialized) {
        return;
      }
      File modifiedPatternsPath = new File(PathManager.getConfigPath());
      modifiedPatternsPath = new File(modifiedPatternsPath, "fileTemplates");
      modifiedPatternsPath = new File(modifiedPatternsPath, "includes");

      LogSystem emptyLogSystem = new LogSystem() {
        public void init(RuntimeServices runtimeServices) throws Exception {
        }

        public void logVelocityMessage(int i, String s) {
          //todo[myakovlev] log somethere?
        }
      };
      Velocity.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM, emptyLogSystem);
      Velocity.setProperty(Velocity.RESOURCE_LOADER, "file,class");
      //todo[myakovlev] implement my oun Loader, with ability to load templates from classpath
      Velocity.setProperty("file.resource.loader.class", MyFileResourceLoader.class.getName());
      Velocity.setProperty("class.resource.loader.class", MyClasspathResourceLoader.class.getName());
      Velocity.setProperty(Velocity.FILE_RESOURCE_LOADER_PATH, modifiedPatternsPath.getAbsolutePath());
      Velocity.setProperty(Velocity.INPUT_ENCODING, FileTemplate.ourEncoding);
      Velocity.init();
      ourVelocityInitialized = true;
    }
    catch (Exception e){
      LOG.error("Unable to init Velocity", e);
    }
  }

  public static PsiElement createFromTemplate(final FileTemplate template, @NonNls final String fileName, Properties props, final Project project, final PsiDirectory directory) throws Exception{
    PsiElement[] result = new PsiElement[1];
    createFromTemplate(result, template, fileName, props, project, directory);
    return result[0];
  }

  public static boolean createFromTemplate(final PsiElement[] myCreatedElement, final FileTemplate template, final String fileName, Properties props, final Project project, final PsiDirectory directory) throws Exception{
    if (template == null){
      throw new IllegalArgumentException("template cannot be null");
    }
    if (props == null) {
      props = FileTemplateManager.getInstance().getDefaultProperties();
    }
    FileTemplateManager.getInstance().addRecentName(template.getName());

    //Set escaped references to dummy values to remove leading "\" (if not already explicitely set)
    String[] dummyRefs = calculateAttributes(template.getText(), props, true);
    for (String dummyRef : dummyRefs) {
      props.setProperty(dummyRef, "");
    }
    String mergedText;

    try{
      if (template.isJavaClassTemplate()){
        String packageName = props.getProperty(PACKAGE_ATTR);
        if(packageName == null || packageName.length() == 0){
          props = new Properties(props);
          props.setProperty(PACKAGE_ATTR, PACKAGE_ATTR);
        }
      }
      mergedText = template.getText(props);
    }
    catch (Exception e){
      throw e;
    }
    final String templateText = StringUtil.convertLineSeparators(mergedText);
    final Exception[] commandException = new Exception[1];
    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      public void run(){
        final Runnable run = new Runnable(){
          public void run(){
            try{
              FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
              if (fileType.equals(StdFileTypes.JAVA)) {
                String extension = template.getExtension();
                myCreatedElement[0] = createClassOrInterface(project, directory, templateText, template.isAdjust(), extension);
              }
              else{
                myCreatedElement[0] = createPsiFile(project, directory, templateText, fileName, template.getExtension());
              }
            }
            catch (Exception ex){
              commandException[0] = ex;
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(run);
      }
    }, template.isJavaClassTemplate()
       ? IdeBundle.message("command.create.class.from.template")
       : IdeBundle.message("command.create.file.from.template"), null);
    if(commandException[0] != null){
      throw commandException[0];
    }
    return true;
  }

  public static PsiClass createClassOrInterface(Project project,
                                                PsiDirectory directory,
                                                String content,
                                                boolean reformat,
                                                String extension) throws IncorrectOperationException{
    if (extension == null) extension = StdFileTypes.JAVA.getDefaultExtension();
    PsiJavaFile psiJavaFile = (PsiJavaFile)PsiManager.getInstance(project).getElementFactory().createFileFromText("myclass" + "." + extension, content);
    PsiClass[] classes = psiJavaFile.getClasses();
    if(classes.length == 0){
      throw new IncorrectOperationException("This template did not produce Java class nor interface!");
    }
    PsiClass createdClass = classes[0];
    if(reformat){
      CodeStyleManager.getInstance(project).reformat(psiJavaFile);
    }
    String className = createdClass.getName();
    String fileName = className + "." + extension;
    if(createdClass.isInterface()){
      directory.checkCreateInterface(className);
    }
    else{
      directory.checkCreateClass(className);
    }
    psiJavaFile = (PsiJavaFile)psiJavaFile.setName(fileName);
    psiJavaFile = (PsiJavaFile) directory.add(psiJavaFile);

    return psiJavaFile.getClasses()[0];
  }

  private static PsiFile createPsiFile(Project project, PsiDirectory directory, String content, String fileName, String extension) throws IncorrectOperationException{
    final String suggestedFileNameEnd = "." + extension;
    
    if (!fileName.endsWith(suggestedFileNameEnd)) {
      fileName += suggestedFileNameEnd;
    }
    
    directory.checkCreateFile(fileName);
    PsiFile file = PsiManager.getInstance(project).getElementFactory().createFileFromText(fileName, content);
    file = (PsiFile)directory.add(file);
    return file;
  }

  public static String indent(String methodText, Project project, FileType fileType) {
    int indent = CodeStyleSettingsManager.getSettings(project).getIndentSize(fileType);
    StringBuffer buf = new StringBuffer();
    for(int i = 0; i < indent; i++) buf.append(' ');

    return methodText.replaceAll("\n", "\n" + buf.toString());
  }

  @NonNls private static final String INCLUDES_PATH = "fileTemplates/includes/";

  public static class MyClasspathResourceLoader extends ClasspathResourceLoader{
    @NonNls private static final String FT_EXTENSION = ".ft";

    public synchronized InputStream getResourceStream(String name) throws ResourceNotFoundException{
      InputStream resourceStream = super.getResourceStream(INCLUDES_PATH + name + FT_EXTENSION);
      return resourceStream;
    }
  }

  public static class MyFileResourceLoader extends FileResourceLoader{
    public void init(ExtendedProperties configuration){
      super.init(configuration);

      File modifiedPatternsPath = new File(PathManager.getConfigPath());
      modifiedPatternsPath = new File(modifiedPatternsPath, INCLUDES_PATH);

      try{
        //noinspection HardCodedStringLiteral
        Field pathsField = FileResourceLoader.class.getDeclaredField("paths");
        pathsField.setAccessible(true);
        Vector<String> paths = (Vector<String>)pathsField.get(this);
        paths.removeAllElements();
        paths.addElement(modifiedPatternsPath.getAbsolutePath());
        if(ApplicationManager.getApplication().isUnitTestMode()){
          File file1 = new File(PathManagerEx.getTestDataPath());
          //noinspection HardCodedStringLiteral
          File testsDir = new File(new File(file1, "ide"), "fileTemplates");
          paths.add(testsDir.getAbsolutePath());
        }
      }
      catch (Exception e){
        throw new RuntimeException(e);
      }
    }
  }

  public static void setClassAndMethodNameProperties (Properties properties, PsiClass aClass, PsiMethod method) {
    String className = aClass.getQualifiedName();
    if (className == null) className = "";
    properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, className);

    String methodName = method.getName();
    properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, methodName);
  }

  public static void setPackageNameAttribute (Properties properties, PsiDirectory directory) {
    PsiPackage aPackage = directory.getPackage();
    if (aPackage != null) {
      String packageName = aPackage.getQualifiedName();
      if (packageName.length() > 0) {
        properties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, packageName);
        return;
      }
    }
    properties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, FileTemplate.ATTRIBUTE_PACKAGE_NAME);
  }

  public static boolean canCreateFromTemplate (PsiDirectory[] dirs, FileTemplate template) {
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
    if (fileType.equals(StdFileTypes.UNKNOWN)) return false;
    if (
      template.isJavaClassTemplate() ||
      fileType.equals(StdFileTypes.GUI_DESIGNER_FORM)
    ){
      for (PsiDirectory dir : dirs) {
        if (dir.getPackage() != null) return true;
      }
      return false;
    }
    return true;
  }

}
