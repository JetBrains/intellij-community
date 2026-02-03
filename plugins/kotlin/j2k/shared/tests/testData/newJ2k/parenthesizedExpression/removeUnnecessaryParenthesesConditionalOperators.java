public class J {
    private void test() {
        boolean b = bool()
                    && bool();

        if (bool()
            || bool()) {
        }

        while (bool()
               && bool()) {
        }

        takesBool(bool()
                  || bool());
    }

    private boolean bool() {
        return true;
    }

    private void takesBool(boolean b) {
    }
}