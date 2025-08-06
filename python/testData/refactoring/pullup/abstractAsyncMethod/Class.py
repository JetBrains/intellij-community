from SuperClass import Parent

class Child(Parent):
    async def async_method(self):
        """An async method that should be pulled up"""
        return "result"