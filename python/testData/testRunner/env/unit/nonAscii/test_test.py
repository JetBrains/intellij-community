# -*- coding: utf-8 -*-
import unittest


class TestCase(unittest.TestCase):
    @unittest.skip(u"Здесь ошибка")
    def test(self):
        self.assertEqual(2+2, 5)