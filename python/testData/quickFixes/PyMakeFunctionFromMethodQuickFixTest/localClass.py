def make_server(config):
    class Handler(object):
        def method_<caret>name2(self, x):
            x.send_error(404, 'File not found')