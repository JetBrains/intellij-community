import java.io.OutputStream;
import java.io.IOException;

class A {
    {
        try {
            OutputStream out = null;

            newMethod(out);
        } catch(Throwable t) {
        }
    }

    private void newMethod(OutputStream out) throws IOException {
        try {
        } finally {
            out.close();
        }
    }
}