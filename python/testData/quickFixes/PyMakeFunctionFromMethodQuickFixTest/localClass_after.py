def make_server(config):
    def method_name2(x):
        x.send_error(404, 'File not found')

    class Handler(object):
        pass