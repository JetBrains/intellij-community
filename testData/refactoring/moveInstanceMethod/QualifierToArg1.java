class CommandQueue {

}

class CommandManager {
    void <caret>f(CommandQueue q) {
    }

    void g() {

    }

    CommandQueue getCommandQueue() {
        return null;
    }
}

class Application {
    CommandManager myManager;
    {
        myManager.f(myManager.getCommandQueue());
    }
}