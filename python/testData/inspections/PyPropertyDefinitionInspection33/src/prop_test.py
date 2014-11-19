class A:
    @property
    def normal_property(self):
        pass

    @property
    @abstractproperty
    def abstract_property1(self):
        pass

    @property
    @abstractmethod
    def abstract_property2(self):
        pass
