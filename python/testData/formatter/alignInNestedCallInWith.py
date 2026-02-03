import logging
from nose.tools import assert_raises_regex


def _assert_stuff(i):
    with assert_raises_regex(
            logging.INFO,
            'Did stuff to {} because of reasons that take up a whole line of text'.format(
                    i.relname)):
        pass