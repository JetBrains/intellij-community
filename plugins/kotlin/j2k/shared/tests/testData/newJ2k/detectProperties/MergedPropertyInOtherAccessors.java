public class Editor {
    private final State myState = new State();

    public boolean isScrollToCaret() {
        return myState.isScrollToCaret();
    }

    public void setScrollToCaret(boolean scrollToCaret) {
        myState.setScrollToCaret(scrollToCaret);
    }

    public State getState() {
        return myState;
    }
}