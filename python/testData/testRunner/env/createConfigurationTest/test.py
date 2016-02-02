# This does not work

import aid.test.base


class Th<caret>eTest(aid.test.base.TestBase):

    def test_case_1(self):
        self.fail()

    def test_case_2(self):
        pass