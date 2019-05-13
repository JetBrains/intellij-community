class First:
    def __init__(self):
        print('First inited')


class Second(First):
    def __init__(self):
        super(__class__, self).__init__()
        print('Second inited')