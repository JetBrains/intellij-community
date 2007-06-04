import org.testng.annotations.*;

public class Testt {
    @AfterTest
    public void after1() {
    }

    @AfterMethod
    public void after2() {
    }

    @AfterSuite
    public void after3() {
    }

    @AfterGroups
    public void after4() {
    }

   @AfterTest
   public void after5() {
    }

    @AfterTest
    public void after21() {
    }

    @AfterMethod
    public void after22() {
    }

    @AfterSuite
    public void after23() {
    }


    @AfterTest
    public void after25() {
    }

    @BeforeTest
    public void before1() {
    }

    @BeforeMethod
    public void before2() {
    }

    @BeforeTest
    public void before3() {
    }

    @BeforeSuite
    public void before4() {
    }

    @BeforeGroups
    public void before5() {
    }

    @BeforeTest
    public void before21() {
    }

    @BeforeMethod
    public void before22() {
    }

    @BeforeTest
    public void before23() {
    }

    @BeforeSuite
    public void before24() {
    }

    @AfterTest
    @BeforeSuite
    public void afterBefore(){
    }

}
