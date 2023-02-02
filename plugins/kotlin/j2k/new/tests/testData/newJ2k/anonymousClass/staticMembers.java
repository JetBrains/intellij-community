class J1 {
    public void run() {
        problem();

        new Runnable() {
            static final int x = 0;
            static int y = 0;

            @Override
            public void run() {
                x + y;
                problem();
            }
        };
    }

    static void problem() {
    }
}

class J2 {
    private static Runnable foo = new Runnable() {
        static final int x = 0;
        static int y = 0;

        @Override
        public void run() {
            x + y;
            problem();
        }

        static void problem() {
        }
    };
}