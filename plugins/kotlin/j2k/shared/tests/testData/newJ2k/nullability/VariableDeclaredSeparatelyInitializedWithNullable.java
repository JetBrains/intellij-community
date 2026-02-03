public class Same {
    public Same returnNull() {
        return null;
    }

    public static void main(String[] args) {
        Same same;
        same = new Same().returnNull();

        Other other;
        other = new Other().returnNull();

        Other otherWithAssignment = new Other().returnNull();
    }
}