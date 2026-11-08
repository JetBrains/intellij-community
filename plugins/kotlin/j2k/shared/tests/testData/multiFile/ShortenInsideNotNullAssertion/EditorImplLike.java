package test2;

import test.EditorThreading;
import test.View;

public class EditorImplLike {
    private final View myView = new View();

    public int getVisibleLineCount() {
        return EditorThreading.compute(() -> myView.getVisibleLineCount());
    }

    public int lineHeight() {
        return EditorThreading.compute(() -> myView.getLineHeight());
    }

    public Integer boxedLineHeight() {
        return EditorThreading.compute(() -> myView.getLineHeight());
    }

    public Integer getBoxedVisibleLineCount() {
        return EditorThreading.compute(() -> myView.getVisibleLineCount());
    }

    public void takeInt(int x) {
    }

    public void argumentCase() {
        takeInt(EditorThreading.compute(() -> myView.getLineHeight()));
    }
}
