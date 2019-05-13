from unittest import TestCase


class FooTest(TestCase):
    def setUp(self):
        print(1)

    @classmethod
    def setUpClass(cls):
        print(1)

    def test_test(self):
        print(3)

    def test_2_test(self):
        self.fail("D")

    def tearDown(self):
        print("A")

    @classmethod
    def tearDownClass(cls):
        print("A")



