package training.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
