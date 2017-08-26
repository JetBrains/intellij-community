from parameterized import parameterized


class TestDecorator(object):
    @parameterized.expand([my_ref for my_ref in range(10)])
    def test_decorator(self, r):
        for my_ref in range(r):
            self.assertGreaterEqual(my_ < ref > ref, 0)
