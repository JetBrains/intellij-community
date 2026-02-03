from twisted.trial import unittest
from twisted.internet.defer import inlineCallbacks
from twisted.internet import defer, reactor


def badCode(d):
    raise Exception('boom!')
    d.callback(None)

class TestFailure(unittest.TestCase):

    @inlineCallbacks
    def testBadCode(self):
        d = defer.Deferred()
        reactor.callLater(0.1, badCode, d)
        yield d.addTimeout(0.2, reactor)