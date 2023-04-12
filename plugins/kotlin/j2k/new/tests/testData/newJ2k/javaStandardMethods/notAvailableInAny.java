public class J {
    void foo(J j) {
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
}