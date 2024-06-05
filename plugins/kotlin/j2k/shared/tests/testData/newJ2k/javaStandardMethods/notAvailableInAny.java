public class J {
    void simple(J j) {
        notify();
        notifyAll();
        wait();
        wait(42);
        wait(42, 42);
        j.notify();
        j.notifyAll();
        j.wait();
        j.wait(42);
        j.wait(42, 42);
    }

    void withCast(Number i) {
        ((Integer) i).notify();
        ((Integer) i).notifyAll();
        ((Integer) i).wait();
        ((Integer) i).wait(42);
        ((Integer) i).wait(42, 42);
        ((Object) i).notify();
        ((Object) i).notifyAll();
        ((Object) i).wait();
        ((Object) i).wait(42);
        ((Object) i).wait(42, 42);
    }
}