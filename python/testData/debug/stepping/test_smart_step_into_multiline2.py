from __future__ import print_function

import json


def return_my_lucking_link():
    url = 'https://example.com/'
    return url


def return_my_lucking_payload():
    payload = {'some': 'data'}
    return payload


class A(object):
    def do_stuff(self, x, data=None):
        return "%s: %s" % (x, data)


r = A().do_stuff(  # breakpoint
    return_my_lucking_link(),
    data=json.dumps(return_my_lucking_payload())
)

print(r)  # breakpoint
