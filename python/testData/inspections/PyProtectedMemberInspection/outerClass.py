
class AbstractExperimentManager(object):

    def _call_on_plugins(self, method_name, *args, **kwargs):
        pass

class EventExperimentManager(AbstractExperimentManager):


    class __SomeEvent(object):

        def __init__(self, parent):
            """
            :param EventExperimentManager parent: Parent
            """
            self.parent = parent

        def __call__(self, event):
             self.parent._call_on_plugins("foo")