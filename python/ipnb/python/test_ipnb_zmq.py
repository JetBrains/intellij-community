"""Tests of interactions with IPython kernel via 0MQ."""


from unittest import TestCase

import zmq


class ZMQVersionTest(TestCase):
    def test_version_available(self):
        self.assertIsNotNone(zmq.pyzmq_version())
