/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.testng.annotations.*;
public class Testt {
    @AfterTest()
    public void after1() {
    }

    @AfterMethod()
    public void after2() {
    }

    @AfterSuite()
    public void after3() {
    }

    @AfterGroups()
    public void after4() {
    }

   @AfterTest()
    public void after5() {
    }

    @AfterTest()
    public void after21() {
    }

    @AfterMethod()
    public void after22() {
    }

    @AfterSuite()
    public void after23() {
    }


    @AfterTest()
    public void after25() {
    }

    @BeforeTest()
    public void before1() {
    }

    @BeforeMethod()
    public void before2() {
    }

    @BeforeTest()
    public void before3() {
    }

    @BeforeSuite()
    public void before4() {
    }

    @BeforeGroups()
    public void before5() {
    }

    @BeforeTest()
    public void before21() {
    }

    @BeforeMethod()
    public void before22() {
    }

    @BeforeTest()
    public void before23() {
    }

    @BeforeSuite()
    public void before24() {
    }

    @BeforeSuite
    @AfterSuite
    public void afterBefore(){
    }

}
