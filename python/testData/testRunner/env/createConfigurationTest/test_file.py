# This does not work

import aid.test.base
sp<caret>am=42

class TheTest(aid.test.base.TestBase):

    def test_case_1(self):
        self.fail()

    def test_case_2(self):
        pass