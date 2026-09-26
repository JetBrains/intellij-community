// TODO: `name` parameter should be nullable in K2, looks like a bug in PsiClassType#getPsiContext()
public class TestClass {
    private static String getCheckKey(String category, String name, boolean createWithProject) {
        return category + ':' + name + ':' + createWithProject;
    }
}