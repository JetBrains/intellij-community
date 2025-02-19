// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service;

import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.project.ProjectId;
import com.intellij.platform.project.ProjectIdKt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo;
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import org.intellij.plugins.markdown.mapper.MarkdownHeaderMapper;
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

// md TODO: organize the code better
@Service(Service.Level.APP)
public final class MarkdownFrontendServiceImpl implements MarkdownFrontendService {
  private static final Logger logger = Logger.getInstance(MarkdownFrontendServiceImpl.class);

  @Override
  public void openFile(ProjectId projectId, @NotNull URI uri) {
    Project project = ProjectIdKt.findProject(projectId);
    OpenFileAction.openFile(uri.getPath(), project);
  }

  @Override
  public Collection<MarkdownHeaderInfo> collectHeaders(ProjectId projectId, @NotNull URI uri) {
    Project project = ProjectIdKt.findProject(projectId);
    VirtualFile targetFile = findVirtualFile(uri);
    String anchor = uri.getFragment();

    return ReadAction.compute(() -> {
      if (DumbService.isDumb(project)) return null;
      PsiFile file = PsiManager.getInstance(project).findFile(targetFile);
      GlobalSearchScope scope = (file == null) ? GlobalSearchScope.EMPTY_SCOPE : GlobalSearchScope.fileScope(file);;
      Collection<MarkdownHeader> headers = HeaderAnchorIndex.Companion.collectHeaders(project, scope, anchor);
      return ContainerUtil.map(headers, MarkdownHeaderMapper::map);
    });
  }

  @Override
  public Project guessProjectForUri(URI uri) {
    VirtualFile file = findVirtualFile(uri);
    return ProjectUtil.guessProjectForFile(file);
  }

  @Override
  public void navigateToHeader(ProjectId projectId, MarkdownHeaderInfo headerInfo) {
    URI uri = createFileUri(headerInfo.getFilePath());
    if (uri == null) return;
    VirtualFile file = findVirtualFile(uri);
    Project project = ProjectIdKt.findProject(projectId);
    FileEditorManager manager = FileEditorManager.getInstance(ProjectIdKt.findProject(projectId));
    List<MarkdownEditorWithPreview> openedEditors = manager.getEditorList(file).stream()
      .filter(editor -> editor instanceof MarkdownEditorWithPreview)
      .map(editor -> (MarkdownEditorWithPreview)editor)
      .toList();
    PsiElement element = PsiUtilCore.getPsiFile(project,file).findElementAt(headerInfo.getTextOffset());
    if (element == null) return;
    if (!openedEditors.isEmpty()) {
      for (MarkdownEditorWithPreview editor : openedEditors) {
        PsiUtilCore.getElementAtOffset(PsiUtilCore.getPsiFile(project, file), element.getTextOffset());
        PsiNavigateUtil.navigate(element, true);
      }
      return;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, element.getTextOffset());
    manager.openEditor(descriptor, true);
  }

  private static VirtualFile findVirtualFile(URI uri){
    String uriPath = uri.getPath();
    if (SystemInfo.isWindows) uriPath = UriUtil.trimLeadingSlashes(uriPath);
    Path path = Path.of(uriPath);
    return VfsUtil.findFile(path, true);
  }

  // md TODO: might be an extension function
  private static URI createFileUri(String link) {
    try {
      return new URI("file", null, link, null);
    }
    catch (URISyntaxException exception) {
      logger.warn(exception);
      return null;
    }
  }
}
