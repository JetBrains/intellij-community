import org.testng.annotations.Configuration;
 public class Testt {
    @<caret>Configuration(afterTestClass = true)
    public void after1() {
    }

    @Configuration(afterTestMethod = true)
    public void after2() {
    }

    @Configuration(afterSuite = true)
    public void after3() {
    }

    @Configuration(afterGroups = {"group1"})
    public void after4() {
    }

    @Configuration(afterTest = true)
    public void after5() {
    }

    @Configuration(afterTestClass = false)
    public void after21() {
    }

    @Configuration(afterTestMethod = false)
    public void after22() {
    }

    @Configuration(afterSuite = false)
    public void after23() {
    }

    @Configuration(afterTest = false)
    public void after25() {
    }

    @Configuration(beforeTestClass = true)
    public void before1() {
    }

    @Configuration(beforeTestMethod = true)
    public void before2() {
    }

    @Configuration(beforeTest = true)
    public void before3() {
    }

    @Configuration(beforeSuite = true)
    public void before4() {
    }

    @Configuration(beforeGroups = {"group2"})
    public void before5() {
    }

    @Configuration(beforeTestClass = false)
    public void before21() {
    }

    @Configuration(beforeTestMethod = false)
    public void before22() {
    }

    @Configuration(beforeTest = false)
    public void before23() {
    }

    @Configuration(beforeSuite = false)
    public void before24() {
    }

    @Configuration(beforeSuite = true, afterTest = true)
    public void afterBefore(){
    }

}
