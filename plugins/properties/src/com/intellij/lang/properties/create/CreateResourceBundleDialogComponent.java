/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.create;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class CreateResourceBundleDialogComponent {
  private final static Logger LOG = Logger.getInstance(CreateResourceBundleDialogComponent.class);

  private static final Comparator<Locale> LOCALE_COMPARATOR = new Comparator<Locale>() {
    @Override
    public int compare(Locale l1, Locale l2) {
      if (l1 == PropertiesUtil.DEFAULT_LOCALE) {
        return -1;
      }
      if (l2 == PropertiesUtil.DEFAULT_LOCALE) {
        return 1;
      }
      return l1.toString().compareTo(l2.toString());
    }
  };
  private final Project myProject;
  private final PsiDirectory myDirectory;
  private JPanel myPanel;
  private JTextField myResourceBundleBaseNameTextField;
  private JButton myAddLocaleFromExistButton;
  private JPanel myNewBundleLocalesPanel;
  private JPanel myProjectExistLocalesPanel;
  private JButton myAddAllButton;
  private MyLocalesToAddModel myLocalesModel;

  public CreateResourceBundleDialogComponent(Project project, PsiDirectory directory) {
    myProject = project;
    myDirectory = directory;
  }

  public static class Dialog extends DialogWrapper {
    @Nullable private final Project myProject;
    private final PsiDirectory myDirectory;
    private CreateResourceBundleDialogComponent myComponent;
    private PsiElement[] myCreatedFiles;

    protected Dialog(@Nullable Project project, PsiDirectory directory) {
      super(project);
      myProject = project;
      myDirectory = directory;
      myComponent = new CreateResourceBundleDialogComponent(myProject, myDirectory);
      init();
      initValidation();
    }

    @Override
    protected void doOKAction() {
      final String errorString = myComponent.canCreateAllFilesForAllLocales();
      if (errorString != null) {
        Messages.showErrorDialog(getContentPanel(), errorString);
      } else {
        final List<PsiFile> createFiles = myComponent.createPropertiesFiles();
        myCreatedFiles = createFiles.toArray(new PsiElement[createFiles.size()]);
        super.doOKAction();
      }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myComponent.getPanel();
    }

    public PsiElement[] getCreatedFiles() {
      return myCreatedFiles;
    }
  }

  private List<PsiFile> createPropertiesFiles() {
    final String name = getBaseName();
    final Set<String> fileNames = ContainerUtil.map2Set(myLocalesModel.getLocales(), new Function<Locale, String>() {
      @Override
      public String fun(Locale locale) {
        return locale == PropertiesUtil.DEFAULT_LOCALE ? (name + ".properties") : (name + "_" + locale.toString() + ".properties");
      }
    });
    return ApplicationManager.getApplication().runWriteAction(new Computable<List<PsiFile>>() {
      @Override
      public List<PsiFile> compute() {
        return ContainerUtil.map(fileNames, new Function<String, PsiFile>() {
          @Override
          public PsiFile fun(String n) {
            return myDirectory.createFile(n);
          }
        });
      }
    });
  }

  private String getBaseName() {
    return myResourceBundleBaseNameTextField.getText();
  }

  private String canCreateAllFilesForAllLocales() {
    final String name = getBaseName();
    if (name.isEmpty()) {
      return "Base name is empty";
    }
    final Set<String> suffixes = ContainerUtil.map2Set(myLocalesModel.getLocales(), new Function<Locale, String>() {
      @Override
      public String fun(Locale locale) {
        return locale.toString() + ".properties";
      }
    });
    if (suffixes.isEmpty()) {
      return "No locales added";
    }
    for (PsiElement element : myDirectory.getChildren()) {
      if (element instanceof PsiFile) {
        if (element instanceof PropertiesFile) {
          PropertiesFile propertiesFile = (PropertiesFile) element;
          final String propertiesFileName = propertiesFile.getName();
          if (propertiesFileName.startsWith(name)) {
            final String fileNameSuffix = propertiesFileName.substring(name.length());
            if (suffixes.contains(fileNameSuffix)) {
              return "Some of files already exist";
            }
          }
        }
      }
    }
    return null;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @Nullable
  private static List<Locale> extractLocalesFromString(final String rawLocales) {
    if (rawLocales.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] splitRawLocales = rawLocales.split(",");
    final List<Locale> locales = new ArrayList<Locale>(splitRawLocales.length);

    for (String rawLocale : splitRawLocales) {
      final Locale locale = PropertiesUtil.getLocale("_" + rawLocale + ".properties");
      if (locale == PropertiesUtil.DEFAULT_LOCALE) {
        return null;
      }
      else if (!locales.contains(locale)) {
        locales.add(locale);
      }
    }
    return locales;
  }

  @SuppressWarnings("unchecked")
  private void createUIComponents() {
    final JBList projectExistLocalesList = new JBList();
    final MyExistLocalesListModel existLocalesListModel = new MyExistLocalesListModel();
    projectExistLocalesList.setModel(existLocalesListModel);
    projectExistLocalesList.setCellRenderer(getLocaleRenderer());
    myProjectExistLocalesPanel = ToolbarDecorator.createDecorator(projectExistLocalesList)
      .disableRemoveAction()
      .disableUpDownActions()
      .createPanel();
    myProjectExistLocalesPanel.setBorder(IdeBorderFactory.createTitledBorder("Project locales", false));

    final JBList localesToAddList = new JBList();
    myLocalesModel = new MyLocalesToAddModel();
    localesToAddList.setModel(myLocalesModel);
    localesToAddList.setCellRenderer(getLocaleRenderer());
    myNewBundleLocalesPanel = ToolbarDecorator.createDecorator(localesToAddList).setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final String rawAddedLocales = Messages.showInputDialog(myProject,
                                                  PropertiesBundle.message("create.resource.bundle.dialog.add.locales.validator.message"),
                                                  PropertiesBundle.message("create.resource.bundle.dialog.add.locales.validator.title"),
                                                  null, null, new InputValidatorEx() {
            @Nullable
            @Override
            public String getErrorText(String inputString) {
              return checkInput(inputString) ? null : "Invalid locales";
            }

            @Override
            public boolean checkInput(String inputString) {
              return extractLocalesFromString(inputString) != null;
            }

            @Override
            public boolean canClose(String inputString) {
              return checkInput(inputString);
            }
          });
        if (rawAddedLocales != null) {
          final List<Locale> locales = extractLocalesFromString(rawAddedLocales);
          LOG.assertTrue(locales != null);
          myLocalesModel.addRows(locales);
        }
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        myLocalesModel.removeRow(localesToAddList.getSelectedIndices());
      }
    }).disableUpDownActions().createPanel();
    myNewBundleLocalesPanel.setBorder(IdeBorderFactory.createTitledBorder("Locales to add", false));

    myAddLocaleFromExistButton = new JButton(AllIcons.Actions.Forward);
    new ClickListener(){
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (clickCount == 1) {
          myLocalesModel.addRows(ContainerUtil.map(projectExistLocalesList.getSelectedValues(), new Function<Object, Locale>() {
            @Override
            public Locale fun(Object o) {
              return (Locale)o;
            }
          }));
          return true;
        }
        return false;
      }
    }.installOn(myAddLocaleFromExistButton);

    myAddAllButton = new JButton("Add All");
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (clickCount == 1) {
          myLocalesModel.addRows(existLocalesListModel.getLocales());
        }
        return false;
      }
    }.installOn(myAddAllButton);
  }

  @NotNull
  private static ColoredListCellRenderer<Locale> getLocaleRenderer() {
    return new ColoredListCellRenderer<Locale>() {
      @Override
      protected void customizeCellRenderer(JList list, Locale locale, int index, boolean selected, boolean hasFocus) {
        if (PropertiesUtil.DEFAULT_LOCALE == locale) {
          append("Default locale");
        } else {
          append(locale.toString());
          append(PropertiesUtil.getPresentableLocale(locale), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    };
  }

  private static class MyLocalesToAddModel extends AbstractListModel {
    private final List<Locale> myLocales;

    private MyLocalesToAddModel() {
      myLocales = new ArrayList<Locale>();
      myLocales.add(PropertiesUtil.DEFAULT_LOCALE);
    }

    public List<Locale> getLocales() {
      return myLocales;
    }

    @Override
    public int getSize() {
      return myLocales.size();
    }

    @Override
    public Locale getElementAt(int index) {
      return myLocales.get(index);
    }

    public void addRows(final List<Locale> toAdd) {
      boolean added = false;
      for (Locale locale : toAdd) {
        if (!myLocales.contains(locale)) {
          myLocales.add(locale);
          if (!added) {
            added = true;
          }
        }
      }
      if (added) {
        Collections.sort(myLocales, LOCALE_COMPARATOR);
        fireIntervalAdded(this, 0, myLocales.size());
      }
    }

    public void removeRow(int[] indices) {
      indices = Arrays.copyOf(indices, indices.length);
      Arrays.sort(indices);
      for (int i = indices.length - 1; i >= 0 ; i--) {
        myLocales.remove(indices[i]);
      }
      fireIntervalRemoved(this, indices[0], indices[indices.length - 1]);
    }
  }

  private class MyExistLocalesListModel extends AbstractListModel {
    private final List<Locale> myLocales;

    private MyExistLocalesListModel() {
      myLocales = new ArrayList<Locale>();
      myLocales.add(PropertiesUtil.DEFAULT_LOCALE);

      PropertiesReferenceManager.getInstance(myProject).processPropertiesFiles(GlobalSearchScopesCore.projectProductionScope(myProject), new PropertiesFileProcessor() {
        @Override
        public boolean process(String baseName, PropertiesFile propertiesFile) {
          final Locale locale = propertiesFile.getLocale();
          if (locale != PropertiesUtil.DEFAULT_LOCALE && !myLocales.contains(locale)) {
            myLocales.add(locale);
          }
          return true;
        }
      }, BundleNameEvaluator.DEFAULT);
      Collections.sort(myLocales, LOCALE_COMPARATOR);
    }

    @Override
    public int getSize() {
      return myLocales.size();
    }

    @Override
    public Locale getElementAt(int index) {
      return myLocales.get(index);
    }

    public List<Locale> getLocales() {
      return myLocales;
    }
  }
}
