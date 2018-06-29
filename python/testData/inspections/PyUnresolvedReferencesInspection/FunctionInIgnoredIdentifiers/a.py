from mock import patch


class Spam(object):
    pass


with patch.object(Spam, 'foo'):
    pass
