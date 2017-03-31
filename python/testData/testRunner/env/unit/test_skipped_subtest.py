import unittest


class SampleTest(unittest.TestCase):
    widget = None

    def setUp(self):
        self.widget = 'abcd'

    def tearDown(self):
        self.widget = None

    def test_foo(self):
        for i in range(20):
            with self.subTest(i=i):
                if i % 2:
                    self.assertTrue(False)
                elif not (i % 5):
                    self.skipTest('it happen')
                else:
                    self.assertEqual(self.widget, 'abcd')