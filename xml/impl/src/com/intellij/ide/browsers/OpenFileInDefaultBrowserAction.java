/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.Collection;

public class OpenFileInDefaultBrowserAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(OpenFileInDefaultBrowserAction.class);

  @Nullable
  public static Pair<OpenInBrowserRequest, WebBrowserUrlProvider> doUpdate(AnActionEvent event) {
    OpenInBrowserRequest request = createRequest(event.getDataContext());
    boolean applicable = false;
    WebBrowserUrlProvider provider = null;
    if (request != null) {
      applicable = HtmlUtil.isHtmlFile(request.getFile()) && !(request.getVirtualFile() instanceof LightVirtualFile);
      if (!applicable) {
        provider = WebBrowserServiceImpl.getProvider(request);
        applicable = provider != null;
      }
    }

    Presentation presentation = event.getPresentation();
    presentation.setVisible(applicable);
    presentation.setVisible(applicable);
    return applicable ? Pair.create(request, provider) : null;
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Pair<OpenInBrowserRequest, WebBrowserUrlProvider> result = doUpdate(e);
    if (result == null) {
      return;
    }

    WebBrowserUrlProvider browserUrlProvider = result.second;
    String text = getTemplatePresentation().getText();
    String description = getTemplatePresentation().getDescription();
    if (browserUrlProvider != null) {
      final String customText = browserUrlProvider.getOpenInBrowserActionText(result.first.getFile());
      if (customText != null) {
        text = customText;
      }
      final String customDescription = browserUrlProvider.getOpenInBrowserActionDescription(result.first.getFile());
      if (customDescription != null) {
        description = customDescription;
      }
      if (HtmlUtil.isHtmlFile(result.first.getFile())) {
        description += " (hold Shift to open URL of local file)";
      }
    }

    presentation.setText(text);
    presentation.setDescription(description);

    GeneralSettings settings = GeneralSettings.getInstance();
    if (!settings.isUseDefaultBrowser()) {
      BrowsersConfiguration.BrowserFamily family = BrowsersConfiguration.getInstance().findFamilyByPath(settings.getBrowserPath());
      if (family != null) {
        presentation.setIcon(family.getIcon());
      }
    }

    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(presentation.isEnabled());
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    open(e, null);
  }

  @Nullable
  public static OpenInBrowserRequest createRequest(@NotNull DataContext context) {
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    if (editor != null) {
      Project project = editor.getProject();
      if (project != null && project.isInitialized()) {
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(context);
        if (psiFile == null) {
          psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        }
        if (psiFile != null) {
          return new OpenInBrowserRequest(psiFile) {
            private PsiElement element;

            @NotNull
            @Override
            public PsiElement getElement() {
              if (element == null) {
                element = getFile().findElementAt(editor.getCaretModel().getOffset());
              }
              return ObjectUtils.chooseNotNull(element, getFile());
            }
          };
        }
      }
    }
    else {
      final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(context);
      if (psiFile != null) {
        return OpenInBrowserRequest.create(psiFile);
      }

      final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
      final Project project = CommonDataKeys.PROJECT.getData(context);
      if (virtualFile != null && !virtualFile.isDirectory() && virtualFile.isValid() && project != null && project.isInitialized()) {
        return new OpenInBrowserRequest() {
          @NotNull
          @Override
          public VirtualFile getVirtualFile() {
            return virtualFile;
          }

          @NotNull
          @Override
          public Project getProject() {
            return project;
          }

          @NotNull
          @Override
          public PsiElement getElement() {
            return getFile();
          }

          @NotNull
          @Override
          public PsiFile getFile() {
            if (file == null) {
              file = PsiManager.getInstance(getProject()).findFile(virtualFile);
              LOG.assertTrue(file != null);
            }
            return file;
          }
        };
      }
    }
    return null;
  }

  public static void open(@NotNull AnActionEvent event, @Nullable WebBrowser browser) {
    open(createRequest(event.getDataContext()), (event.getModifiers() & InputEvent.SHIFT_MASK) != 0, browser);
  }

  public static void open(@Nullable OpenInBrowserRequest request, boolean preferLocalUrl, @Nullable final WebBrowser browser) {
    if (request == null) {
      return;
    }

    try {
      Collection<Url> urls = WebBrowserService.getInstance().getUrlsToOpen(request, preferLocalUrl);
      if (!urls.isEmpty()) {
        chooseUrl(urls).doWhenDone(new Consumer<Url>() {
          @Override
          public void consume(Url url) {
            ApplicationManager.getApplication().saveAll();
            UrlOpener.launchBrowser(url.toExternalForm(), browser);
          }
        });
      }
    }
    catch (WebBrowserUrlProvider.BrowserException e1) {
      Messages.showErrorDialog(e1.getMessage(), XmlBundle.message("browser.error"));
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  @NotNull
  private static AsyncResult<Url> chooseUrl(@NotNull Collection<Url> urls) {
    if (urls.size() == 1) {
      return new AsyncResult.Done<Url>(ContainerUtil.getFirstItem(urls));
    }

    final JBList list = new JBList(urls);
    list.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        // todo icons looks good, but is it really suitable for all URLs providers?
        setIcon(AllIcons.Nodes.Servlet);
        append(((Url)value).toDecodedForm());
      }
    });

    final AsyncResult<Url> result = new AsyncResult<Url>();
    JBPopupFactory.getInstance().
      createListPopupBuilder(list).
      setTitle("Choose Url").
      setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          Url value = (Url)list.getSelectedValue();
          if (value != null) {
            result.setDone(value);
          }
          else {
            result.setRejected();
          }
        }
      }).
      createPopup().showInFocusCenter();
    return result;
  }
}
