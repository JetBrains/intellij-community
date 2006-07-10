class CommandQueue {

}

class CommandManager {
    void <caret>f(CommandQueue q) {
      g();
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