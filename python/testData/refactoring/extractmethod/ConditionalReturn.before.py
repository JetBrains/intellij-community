class Conference(object):
    def __init__(self):
        self.talks = []

    def getTalkAt(self, hour):
        for start, end, name in self.talks:
            <selection>if hour >= start and hour < end: return name</selection>