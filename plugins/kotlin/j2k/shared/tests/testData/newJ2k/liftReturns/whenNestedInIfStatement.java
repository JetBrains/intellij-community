class Test {
    public String test(int n) {
        if (n > 1) {
            switch (n) {
                case 2:
                    return "2"
                case 3:
                    return "3";
                default:
                    return "too high";
            }
        } else {
            return "too low";
        }
    }
}