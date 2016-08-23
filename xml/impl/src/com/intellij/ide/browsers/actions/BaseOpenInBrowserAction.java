/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.browsers.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.*;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.util.BitUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.Collection;

public abstract class BaseOpenInBrowserAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(BaseOpenInBrowserAction.class);

  protected BaseOpenInBrowserAction(@NotNull WebBrowser browser) {
    super(browser.getName(), null, browser.getIcon());
  }

  @SuppressWarnings("UnusedDeclaration")
  protected BaseOpenInBrowserAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Nullable
  protected abstract WebBrowser getBrowser(@NotNull AnActionEvent event);

  @Override
  public final void update(AnActionEvent e) {
    WebBrowser browser = getBrowser(e);
    if (browser == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    OpenInBrowserRequest result = doUpdate(e);
    if (result == null) {
      return;
    }

    String description = getTemplatePresentation().getText();
    if (ActionPlaces.CONTEXT_TOOLBAR.equals(e.getPlace())) {
      StringBuilder builder = new StringBuilder(description);
      builder.append(" (");
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("WebOpenInAction");
      boolean exists = shortcuts.length > 0;
      if (exists) {
        builder.append(KeymapUtil.getShortcutText(shortcuts[0]));
      }

      if (HtmlUtil.isHtmlFile(result.getFile())) {
        builder.append(exists ? ", " : "").append("hold Shift to open URL of local file");
      }
      builder.append(')');
      description = builder.toString();
    }
    e.getPresentation().setText(description);
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    WebBrowser browser = getBrowser(e);
    if (browser != null) {
      UsageTrigger.trigger("OpenInBrowser." + browser.getName());
      open(e, browser);
    }
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
        if (psiFile != null && !(psiFile.getVirtualFile() instanceof ContentRevisionVirtualFile)) {
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
      PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(context);
      VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
      Project project = CommonDataKeys.PROJECT.getData(context);
      if (virtualFile != null && !virtualFile.isDirectory() && virtualFile.isValid() && project != null && project.isInitialized()) {
        psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      }

      if (psiFile != null && !(psiFile.getVirtualFile() instanceof ContentRevisionVirtualFile)) {
        return OpenInBrowserRequest.create(psiFile);
      }
    }
    return null;
  }

  @Nullable
  public static OpenInBrowserRequest doUpdate(@NotNull AnActionEvent event) {
    OpenInBrowserRequest request = createRequest(event.getDataContext());
    boolean applicable = request != null && WebBrowserServiceImpl.getProvider(request) != null;
    event.getPresentation().setEnabledAndVisible(applicable);
    return applicable ? request : null;
  }

  public static void open(@NotNull AnActionEvent event, @Nullable WebBrowser browser) {
    open(createRequest(event.getDataContext()), BitUtil.isSet(event.getModifiers(), InputEvent.SHIFT_MASK), browser);
  }

  public static void open(@Nullable final OpenInBrowserRequest request, boolean preferLocalUrl, @Nullable final WebBrowser browser) {
    if (request == null) {
      return;
    }

    try {
      Collection<Url> urls = WebBrowserService.getInstance().getUrlsToOpen(request, preferLocalUrl);
      if (!urls.isEmpty()) {
        chooseUrl(urls)
          .done(url -> {
            ApplicationManager.getApplication().saveAll();
            BrowserLauncher.getInstance().browse(url.toExternalForm(), browser, request.getProject());
          });
      }
    }
    catch (WebBrowserUrlProvider.BrowserException e1) {
      Messages.showErrorDialog(e1.getMessage(), IdeBundle.message("browser.error"));
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  @NotNull
  private static Promise<Url> chooseUrl(@NotNull Collection<Url> urls) {
    if (urls.size() == 1) {
      return Promise.resolve(ContainerUtil.getFirstItem(urls));
    }

    final JBList list = new JBList(urls);
    list.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        // todo icons looks good, but is it really suitable for all URLs providers?
        setIcon(AllIcons.Nodes.Servlet);
        append(((Url)value).toDecodedForm());
      }
    });

    final AsyncPromise<Url> result = new AsyncPromise<>();
    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Choose Url")
      .setItemChoosenCallback(() -> {
        Url value = (Url)list.getSelectedValue();
        if (value == null) {
          result.setError("selected value is null");
        }
        else {
          result.setResult(value);
        }
      })
      .createPopup()
      .showInFocusCenter();
    return result;
  }
}