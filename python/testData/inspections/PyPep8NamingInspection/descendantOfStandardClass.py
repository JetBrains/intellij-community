import collections

class MyDict(collections.OrderedDict):
    def getLast(self):
        if self:
            return list(self.values)[-1]
        raise ValueError()