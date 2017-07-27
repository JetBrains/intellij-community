package training.ui;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.keymap.KeymapUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by karashevich on 01/09/15.
 */
public class Message {


    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    @Nullable
    public static Message[] convert(@Nullable Element element) {
        if (element == null) {
            return new Message[0];
        }
        List<Message> list = new ArrayList<>();
        element.getContent().forEach(content -> {
            if (content instanceof Text) {
                list.add(new Message(content.getValue(), MessageType.TEXT_REGULAR));
            }
            else if (content instanceof Element) {
                XMLOutputter outputter = new XMLOutputter();
                MessageType type = MessageType.TEXT_REGULAR;
                String text = outputter.outputString(((Element)content).getContent());
                switch (((Element)content).getName()) {
                    case "code":
                        type = MessageType.CODE;
                        break;
                    case "strong":
                        type = MessageType.TEXT_BOLD;
                        break;
                    case "link":
                        type = MessageType.LINK;
                        break;
                    case "action":
                        type = MessageType.SHORTCUT;
                        final KeyStroke shortcutByActionId = KeymapUtil.INSTANCE.getShortcutByActionId(text);
                        if (shortcutByActionId != null) {
                            text = KeymapUtil.INSTANCE.getKeyStrokeText(shortcutByActionId);
                        }
                        break;
                    case "ide":
                        type = MessageType.TEXT_REGULAR;
                        text = ApplicationNamesInfo.getInstance().getFullProductName();
                        break;
                }
                Message message = new Message(text, type);
                list.add(message);
            }
        });
        return ContainerUtil.toArray(list, new Message[0]);
    }

    public enum MessageType {TEXT_REGULAR, TEXT_BOLD, SHORTCUT, CODE, LINK, CHECK}
    @NotNull
    private String messageText;
    private int startOffset;
    private int endOffset;
    @Nullable
    private Runnable runnable = null;

    @NotNull
    private MessageType messageType;
    public Message(@NotNull String messageText, @NotNull MessageType messageType) {
        this.messageText = messageText;
        this.messageType = messageType;
    }

    @Nullable
    public Runnable getRunnable() {
        return runnable;
    }

    public void setRunnable(@Nullable Runnable runnable) {
        this.runnable = runnable;
    }

    public String getText() {
        return messageText;
    }

    public MessageType getType() {
        return messageType;
    }

    public boolean isText() {
        return messageType == MessageType.TEXT_REGULAR || messageType == MessageType.TEXT_BOLD;
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageText='" + messageText + '\'' +
                ", messageType=" + messageType +
                '}';
    }
}
