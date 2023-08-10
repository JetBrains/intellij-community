public class TestClass {
    private static String getCheckKey(String category, String name, boolean createWithProject) {
        return category + ':' + name + ':' + createWithProject;
    }
}