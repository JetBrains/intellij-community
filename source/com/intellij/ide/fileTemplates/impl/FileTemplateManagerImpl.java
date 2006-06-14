package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author MYakovlev
 *         Date: Jul 24
 * @author 2002
 */
public class FileTemplateManagerImpl extends FileTemplateManager implements ExportableApplicationComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl");
  @NonNls private static final String DEFAULT_TEMPLATE_EXTENSION = "ft";
  @NonNls private static final String DEFAULT_TEMPLATES_TOP_DIR = "fileTemplates";
  @NonNls private static final String INTERNAL_DIR = "internal";
  @NonNls private static final String INCLUDES_DIR = "includes";
  @NonNls private static final String CODETEMPLATES_DIR = "code";
  @NonNls  private static final String J2EE_TEMPLATES_DIR = "j2ee";

  private String myDefaultTemplatesDir = ".";
  @NonNls private String myTemplatesDir = "fileTemplates";
  private MyTemplates myTemplates;
  private RecentTemplatesManager myRecentList = new RecentTemplatesManager();
  private boolean myInvalidated = true;
  private FileTemplateManagerImpl myInternalTemplatesManager;
  private FileTemplateManagerImpl myPatternsManager;
  private FileTemplateManagerImpl myCodeTemplatesManager;
  private FileTemplateManagerImpl myJ2eeTemplatesManager;
  private VirtualFile myDefaultDescription;

  private static VirtualFile[] ourTopDirs;
  private VirtualFileManager myVirtualFileManager;
  private FileTypeManagerEx myTypeManager;
  @NonNls private static final String ELEMENT_DELETED_TEMPLATES = "deleted_templates";
  @NonNls private static final String ELEMENT_DELETED_INCLUDES = "deleted_includes";
  @NonNls private static final String ELEMENT_RECENT_TEMPLATES = "recent_templates";
  @NonNls private static final String ELEMENT_TEMPLATES = "templates";
  @NonNls private static final String ELEMENT_INTERNAL_TEMPLATE = "internal_template";
  @NonNls private static final String ELEMENT_TEMPLATE = "template";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_REFORMAT = "reformat";

  private Map<String, String> myLocalizedTemplateNames = new HashMap<String, String>();

  public static FileTemplateManagerImpl getInstance(){
    return (FileTemplateManagerImpl)ApplicationManager.getApplication().getComponent(FileTemplateManager.class);
  }

  public FileTemplateManagerImpl(VirtualFileManager virtualFileManager, FileTypeManagerEx fileTypeManagerEx) {
    myVirtualFileManager = virtualFileManager;
    myTypeManager = fileTypeManagerEx;
    myInternalTemplatesManager = new FileTemplateManagerImpl(INTERNAL_DIR,
                                                             myTemplatesDir + File.separator + INTERNAL_DIR, myVirtualFileManager, myTypeManager);
    myPatternsManager = new FileTemplateManagerImpl(INCLUDES_DIR, myTemplatesDir + File.separator + INCLUDES_DIR, myVirtualFileManager, myTypeManager);
    myCodeTemplatesManager = new FileTemplateManagerImpl(CODETEMPLATES_DIR,
                                                         myTemplatesDir + File.separator + CODETEMPLATES_DIR, myVirtualFileManager, myTypeManager);
    myJ2eeTemplatesManager = new FileTemplateManagerImpl(J2EE_TEMPLATES_DIR,
                                                         myTemplatesDir + File.separator + J2EE_TEMPLATES_DIR, myVirtualFileManager, myTypeManager);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            migrateTemplatesFromBuild915();
          }
        });
      }
    });

    myLocalizedTemplateNames.put(TEMPLATE_CATCH_BODY, IdeBundle.message("template.catch.statement.body"));
    myLocalizedTemplateNames.put(TEMPLATE_IMPLEMENTED_METHOD_BODY, IdeBundle.message("template.implemented.method.body"));
    myLocalizedTemplateNames.put(TEMPLATE_OVERRIDDEN_METHOD_BODY, IdeBundle.message("template.overridden.method.body"));
    myLocalizedTemplateNames.put(TEMPLATE_FROM_USAGE_METHOD_BODY, IdeBundle.message("template.new.method.body"));
    myLocalizedTemplateNames.put(TEMPLATE_I18NIZED_EXPRESSION, IdeBundle.message("template.i18nized.expression"));
    myLocalizedTemplateNames.put(TEMPLATE_I18NIZED_CONCATENATION, IdeBundle.message("template.i18nized.concatenation"));
    myLocalizedTemplateNames.put(TEMPLATE_I18NIZED_JSP_EXPRESSION, IdeBundle.message("template.i18nized.jsp.expression"));
    myLocalizedTemplateNames.put(INTERNAL_CLASS_TEMPLATE_NAME, IdeBundle.message("template.class"));
    myLocalizedTemplateNames.put(INTERNAL_INTERFACE_TEMPLATE_NAME, IdeBundle.message("template.interface"));
    myLocalizedTemplateNames.put(INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME, IdeBundle.message("template.annotationtype"));
    myLocalizedTemplateNames.put(INTERNAL_ENUM_TEMPLATE_NAME, IdeBundle.message("template.enum"));
    myLocalizedTemplateNames.put(FILE_HEADER_TEMPLATE_NAME, IdeBundle.message("template.file.header"));
  }

  private FileTemplateManagerImpl(String defaultTemplatesDir,
                                  String templatesDir,
                                  VirtualFileManager virtualFileManager,
                                  FileTypeManagerEx fileTypeManagerEx) {
    myDefaultTemplatesDir = defaultTemplatesDir;
    myTemplatesDir = templatesDir;
    myVirtualFileManager = virtualFileManager;
    myTypeManager = fileTypeManagerEx;
  }

  public File[] getExportFiles() {
    return new File[]{getParentDirectory(false), PathManager.getDefaultOptionsFile()};
  }

  public String getPresentableName() {
    return IdeBundle.message("item.file.templates");
  }

  public FileTemplate[] getAllTemplates() {
    revalidate();
    ensureTemplatesAreLoaded();
    return myTemplates.getAllTemplates();
  }

  public FileTemplate getTemplate(String templateName) {
    ensureTemplatesAreLoaded();
    return myTemplates.findByName(templateName);
  }

  public FileTemplate addTemplate(String name, @NonNls String extension) {
    revalidate();
    ensureTemplatesAreLoaded();
    LOG.assertTrue(name != null);
    LOG.assertTrue(name.length() > 0);
    if (myTemplates.findByName(name) != null) {
      LOG.error("Duplicate template " + name);
    }

    FileTemplate fileTemplate = new FileTemplateImpl("", name, extension);
    myTemplates.addTemplate(fileTemplate);
    return fileTemplate;
  }

  public void removeTemplate(FileTemplate template, boolean fromDiskOnly) {
    ensureTemplatesAreLoaded();
    myTemplates.removeTemplate(template);
    try {
      ((FileTemplateImpl) template).removeFromDisk();
    } catch (Exception e) {
      LOG.error("Unable to remove template", e);
    }

    if (!fromDiskOnly) {
      myDeletedTemplatesManager.addName(template.getName() + "." + template.getExtension() + "." +
          DEFAULT_TEMPLATE_EXTENSION);
    }

    revalidate();
  }

  public void removeInternal(FileTemplate template) {
    LOG.assertTrue(myInternalTemplatesManager != null);
    myInternalTemplatesManager.removeTemplate(template, true);
  }

  public Properties getDefaultProperties() {
    @NonNls Properties props = new Properties();

    Date date = new Date();
    props.setProperty("DATE", DateFormat.getDateInstance().format(date));
    props.setProperty("TIME", DateFormat.getTimeInstance().format(date));
    Calendar calendar = Calendar.getInstance();
    props.setProperty("YEAR", Integer.toString(calendar.get(Calendar.YEAR)));
    props.setProperty("MONTH", Integer.toString(calendar.get(Calendar.MONTH) + 1)); //to correct Calendar bias to 0
    props.setProperty("DAY", Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)));
    props.setProperty("HOUR", Integer.toString(calendar.get(Calendar.HOUR_OF_DAY)));
    props.setProperty("MINUTE", Integer.toString(calendar.get(Calendar.MINUTE)));

    props.setProperty("USER", System.getProperty("user.name"));

    return props;
  }

  private File getParentDirectory(boolean create) {
    File configPath = new File(PathManager.getConfigPath());
    File templatesPath = new File(configPath, myTemplatesDir);
    if (!templatesPath.exists()) {
      if (create) {
        final boolean created = templatesPath.mkdirs();
        if (!created) {
          LOG.error("Cannot create directory: " + templatesPath.getAbsolutePath());
        }
      }
    }
    return templatesPath;
  }

  private void ensureTemplatesAreLoaded() {
    if (!myInvalidated) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        loadTemplates();
      }
    });
  }

  private void loadTemplates() {
    VirtualFile[] defaultTemplates = getDefaultTemplates();
    for (VirtualFile file : defaultTemplates) {
      //noinspection HardCodedStringLiteral
      if (file.getName().equals("default.html")) {
        myDefaultDescription = file;           //todo[myakovlev]
      }
    }

    File templateDir = getParentDirectory(false);
    File[] files = templateDir.listFiles();
    if (files == null) {
      files = new File[0];
    }

    if (myTemplates == null) {
      myTemplates = new MyTemplates();
    }
    List<FileTemplate> existingTemplates = new ArrayList<FileTemplate>();
    // Read user-defined templates
    for (File file : files) {
      if (file.isDirectory()) {
        continue;
      }
      String name = file.getName();
      String extension = myTypeManager.getExtension(name);
      name = name.substring(0, name.length() - extension.length() - 1);
      FileTemplate existing = myTemplates.findByName(name);
      if (existing == null || existing.isDefault()) {
        if (existing != null) {
          myTemplates.removeTemplate(existing);
        }
        FileTemplateImpl fileTemplate = new FileTemplateImpl(file, name, extension, false);
        //fileTemplate.setDescription(myDefaultDescription);   default description will be shown
        myTemplates.addTemplate(fileTemplate);
        existingTemplates.add(fileTemplate);
      } else {
        // it is a user-defined template, revalidate it
        LOG.assertTrue(!((FileTemplateImpl) existing).isModified());
        ((FileTemplateImpl) existing).invalidate();
        existingTemplates.add(existing);
      }
    }
    LOG.debug("FileTemplateManagerImpl.loadTemplates() reading default templates...");
    // Read default templates
    for (VirtualFile file : defaultTemplates) {
      String name = file.getName();                                                       //name.extension.ft  , e.g.  "NewClass.java.ft"
      @NonNls String extension = myTypeManager.getExtension(name);
      name = name.substring(0, name.length() - extension.length() - 1);                   //name="NewClass.java"   extension="ft"
      if (extension.equals("html")) {
        continue;
      }
      if (!extension.equals(DEFAULT_TEMPLATE_EXTENSION)) {
        LOG.error(file.toString() + " should have *." + DEFAULT_TEMPLATE_EXTENSION + " extension!");
      }
      extension = myTypeManager.getExtension(name);
      name = name.substring(0, name.length() - extension.length() - 1);                   //name="NewClass"   extension="java"
      FileTemplate aTemplate = myTemplates.findByName(name);
      if (aTemplate == null) {
        FileTemplate fileTemplate = new FileTemplateImpl(file, name, extension);
        myTemplates.addTemplate(fileTemplate);
        aTemplate = fileTemplate;
      }
      VirtualFile description = getDescriptionForTemplate(file);
      if (description != null) {
        ((FileTemplateImpl)aTemplate).setDescription(description);
      }
      /*else{
        ((FileTemplateImpl)aTemplate).setDescription(myDefaultDescription);
      }*/
    }
    FileTemplate[] allTemplates = myTemplates.getAllTemplates();
    for (FileTemplate template : allTemplates) {
      FileTemplateImpl templateImpl = (FileTemplateImpl) template;
      if (!templateImpl.isDefault()) {
        if (!existingTemplates.contains(templateImpl)) {
          if (!templateImpl.isNew()) {
            myTemplates.removeTemplate(templateImpl);
            templateImpl.removeFromDisk();
          }
        }
      }
    }

    myInvalidated = false;
  }


  private void saveTemplates() {
    try {
      saveTemplates_();
      if (myInternalTemplatesManager != null) {
        myInternalTemplatesManager.saveTemplates();
      }
      if (myPatternsManager != null) {
        myPatternsManager.saveTemplates();
      }
      if (myCodeTemplatesManager != null) {
        myCodeTemplatesManager.saveTemplates();
      }
      if (myJ2eeTemplatesManager != null) {
        myJ2eeTemplatesManager.saveTemplates();
      }
    } catch (IOException e) {
      LOG.error("Unable to save templates", e);
    }
  }

  private void saveTemplates_() throws IOException {
    if (myTemplates == null) {
      return;
    }
    FileTemplate[] allTemplates = myTemplates.getAllTemplates();
    for (FileTemplate template : allTemplates) {
      FileTemplateImpl templateImpl = (FileTemplateImpl) template;
      if (templateImpl.isModified()) {
        templateImpl.writeExternal(getParentDirectory(true));
      }
    }
  }

  public Collection<String> getRecentNames() {
    ensureTemplatesAreLoaded();
    validateRecentNames();
    return myRecentList.getRecentNames(RECENT_TEMPLATES_SIZE);
  }

  public void addRecentName(String name) {
    myRecentList.addName(name);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getComponentName() {
    return "FileTemplateManager";
  }

  private MyDeletedTemplatesManager myDeletedTemplatesManager = new MyDeletedTemplatesManager();

  public void readExternal(Element element) throws InvalidDataException {
    Element deletedTemplatesElement = element.getChild(ELEMENT_DELETED_TEMPLATES);
    if (deletedTemplatesElement != null) {
      myDeletedTemplatesManager.readExternal(deletedTemplatesElement);
    }

    Element deletedIncludesElement = element.getChild(ELEMENT_DELETED_INCLUDES);
    if (deletedIncludesElement != null) {
      myPatternsManager.myDeletedTemplatesManager.readExternal(deletedIncludesElement);
    }

    Element recentElement = element.getChild(ELEMENT_RECENT_TEMPLATES);
    if (recentElement != null) {
      myRecentList.readExternal(recentElement);
    }

    Element templatesElement = element.getChild(ELEMENT_TEMPLATES);
    if (templatesElement != null) {
      revalidate();
      FileTemplate[] internals = getInternalTemplates();
      List children = templatesElement.getChildren();
      for (final Object aChildren : children) {
        Element child = (Element)aChildren;
        String name = child.getAttributeValue(ATTRIBUTE_NAME);
        boolean reformat = Boolean.TRUE.toString().equals(child.getAttributeValue(ATTRIBUTE_REFORMAT));
        if (child.getName().equals(ELEMENT_INTERNAL_TEMPLATE)) {
          for (FileTemplate internal : internals) {
            if (name.equals(internal.getName())) internal.setAdjust(reformat);
          }
        }
        else if (child.getName().equals(ELEMENT_TEMPLATE)) {
          FileTemplate template = getTemplate(name);
          if (template != null) {
            template.setAdjust(reformat);
          }
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    saveTemplates();
    validateRecentNames();

    Element deletedTemplatesElement = new Element(ELEMENT_DELETED_TEMPLATES);
    element.addContent(deletedTemplatesElement);
    myDeletedTemplatesManager.writeExternal(deletedTemplatesElement);

    Element deletedIncludesElement = new Element(ELEMENT_DELETED_INCLUDES);
    element.addContent(deletedIncludesElement);
    myPatternsManager.myDeletedTemplatesManager.writeExternal(deletedIncludesElement);

    Element recentElement = new Element(ELEMENT_RECENT_TEMPLATES);
    element.addContent(recentElement);
    myRecentList.writeExternal(recentElement);

    Element templatesElement = new Element(ELEMENT_TEMPLATES);
    element.addContent(templatesElement);
    revalidate();
    FileTemplate[] internals = getInternalTemplates();
    for (FileTemplate internal : internals) {
      templatesElement.addContent(createElement(internal, true));
    }

    FileTemplate[] allTemplates = getAllTemplates();
    for (FileTemplate fileTemplate : allTemplates) {
      templatesElement.addContent(createElement(fileTemplate, false));
    }
  }

  private static Element createElement(FileTemplate template, boolean isInternal) {
    Element templateElement = new Element(isInternal ? ELEMENT_INTERNAL_TEMPLATE : ELEMENT_TEMPLATE);
    templateElement.setAttribute(ATTRIBUTE_NAME, template.getName());
    templateElement.setAttribute(ATTRIBUTE_REFORMAT, Boolean.toString(template.isAdjust()));
    return templateElement;
  }

  private void validateRecentNames() {
    if (myTemplates != null) {
      List<String> allNames = new ArrayList<String>(myTemplates.size());
      FileTemplate[] allTemplates = myTemplates.getAllTemplates();
      for (FileTemplate fileTemplate : allTemplates) {
        allNames.add(fileTemplate.getName());
      }
      myRecentList.validateNames(allNames);
    }
  }

  private void revalidate() {
    saveAll();
    myInvalidated = true;
    if (myTemplates != null) {
      FileTemplate[] allTemplates = myTemplates.getAllTemplates();
      for (FileTemplate template : allTemplates) {
        ((FileTemplateImpl) template).invalidate();
      }
    }
  }

  public void saveAll() {
    saveTemplates();
  }

  public FileTemplate[] getInternalTemplates() {
    FileTemplate[] result = new FileTemplate[6];
    result[0] = getInternalTemplate(INTERNAL_CLASS_TEMPLATE_NAME);
    result[1] = getInternalTemplate(INTERNAL_INTERFACE_TEMPLATE_NAME);
    result[2] = getInternalTemplate(INTERNAL_ENUM_TEMPLATE_NAME);
    result[3] = getInternalTemplate(INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME);
    result[4] = getInternalTemplate(INTERNAL_HTML_TEMPLATE_NAME);
    result[5] = getInternalTemplate(INTERNAL_XHTML_TEMPLATE_NAME);
    return result;
  }

  public FileTemplate getInternalTemplate(String templateName) {
    LOG.assertTrue(myInternalTemplatesManager != null);
    //noinspection HardCodedStringLiteral
    String actualTemplateName = ApplicationManager.getApplication().isUnitTestMode() ? templateName + "ForTest" : templateName;
    FileTemplateImpl template = (FileTemplateImpl) myInternalTemplatesManager.getTemplate(actualTemplateName);

    if (template == null) {
      template = (FileTemplateImpl)getJ2eeTemplate(actualTemplateName); // Hack to be able to register class templates from the plugin.
      if (template != null) {
        template.setAdjust(true);
      }
      else {
        String text;
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          text = getDefaultClassTemplateText(templateName);
        }
        else {
          text = getTestClassTemplateText(templateName);
        }

        text = StringUtil.convertLineSeparators(text);
        text = StringUtil.replace(text, "$NAME$", "${NAME}");
        text = StringUtil.replace(text, "$PACKAGE_NAME$", "${PACKAGE_NAME}");
        text = StringUtil.replace(text, "$DATE$", "${DATE}");
        text = StringUtil.replace(text, "$TIME$", "${TIME}");
        text = StringUtil.replace(text, "$USER$", "${USER}");

        template = (FileTemplateImpl)myInternalTemplatesManager.addTemplate(actualTemplateName, "java");
        template.setText(text);
      }
    }

    template.setInternal(true);
    return template;
  }

  @NonNls
  private String getTestClassTemplateText(String templateName) {
    return "package $PACKAGE_NAME$;\npublic " + internalTemplateToSubject(templateName) + " $NAME$ { }";
  }

  public String internalTemplateToSubject(String templateName) {
    //noinspection HardCodedStringLiteral
    return INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME.equals(templateName) ? "@interface" : templateName.toLowerCase();
  }

  public String localizeInternalTemplateName(final FileTemplate template) {
    String localizedName = myLocalizedTemplateNames.get(template.getName());
    if (localizedName == null) {
      localizedName = myLocalizedTemplateNames.get(template.getName() + "." + template.getExtension());
    }
    return localizedName != null ? localizedName : template.getName();
  }

  @NonNls
  private String getDefaultClassTemplateText(String templateName) {
    return IdeBundle.message("template.default.class.comment", ApplicationNamesInfo.getInstance().getFullProductName()) +
           "package $PACKAGE_NAME$;\n" +
           "public " + internalTemplateToSubject(templateName) + " $NAME$ { }";
  }

  public FileTemplate getCodeTemplate(String templateName) {
    return getTemplateFromManager(templateName, myCodeTemplatesManager, "Code");
  }

  public FileTemplate getJ2eeTemplate(String templateName) {
    return getTemplateFromManager(templateName, myJ2eeTemplatesManager, "J2EE");
  }

  private FileTemplate getTemplateFromManager(String templateName,
                                              FileTemplateManagerImpl templatesManager,
                                              @NonNls String templateType) {
    LOG.assertTrue(templatesManager != null);
    String name = templateName;
    String extension = myTypeManager.getExtension(name);
    if (extension.length() > 0) {
      name = name.substring(0, name.length() - extension.length() - 1);
    }
    FileTemplate template = templatesManager.getTemplate(name);
    if (template != null) {
      if (extension.equals(template.getExtension())) {
        return template;
      }
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode() && templateName.endsWith("ForTest")) return null;

      VirtualFile[] defaultTemplates = templatesManager.getDefaultTemplates();
      @NonNls String message = "Unable to find " + templateType + " Template '" + templateName + "'! Default " + templateType +
                               " Templates are: ";
      if (defaultTemplates != null) {
        for (int i = 0; i < defaultTemplates.length; i++) {
          VirtualFile defaultTemplate = defaultTemplates[i];
          if (i != 0) {
            message += ", ";
          }
          message += defaultTemplate.getPresentableUrl();
        }
      }
      LOG.error(message);
    }
    return null;
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  private VirtualFile getDescriptionForTemplate(VirtualFile vfile) {
    if (vfile != null) {
      VirtualFile parent = vfile.getParent();
      assert parent != null;
      String name = vfile.getName();                                                    //name.extension.ft  , f.e.  "NewClass.java.ft"
      String extension = myTypeManager.getExtension(name);
      if (extension.equals(DEFAULT_TEMPLATE_EXTENSION)) {
        name = name.substring(0, name.length() - extension.length() - 1);                   //name="NewClass.java"   extension="ft"

        Locale locale = Locale.getDefault();
        String descName = MessageFormat.format("{0}_{1}_{2}.html",
                                               name, locale.getLanguage(), locale.getCountry());
        VirtualFile descFile = parent.findChild(descName);
        if (descFile != null && descFile.isValid()) {
          return descFile;
        }

        descName = MessageFormat.format("{0}_{1}.html", name, locale.getLanguage());
        descFile = parent.findChild(descName);
        if (descFile != null && descFile.isValid()) {
          return descFile;
        }

        descFile = parent.findChild(name + ".html");
        if (descFile != null && descFile.isValid()) {
          return descFile;
        }
      }
    }
    return null;
  }

  private static List<VirtualFile> listDir(VirtualFile vfile) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    if (vfile != null && vfile.isDirectory()) {
      VirtualFile[] children = vfile.getChildren();
      for (VirtualFile child : children) {
        if (!child.isDirectory()) {
          result.add(child);
        }
      }
    }
    return result;
  }

  private void removeDeletedTemplates(Set<VirtualFile> files) {
    Set<VirtualFile> removedSet = new HashSet<VirtualFile>();

    for (VirtualFile file: files) {
      String nameWithExtension = file.getName();
      if (myDeletedTemplatesManager.contains(nameWithExtension)) {
        removedSet.add(file);
      }
    }

    files.removeAll(removedSet);
  }

  private static VirtualFile getDefaultFromManager(String name, String extension, FileTemplateManagerImpl manager) {
    if (manager == null) return null;
    VirtualFile[] files = manager.getDefaultTemplates();
    for (VirtualFile file : files) {
      if (DEFAULT_TEMPLATE_EXTENSION.equals(file.getExtension())) {
        String fullName = file.getNameWithoutExtension(); //Strip .ft
        if (fullName.equals(name + "." + extension)) return file;
      }
    }
    return null;
  }

  public VirtualFile getDefaultTemplate(String name, String extension) {
    VirtualFile result;
    if ((result = getDefaultFromManager(name, extension, this)) != null) return result;
    if ((result = getDefaultFromManager(name, extension, myInternalTemplatesManager)) != null) return result;
    if ((result = getDefaultFromManager(name, extension, myPatternsManager)) != null) return result;
    if ((result = getDefaultFromManager(name, extension, myJ2eeTemplatesManager)) != null) return result;
    return getDefaultFromManager(name, extension, myCodeTemplatesManager);
  }

  public FileTemplate getDefaultTemplate(String name) {
    @NonNls String extension = myTypeManager.getExtension(name);
    String nameWithoutExtension = StringUtil.trimEnd(name, "."+extension);
    if (extension.length() == 0) {
      extension = "java";
    }
    VirtualFile file = getDefaultTemplate(nameWithoutExtension, extension);
    if (file == null) return null;
    FileTemplateImpl fileTemplate = new FileTemplateImpl(file, nameWithoutExtension, extension);
    return fileTemplate;
  }

  private VirtualFile[] getDefaultTemplates() {
    if (myDefaultTemplatesDir == null || myDefaultTemplatesDir.length() == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    VirtualFile[] topDirs = getTopTemplatesDir();
    if (LOG.isDebugEnabled()) {
      @NonNls String message = "Top dirs found: ";
      for (int i = 0; i < topDirs.length; i++) {
        VirtualFile topDir = topDirs[i];
        message += (i > 0 ? ", " : "") + topDir.getPresentableUrl();
      }
      LOG.debug(message);
    }
    Set<VirtualFile> templatesList = new HashSet<VirtualFile>();
    for (VirtualFile topDir : topDirs) {
      VirtualFile parentDir = myDefaultTemplatesDir.equals(".") ? topDir : topDir.findChild(myDefaultTemplatesDir);
      if (parentDir != null) {
        templatesList.addAll(listDir(parentDir));
      }
    }
    removeDeletedTemplates(templatesList);

    return templatesList.toArray(new VirtualFile[templatesList.size()]);
  }

  private VirtualFile[] getTopTemplatesDir() {
    if (ourTopDirs != null) {
      return ourTopDirs;
    }

    Set<VirtualFile> dirList = new HashSet<VirtualFile>();

    appendDefaultTemplatesFromClassloader(FileTemplateManagerImpl.class.getClassLoader(), dirList);
    final Application app = ApplicationManager.getApplication();
    PluginDescriptor[] plugins = app.getPlugins();
    for (PluginDescriptor plugin : plugins) {
      appendDefaultTemplatesFromClassloader(plugin.getPluginClassLoader(), dirList);
    }

    ourTopDirs = dirList.toArray(new VirtualFile[dirList.size()]);
    return ourTopDirs;
  }

  private void appendDefaultTemplatesFromClassloader(ClassLoader classLoader, Set<VirtualFile> dirList) {
    try {
      Enumeration systemResources = classLoader.getResources(DEFAULT_TEMPLATES_TOP_DIR);
      if (systemResources != null && systemResources.hasMoreElements()) {
        Set<URL> urls = new HashSet<URL>();
        while (systemResources.hasMoreElements()) {
          URL nextURL = (URL) systemResources.nextElement();
          if (!urls.contains(nextURL)) {
            urls.add(nextURL);
            VirtualFile dir = VfsUtil.findFileByURL(nextURL, myVirtualFileManager);
            if (dir == null) {
              LOG.error("Cannot find file by URL: " + nextURL);
            }
            if (LOG.isDebugEnabled()) {
              LOG.debug("Top directory: " + dir.getPresentableUrl());
            }
            dirList.add(dir);
          }
        }
      }
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  public FileTemplate[] getAllPatterns() {
    return myPatternsManager.getAllTemplates();
  }

  public FileTemplate getPattern(String name) {
    return myPatternsManager.getTemplate(name);
  }

  public FileTemplate addPattern(String name, String extension) {
    LOG.assertTrue(myPatternsManager != null);
    return myPatternsManager.addTemplate(name, extension);
  }

  public void removePattern(FileTemplate template, boolean fromDiskOnly) {
    LOG.assertTrue(myPatternsManager != null);
    myPatternsManager.removeTemplate(template, fromDiskOnly);
  }

  public FileTemplate[] getAllCodeTemplates() {
    LOG.assertTrue(myCodeTemplatesManager != null);
    return myCodeTemplatesManager.getAllTemplates();
  }

  public FileTemplate[] getAllJ2eeTemplates() {
    LOG.assertTrue(myJ2eeTemplatesManager != null);
    return myJ2eeTemplatesManager.getAllTemplates();
  }

  public FileTemplate addCodeTemplate(String name, String extension) {
    LOG.assertTrue(myCodeTemplatesManager != null);
    return myCodeTemplatesManager.addTemplate(name, extension);
  }

  public FileTemplate addJ2eeTemplate(String name, String extension) {
    LOG.assertTrue(myJ2eeTemplatesManager != null);
    return myJ2eeTemplatesManager.addTemplate(name, extension);
  }

  public void removeCodeTemplate(FileTemplate template, boolean fromDiskOnly) {
    LOG.assertTrue(myCodeTemplatesManager != null);
    myCodeTemplatesManager.removeTemplate(template, fromDiskOnly);
  }

  public void removeJ2eeTemplate(FileTemplate template, boolean fromDiskOnly) {
    LOG.assertTrue(myJ2eeTemplatesManager != null);
    myJ2eeTemplatesManager.removeTemplate(template, fromDiskOnly);
  }

  public VirtualFile getDefaultTemplateDescription() {
    return myDefaultDescription;
  }

  public VirtualFile getDefaultIncludeDescription() {
    return myPatternsManager.myDefaultDescription;
  }

  private static class MyTemplates {
    private List<FileTemplate> myTemplatesList = new ArrayList<FileTemplate>();

    public int size() {
      return myTemplatesList.size();
    }

    public void removeTemplate(FileTemplate template) {
      myTemplatesList.remove(template);
    }

    public FileTemplate[] getAllTemplates() {
      return myTemplatesList.toArray(new FileTemplate[myTemplatesList.size()]);
    }

    public FileTemplate findByName(String name) {
      for (FileTemplate template : myTemplatesList) {
        if (template.getName().equals(name)) {
          return template;
        }
      }
      return null;
    }

    public void addTemplate(FileTemplate newTemplate) {
      for (FileTemplate template : myTemplatesList) {
        if (template == newTemplate) {
          return;
        }
        if (template.getName().compareToIgnoreCase(newTemplate.getName()) > 0) {
          myTemplatesList.add(myTemplatesList.indexOf(template), newTemplate);
          return;
        }
      }
      myTemplatesList.add(newTemplate);
    }
  }

  static class MyDeletedTemplatesManager implements JDOMExternalizable {
    public JDOMExternalizableStringList DELETED_DEFAULT_TEMPLATES = new JDOMExternalizableStringList();

    public void addName(String nameWithExtension) {
      if (nameWithExtension != null) {
        DELETED_DEFAULT_TEMPLATES.remove(nameWithExtension);
        DELETED_DEFAULT_TEMPLATES.add(nameWithExtension);
      }
    }

    public boolean contains(String nameWithExtension) {
      return DELETED_DEFAULT_TEMPLATES.contains(nameWithExtension);
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

  }

  public static class RecentTemplatesManager implements JDOMExternalizable {
    public JDOMExternalizableStringList RECENT_TEMPLATES = new JDOMExternalizableStringList();

    public void addName(String name) {
      if (name != null) {
        RECENT_TEMPLATES.remove(name);
        RECENT_TEMPLATES.add(name);
      }
    }

    public Collection<String> getRecentNames(int max) {
      int size = RECENT_TEMPLATES.size();
      int resultSize = Math.min(max, size);
      return RECENT_TEMPLATES.subList(size - resultSize, size);
    }

    public void validateNames(List<String> validNames) {
      RECENT_TEMPLATES.retainAll(validNames);
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

  }


  private static final Map<String, String> old2NewNames = new com.intellij.util.containers.HashMap<String, String>();

  static {
    //noinspection HardCodedStringLiteral
    old2NewNames.put("NewClass", IdeBundle.message("template.class"));
    //noinspection HardCodedStringLiteral
    old2NewNames.put("NewInterface", IdeBundle.message("template.interface"));
    //noinspection HardCodedStringLiteral
    old2NewNames.put("CatchBody", IdeBundle.message("template.catch.statement.body"));
    //noinspection HardCodedStringLiteral
    old2NewNames.put("ImplementedFunction", IdeBundle.message("template.implemented.method.body"));
    //noinspection HardCodedStringLiteral
    old2NewNames.put("OverridenFunction", IdeBundle.message("template.overridden.method.body"));
    //noinspection HardCodedStringLiteral
    old2NewNames.put("FromUsageFunction", IdeBundle.message("template.new.method.body"));
  }

  private static boolean userWasAsked = false;
  private static boolean shouldConvert = false;
  private static boolean wasConverted = false;

  // migrate templates to the build 915+ format
  // return migrated templates
  private void migrateTemplatesFromBuild915() {

    if (myInternalTemplatesManager != null) {
      userWasAsked = false;
      shouldConvert = false;
      wasConverted = false;
      myInternalTemplatesManager.migrateTemplatesFromBuild915();
      myCodeTemplatesManager.migrateTemplatesFromBuild915();
      if (wasConverted) {
        saveAll();
      }
    } else {
      final FileTemplate[] templates = getAllTemplates();
      for (FileTemplate template : templates) {
        final String name = template.getName();
        final String extension = template.getExtension();
        final String newName = old2NewNames.get(name);
        if (newName != null && !template.isDefault() && getDefaultTemplate(newName, extension) != null) {
          if (!userWasAsked) {
            final int ret =
                Messages.showOkCancelDialog(IdeBundle.message("prompt.convert.file.templates"),
                                            IdeBundle.message("title.old.file.templates.found"), Messages.getQuestionIcon());
            userWasAsked = true;
            shouldConvert = ret == 0;
          }
          if (shouldConvert) {
            final FileTemplate defaultTemplate = myTemplates.findByName(newName);
            if (defaultTemplate != null) {
              // avoid duplicates
              myTemplates.removeTemplate(defaultTemplate);
            }
            // load text from file before delete
            template.getText();
            template.setName(newName);

            wasConverted = true;
          }
        }
      }
    }
  }

}
