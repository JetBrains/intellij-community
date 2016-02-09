/**
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import org.intellij.plugins.xpathView.util.HighlighterUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.util.List;

/**
 * Application component.<br>
 * This component holds the application-level configuration and registers an own handler for
 * ESC-Action to clear highlighters.<br>
 * <p/>
 * Also used to manage highlighters.
 */
@State(
  name = "XPathView.XPathViewPlugin",
  storages = {
    @Storage("xpath.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class XPathAppComponent implements ApplicationComponent, PersistentStateComponent<Config>, DefaultLiveTemplatesProvider {
  private static final String ACTION_FIND_NEXT = "FindNext";
  private static final String ACTION_FIND_PREVIOUS = "FindPrevious";

  private AnAction nextAction;
  private AnAction prevAction;

  private Config configuration = new Config();

  @Override
  @NotNull
  public String getComponentName() {
    return "XPathView.XPathViewPlugin";
  }

  @Override
  public void initComponent() {
     ActionManager actionManager = ActionManager.getInstance();
    nextAction = actionManager.getAction(ACTION_FIND_NEXT);
    prevAction = actionManager.getAction(ACTION_FIND_PREVIOUS);

    if (nextAction != null && prevAction != null) {
      actionManager.unregisterAction(ACTION_FIND_NEXT);
      actionManager.unregisterAction(ACTION_FIND_PREVIOUS);
      actionManager.registerAction(ACTION_FIND_NEXT, new MyFindAction(nextAction, false));
      actionManager.registerAction(ACTION_FIND_PREVIOUS, new MyFindAction(prevAction, true));
    }
  }

  @Override
  public void disposeComponent() {
    // IDEA-97697
    //    final ActionManager actionManager = ActionManager.getInstance();
    //    actionManager.unregisterAction(ACTION_FIND_NEXT);
    //    actionManager.unregisterAction(ACTION_FIND_PREVIOUS);
    //    actionManager.registerAction(ACTION_FIND_NEXT, nextAction);
    //    actionManager.registerAction(ACTION_FIND_PREVIOUS, prevAction);
  }

  @Nullable
  @Override
  public Config getState() {
    return configuration;
  }

  @Override
  public void loadState(Config state) {
    configuration = state;
  }

  /**
   * Returns the configuration of this plugin
   *
   * @return the configuration object
   * @see Config
   */
  @NotNull
  public Config getConfig() {
    return configuration;
  }

  public void setConfig(@NotNull Config configuration) {
    this.configuration = configuration;
  }

  public static XPathAppComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(XPathAppComponent.class);
  }

  class MyFindAction extends AnAction implements DumbAware {
    private final AnAction origAction;
    private final boolean isPrev;
    private boolean wrapAround;

    public MyFindAction(AnAction origAction, boolean isPrev) {
      this.origAction = origAction;
      this.isPrev = isPrev;

      copyFrom(origAction);
      setEnabledInModalContext(origAction.isEnabledInModalContext());
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
      final Editor editor = CommonDataKeys.EDITOR.getData(event.getDataContext());
      if (editor != null) {
        if (HighlighterUtil.hasHighlighters(editor)) {
          final int offset = editor.getCaretModel().getOffset();
          final List<RangeHighlighter> hl = HighlighterUtil.getHighlighters(editor);
          int diff = Integer.MAX_VALUE;
          RangeHighlighter next = null;
          for (RangeHighlighter highlighter : hl) {
            if (isPrev) {
              if (highlighter.getStartOffset() < offset && offset - highlighter.getStartOffset() < diff) {
                diff = offset - highlighter.getStartOffset();
                next = highlighter;
              }
            }
            else {
              if (highlighter.getStartOffset() > offset && highlighter.getStartOffset() - offset < diff) {
                diff = highlighter.getStartOffset() - offset;
                next = highlighter;
              }
            }
          }

          final int startOffset;
          if (next != null) {
            startOffset = next.getStartOffset();
          }
          else if (wrapAround) {
            startOffset = hl.get(isPrev ? hl.size() - 1 : 0).getStartOffset();
          }
          else {
            final String info = (isPrev ? "First" : "Last") +
                                " XPath match reached. Press " +
                                (isPrev ? getShortcutText(prevAction) : getShortcutText(nextAction)) +
                                " to search from the " +
                                (isPrev ? "bottom" : "top");

            showEditorHint(info, editor);

            wrapAround = true;
            return;
          }
          editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(startOffset), ScrollType.MAKE_VISIBLE);
          editor.getCaretModel().moveToOffset(startOffset);
          wrapAround = false;
          return;
        }
      }
      origAction.actionPerformed(event);
    }

    @Override
    public void update(AnActionEvent event) {
      super.update(event);
      origAction.update(event);
    }

    @Override
    public boolean displayTextInToolbar() {
      return origAction.displayTextInToolbar();
    }

    @Override
    public void setDefaultIcon(boolean b) {
      origAction.setDefaultIcon(b);
    }

    @Override
    public boolean isDefaultIcon() {
      return origAction.isDefaultIcon();
    }
  }

  public static void showEditorHint(final String info, final Editor editor) {
    final JLabel label = new JLabel(info);
    label.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.WHITE, Gray._128),
      BorderFactory.createEmptyBorder(3, 5, 3, 5)));
    label.setForeground(JBColor.foreground());
    label.setBackground(HintUtil.INFORMATION_COLOR);
    label.setOpaque(true);
    label.setFont(label.getFont().deriveFont(Font.BOLD));

    final LightweightHint h = new LightweightHint(label);
    final Point point = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
    SwingUtilities.convertPointToScreen(point, editor.getContentComponent());

        /* === HintManager API Info ===

            public void showEditorHint(final LightweightHint hint,
                                        final Editor editor,
                                        Point p,
                                        int flags,
                                        int timeout,
                                        boolean reviveOnEditorChange)


            reviveOnEditorChange means hint should stay even if active editor have been changed. It's should rarely be true.

            possible flags are:
                public static final int HIDE_BY_ESCAPE = 0x01;
                public static final int HIDE_BY_ANY_KEY = 0x02;
                public static final int HIDE_BY_LOOKUP_ITEM_CHANGE = 0x04;
                public static final int HIDE_BY_TEXT_CHANGE = 0x08;
                public static final int HIDE_BY_OTHER_HINT = 0x10;
                public static final int HIDE_BY_SCROLLING = 0x20;
                public static final int HIDE_IF_OUT_OF_EDITOR = 0x40;
                public static final int UPDATE_BY_SCROLLING = 0x80;
        */
    final int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_SCROLLING;
    HintManagerImpl.getInstanceImpl().showEditorHint(h, editor, point, flags, 0, false);
  }

  public static String getShortcutText(final AnAction action) {
    final ShortcutSet shortcutSet = action.getShortcutSet();
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    for (final Shortcut shortcut : shortcuts) {
      final String text = KeymapUtil.getShortcutText(shortcut);
      if (text.length() > 0) return text;
    }
    return ActionManager.getInstance().getId(action);
  }

  @Override
  public String[] getDefaultLiveTemplateFiles() {
    return new String[]{"/liveTemplates/xsl"};
  }

  @Override
  public String[] getHiddenLiveTemplateFiles() {
    return null;
  }
}
