import unittest


class SampleTest(unittest.TestCase):

    def test_sample(self):
        for i in range(10):
            with self.subTest(i=str(i)+'.'+str(i)):
                self.assertTrue(i > 1)