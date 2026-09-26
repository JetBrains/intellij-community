from unittest import TestCase


class Spam(TestCase):
    def eggs(self):
        self.fail()

    def eggs_and_ham(self):
        self.fail()
