package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.impl.source.jsp.JspImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.xml.util.documentation.XmlDocumentationProvider;
import com.intellij.xml.util.documentation.HtmlDocumentationProvider;
import com.intellij.xml.util.documentation.XHtmlDocumentationProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public class JavaDocManager implements ProjectComponent {
  private final Project myProject;
  private Editor myEditor = null;

  private WeakReference<LightweightHint> myDocInfoHintRef;
  private boolean myRequestFocus;
  private Component myPreviouslyFocused = null;
  private HashMap<FileType,DocumentationProvider> documentationProviders = new HashMap<FileType, DocumentationProvider>();
  public static final Key<PsiElement> ORIGINAL_ELEMENT_KEY = Key.create("Original element");

  public DocumentationProvider getProvider(FileType fileType) {
    return documentationProviders.get(fileType);
  }

  public interface DocumentationProvider {
    String getUrlFor(PsiElement element, PsiElement originalElement);

    String generateDoc(PsiElement element, PsiElement originalElement);

    PsiElement getDocumentationElementForLookupItem(Object object, PsiElement element);

    PsiElement getDocumentationElementForLink(String link, PsiElement context);
  }

  public void registerDocumentationProvider(FileType fileType,DocumentationProvider provider) {
    documentationProviders.put(fileType, provider);
  }

  public static JavaDocManager getInstance(Project project) {
    return project.getComponent(JavaDocManager.class);
  }

  public JavaDocManager(Project project) {
    myProject = project;

    registerDocumentationProvider(StdFileTypes.HTML, new HtmlDocumentationProvider(project));
    registerDocumentationProvider(StdFileTypes.XHTML, new XHtmlDocumentationProvider(project));
    final JspImplUtil.JspDocumentationProvider provider = new JspImplUtil.JspDocumentationProvider(project);
    registerDocumentationProvider(StdFileTypes.JSP,provider);
    registerDocumentationProvider(StdFileTypes.JSPX, provider);

    registerDocumentationProvider(StdFileTypes.XML, new XmlDocumentationProvider());
  }

  public String getComponentName() {
    return "JavaDocManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public LightweightHint showJavaDocInfo(PsiElement element) {
    myRequestFocus = false;

    final JavaDocInfoComponent component = new JavaDocInfoComponent(this);

    final LightweightHint hint = new LightweightHint(component) {
      public void hide() {
        super.hide();

        if (myPreviouslyFocused != null && myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider) {
          ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).unregisterHint();
        }

        myEditor = null;
        myPreviouslyFocused = null;
      }
    };

    LightweightHint oldHint = getDocInfoHint();

    if (oldHint != null) {
      JavaDocInfoComponent oldComponent = (JavaDocInfoComponent)oldHint.getComponent();
      PsiElement element1 = oldComponent.getElement();
      if (element != null && element.equals(element1)) {
        return oldHint;
      }
      oldHint.hide();
    }

    component.setHint(hint);

    fetchDocInfo(getDefaultProvider(element), component);

    myDocInfoHintRef = new WeakReference<LightweightHint>(hint);

    Window window = WindowManager.getInstance().suggestParentWindow(myProject);
    JLayeredPane layeredPane;

    if (window instanceof JFrame) {
      layeredPane = ((JFrame)window).getLayeredPane();
    }
    else if (window instanceof JDialog) {
      layeredPane = ((JDialog)window).getLayeredPane();
    }
    else {
      throw new IllegalStateException("cannot find parent window: project=" + myProject + "; window=" + window);
    }

    myPreviouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);

    if (myPreviouslyFocused == null || !(myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider)) {
      myRequestFocus = true;
    }

    hookFocus(hint);

    Point p = chooseBestHintPosition(hint);
    Dimension preferredTextFieldPanelSize = component.getPreferredSize();

    component.setBounds(p.x, p.y, preferredTextFieldPanelSize.width, preferredTextFieldPanelSize.height);

    final JLayeredPane _layeredPane = layeredPane;

    _layeredPane.add(component, new Integer(500));

    component.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        _layeredPane.remove(component);
        _layeredPane.repaint(component.getBounds());
      }
    },
                                     KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                     JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    return hint;
  }

  public LightweightHint showJavaDocInfo(final Editor editor, PsiFile file, boolean requestFocus) {
    myEditor = editor;
    myRequestFocus = requestFocus;

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = TargetElementUtil.findTargetElement(editor,
                                                             TargetElementUtil.ELEMENT_NAME_ACCEPTED
                                                             | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                                                             | TargetElementUtil.LOOKUP_ITEM_ACCEPTED
                                                             | TargetElementUtil.NEW_AS_CONSTRUCTOR
                                                             | TargetElementUtil.THIS_ACCEPTED
                                                             | TargetElementUtil.SUPER_ACCEPTED);
    PsiElement originalElement = (file != null)?file.findElementAt(editor.getCaretModel().getOffset()): null;

    if (element == null && editor != null) {
      final PsiReference ref = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());

      if (ref != null) {
        final PsiElement parent = ref.getElement().getParent();

        if (parent instanceof PsiMethodCallExpression) {
          element = parent;
        }
      }

      Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();

      if (activeLookup != null) {
        LookupItem item = activeLookup.getCurrentItem();
        if (item == null) return null;

        if (file!=null) {
          DocumentationProvider documentationProvider = documentationProviders.get(file.getFileType());
          if (documentationProvider!=null) {

            if (ref!=null) originalElement = ref.getElement();
            element = documentationProvider.getDocumentationElementForLookupItem(item.getObject(), originalElement);
          }
        }
      }
    }

    if (element instanceof PsiAnonymousClass) {
      element = ((PsiAnonymousClass)element).getBaseClassType().resolve();
    }

    if (element == null && file != null) { // look if we are within a javadoc comment
      element = originalElement;
      if (element == null) return null;
      PsiDocComment comment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
      if (comment == null) return null;
      element = comment.getParent();
      if (!(element instanceof PsiDocCommentOwner)) return null;
    }

    LightweightHint oldHint = getDocInfoHint();
    if (oldHint != null) {
      JavaDocInfoComponent component = (JavaDocInfoComponent)oldHint.getComponent();
      PsiElement element1 = component.getElement();
      if (element != null && element.equals(element1)) {
        if (requestFocus) {
          component.getComponent().requestFocus();
        }
        return oldHint;
      }
      oldHint.hide();
    }

    JavaDocInfoComponent component = new JavaDocInfoComponent(this);

    final IdeFrame frame = WindowManagerEx.getInstanceEx().getFrame(myProject);
    try {
      element.putUserData(ORIGINAL_ELEMENT_KEY,originalElement);
    } catch(RuntimeException ex) {} // PsiPackage does not allow putUserData

    LightweightHint hint = new LightweightHint(component) {
      public void hide() {
        frame.setDefaultFocusableComponent(editor.getContentComponent());
        try {
          super.hide();
        }
        finally {
          frame.setDefaultFocusableComponent(null);
        }
        editor.getContentComponent().requestFocusInWindow();

        if (myPreviouslyFocused != null && myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider) {
          ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).unregisterHint();
        }

        myEditor = null;
        myPreviouslyFocused = null;
      }
    };
    component.setHint(hint);

    fetchDocInfo(getDefaultProvider(element), component);

    if (LookupManager.getInstance(myProject).getActiveLookup() != null) {
      myRequestFocus = false; // move focus on the second try from lookups
    }

    myDocInfoHintRef = new WeakReference<LightweightHint>(hint);

    return hint;
  }

  void hookFocus(LightweightHint hint) {
    if (myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider) {
      ((ChooseByNameBase.JPanelProvider)myPreviouslyFocused.getParent()).registerHint(hint);
    }
  }

  void takeFocus(LightweightHint hint) {
    if (myRequestFocus) {
      hint.getComponent().requestFocusInWindow();
    }
  }

  Point chooseBestHintPosition(LightweightHint hint) {
    if (myEditor != null) {
      takeFocus(hint);

      Point result;
      HintManager hintManager = HintManager.getInstance();
      Dimension hintSize = hint.getComponent().getPreferredSize();
      JComponent editorComponent = myEditor.getComponent();
      JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

      Point p3 = hintManager.getHintPosition(hint, myEditor, HintManager.UNDER);
      Point p4 = hintManager.getHintPosition(hint, myEditor, HintManager.ABOVE);
      p3.x = Math.max(p3.x, 0);
      p3.x = Math.min(p3.x, layeredPane.getWidth() - hintSize.width);
      p4.x = Math.max(p4.x, 0);
      p4.x = Math.min(p4.x, layeredPane.getWidth() - hintSize.width);

      int underSpace = layeredPane.getHeight() - p3.y;
      int aboveSpace = p4.y + hintSize.height;
      p4.y = Math.max(0, p4.y);
      if (aboveSpace > underSpace) {
        result = p4;
      }
      else {
        result = p3;
      }
      return result;
    }

    if (myPreviouslyFocused != null) {
      Window window = WindowManager.getInstance().suggestParentWindow(myProject);
      JLayeredPane layeredPane;

      if (window instanceof JFrame) {
        layeredPane = ((JFrame)window).getLayeredPane();
      }
      else if (window instanceof JDialog) {
        layeredPane = ((JDialog)window).getLayeredPane();
      }
      else {
        throw new IllegalStateException("cannot find parent window: project=" + myProject + "; window=" + window);
      }

      //myPreviouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);

      Dimension preferredTextFieldPanelSize = hint.getComponent().getPreferredSize();
      int x = (layeredPane.getWidth() - preferredTextFieldPanelSize.width) / 2;
      int y = (layeredPane.getHeight() - preferredTextFieldPanelSize.height) / 2;

      if (ChooseByNameBase.isMyComponent(myPreviouslyFocused)) {
        y = myPreviouslyFocused.getParent().getY() - preferredTextFieldPanelSize.height;
      }

      return new Point(x, y);
    }

    return null;
  }

  public JavaDocProvider getDefaultProvider(final PsiElement element) {
    return new JavaDocProvider() {
      public String getJavaDoc() {
        return getDocInfo(element);
      }

      public PsiElement getElement() {
        return element;
      }
    };
  }

  private String getExternalJavaDocUrl(final PsiElement element) {
    String url = null;

    if (element instanceof PsiClass) {
      url = findUrlForClass((PsiClass)element);
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        url = findUrlForClass(aClass);
        if (url != null) {
          url += "#" + field.getName();
        }
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        url = findUrlForClass(aClass);
        if (url != null) {
          String signature = PsiFormatUtil.formatMethod(method,
                                                        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
                                                                              PsiFormatUtil.SHOW_PARAMETERS,
                                                        PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES);
          url += "#" + signature;
        }
      }
    }
    else if (element instanceof PsiPackage) {
      url = findUrlForPackage((PsiPackage)element);
    }
    else if (element instanceof PsiDirectory) {
      PsiPackage aPackage = ((PsiDirectory)element).getPackage();
      if (aPackage != null) {
        url = findUrlForPackage(aPackage);
      }
    } else {
      DocumentationProvider provider = getProviderFromElement(element);
      if (provider!=null) url = provider.getUrlFor(element,element.getUserData(ORIGINAL_ELEMENT_KEY));
    }

    return url == null ? null : url.replace('\\', '/');
  }

  public void openJavaDoc(final PsiElement element) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.javadoc.external");
    String url = getExternalJavaDocUrl(element);
    if (url != null) {
      BrowserUtil.launchBrowser(url);
    }
    else {
      Messages.showMessageDialog(myProject,
                                 "The documentation for this element is not found.\nPlease add all the needed paths to API docs in Project Settings.",
                                 "No Documentation",
                                 Messages.getErrorIcon());
    }
  }

  public LightweightHint getDocInfoHint() {
    if (myDocInfoHintRef == null) return null;
    LightweightHint hint = myDocInfoHintRef.get();
    if (hint == null || !hint.isVisible()) {
      myDocInfoHintRef = null;
      return null;
    }
    return hint;
  }

  private String findUrlForClass(PsiClass aClass) {
    String qName = aClass.getQualifiedName();
    if (qName == null) return null;
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    String packageName = ((PsiJavaFile)file).getPackageName();

    String relPath;
    if (packageName.length() > 0) {
      relPath = packageName.replace('.', '/') + '/' + qName.substring(packageName.length() + 1) + ".html";
    }
    else {
      relPath = qName + ".html";
    }

    final PsiFile containingFile = aClass.getContainingFile();
    if (containingFile == null) return null;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    Module module = fileIndex.getModuleForFile(virtualFile);

    if (module != null) {
      VirtualFile[] javadocPaths = ModuleRootManager.getInstance(module).getJavadocPaths();
      String httpRoot = getHttpRoot(javadocPaths, relPath);
      if (httpRoot != null) return httpRoot;
    }

    final OrderEntry[] orderEntries = fileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry orderEntry : orderEntries) {
      final VirtualFile[] files = orderEntry.getFiles(OrderRootType.JAVADOC);
      final String httpRoot = getHttpRoot(files, relPath);
      if (httpRoot != null) return httpRoot;
    }
    return null;
  }

  private static String getHttpRoot(final VirtualFile[] roots, String relPath) {
    for (VirtualFile root : roots) {
      if (root.getFileSystem() instanceof HttpFileSystem) {
        return root.getUrl() + relPath;
      }
      else {
        VirtualFile file = root.findFileByRelativePath(relPath);
        if (file != null) return file.getUrl();
      }
    }

    return null;
  }

  private String findUrlForPackage(PsiPackage aPackage) {
    String qName = aPackage.getQualifiedName();
    qName = qName.replace('.', File.separatorChar);
    String[] docPaths = JavaDocUtil.getDocPaths(myProject);
    for (String docPath : docPaths) {
      String url = docPath + File.separator + qName + File.separatorChar + "package-summary.html";
      File file = new File(url);
      if (file.exists()) return /*"file:///" + */url;
    }
    return null;
  }

  private String findUrlForLink(PsiPackage basePackage, String link) {
    int index = link.indexOf('#');
    String tail = "";
    if (index >= 0) {
      tail = link.substring(index);
      link = link.substring(0, index);
    }

    String qName = basePackage.getQualifiedName();
    qName = qName.replace('.', File.separatorChar);
    String[] docPaths = JavaDocUtil.getDocPaths(myProject);
    for (String docPath : docPaths) {
      String url = docPath + File.separator + qName + File.separatorChar + link;
      File file = new File(url);
      if (file.exists()) return url + tail;
    }
    return null;
  }

  public void fetchDocInfo(final JavaDocProvider provider, final JavaDocInfoComponent component) {
    component.startWait();

    new Alarm().addRequest(new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (component.isEmpty()) {
              component.setText("Fetching JavaDocs....");
            }
          }
        });
      }
    },
                           600);

    new Thread(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final String text = provider.getJavaDoc();
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                if (text == null) {
                  component.setText("No documentation found.", true);
                }
                else if (text.length() == 0) {
                  component.setText(component.getText(), true);
                }
                else {
                  component.setData(provider.getElement(), text);
                }
              }
            });
          }
        });
      }
    }).start();
  }

  public String getDocInfo(PsiElement element) {
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo(((PsiMethodCallExpression)element));
    }
    else {
      String externalDoc;
      JavaDocExternalFilter docFilter = new JavaDocExternalFilter(myProject);
      String docURL = getExternalJavaDocUrl(element);

      return
        (
          element instanceof PsiCompiledElement &&
          (externalDoc = docFilter.getExternalDocInfo(docURL)) != null
        )
        ? externalDoc
        : docFilter.filterInternalDocInfo(
            new JavaDocInfoGenerator(myProject, element, getProviderFromElement(element)).generateDocInfo(),
            docURL
        );
    }
  }

  public DocumentationProvider getProviderFromElement(final PsiElement element) {
    PsiElement originalElement = element!=null ? element.getUserData(ORIGINAL_ELEMENT_KEY):null;
    PsiFile containingFile = (originalElement!=null)?originalElement.getContainingFile() : (element!=null)?element.getContainingFile():null;
    VirtualFile vfile = (containingFile!=null)?containingFile.getVirtualFile() : null;

    return (vfile!=null)?getProvider(vfile.getFileType()):null;
  }

  private String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = expr.getManager().getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);

    final String text = expr.getText();
    if (candidates.length > 0) {
      final StringBuffer sb = new StringBuffer();

      for (final CandidateInfo candidate : candidates) {
        final PsiElement element = candidate.getElement();

        if (!(element instanceof PsiMethod)) {
          continue;
        }

        sb.append("&nbsp;&nbsp;<a href=\"psi_element://" + JavaDocUtil.getReferenceText(myProject, element) + "\">");
        sb.append(PsiFormatUtil.formatMethod(((PsiMethod)element),
                                             candidate.getSubstitutor(),
                                             PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE |
                                             PsiFormatUtil.SHOW_PARAMETERS,
                                             PsiFormatUtil.SHOW_TYPE));
        sb.append("</a><br>");
      }

      return "<html>Candidates for method call <b>" + text + "</b> are:<br><br>" + sb + "</html>";
    }

    return "<html>No candidates found for method call <b>" + text + "</b>.</html>";
  }

  void navigateByLink(final JavaDocInfoComponent component, String url) {
    component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    final PsiManager manager = PsiManager.getInstance(myProject);
    String prefix = "psi_element://";
    if (url.startsWith(prefix)) {
      final String refText = url.substring(prefix.length());
      final PsiElement targetElement = JavaDocUtil.findReferenceTarget(manager, refText, component.getElement());
      if (targetElement != null) {
        fetchDocInfo(getDefaultProvider(targetElement), component);
      }
    }
    else {
      final String docUrl = url;

      fetchDocInfo
        (new JavaDocProvider() {
          String getElementLocator(String url) {
            String prefix = "doc_element://";
            if (url.startsWith(prefix)) {
              return url.substring(prefix.length());
            }
            return null;
          }

          public String getJavaDoc() {
            String url = getElementLocator(docUrl);
            if (url != null && JavaDocExternalFilter.isJavaDocURL(url)) {
              String text = new JavaDocExternalFilter(myProject).getExternalDocInfo(url);

              if (text != null) {
                return text;
              }
            }

            if (url == null) {
              url = docUrl;
            }

            PsiElement element = component.getElement();
            if (element != null) {
              PsiElement parent = element;
              while (true) {
                if (parent == null || parent instanceof PsiDirectory) break;
                parent = parent.getParent();
              }
              if (parent != null) {
                PsiPackage aPackage = ((PsiDirectory)parent).getPackage();
                if (aPackage != null) {
                  String url1 = findUrlForLink(aPackage, url);
                  if (url1 != null) {
                    url = url1;
                  }
                }
              }
            }

            BrowserUtil.launchBrowser(url);

            return "";
          }

          public PsiElement getElement() {
            //String loc = getElementLocator(docUrl);
            //
            //if (loc != null) {
            //  PsiElement context = component.getElement();
            //  return JavaDocUtil.findReferenceTarget(context.getManager(), loc, context);
            //}

            return component.getElement();
          }
        }, component);
    }

    component.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  void showHint(LightweightHint hint) {
    Point p = chooseBestHintPosition(hint);

    if (myEditor != null) {
      HintManager hintManager = HintManager.getInstance();
      hintManager.showEditorHint(hint, myEditor, p,
                                 HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE |
                                 HintManager.HIDE_BY_TEXT_CHANGE |
                                 HintManager.HIDE_BY_SCROLLING,
                                 0, false);

      if (LookupManager.getInstance(myProject).getActiveLookup() != null) {
        myRequestFocus = false; // move focus on the second try from lookups
      }
      if (myRequestFocus) {
        hint.getComponent().requestFocusInWindow();
      }
    }
    else if (myPreviouslyFocused != null) {
      Dimension preferred = hint.getComponent().getPreferredSize();
      hint.setBounds(p.x, p.y, preferred.width, preferred.height);
    }
  }

  public void requestFocus() {
    if (myPreviouslyFocused != null && myPreviouslyFocused.getParent() instanceof ChooseByNameBase.JPanelProvider) {
      myPreviouslyFocused.getParent().requestFocus();
    }
  }

  public Project getProject() {
    return myProject;
  }
}