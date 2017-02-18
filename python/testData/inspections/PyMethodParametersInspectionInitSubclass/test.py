class QuestBase:
    def __init_subclass__(cls, swallow, **kwargs):
        cls.swallow = swallow
        super().__init_subclass__(**kwargs)


class QuestBase:
    def __init_subclass__(<weak_warning descr="Usually first parameter of such methods is named 'cls'">self</weak_warning>, swallow, **kwargs):
        self.swallow = swallow
        super().__init_subclass__(**kwargs)