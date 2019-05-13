# coding=utf-8
"""
Lettuce terrain hooks: http://lettuce.it/reference/terrain.html
"""
__author__ = 'Ilya.Kazakevich'


class __When(object):
    @staticmethod
    def all(function):
        """
        Runs before/after all features, scenarios and steps
        """
        pass

    @staticmethod
    def each_step(function):
        """
        Runs before/after each step
        """
        pass

    @staticmethod
    def each_scenario(function):
        """
        Runs before/after each scenario
        """
        pass

    @staticmethod
    def each_outline(function):
        """
        Runs before/after each outline in scenario outline.
        Accepts scenario (object) and outline(dict) as arguments.
        Available only since 0.22
        """
        pass

    @staticmethod
    def each_background(function):
        """
        Runs before/after each background
        """
        pass

    @staticmethod
    def each_feature(function):
        """
        Runs before/after each feature
        """
        pass

    @staticmethod
    def each_app(function):
        """
        Runs before/after each Django app.
        """
        pass

    @staticmethod
    def runserver(function):
        """
        Runs before/after lettuce starts up the built-in http server.
        """
        pass

    @staticmethod
    def handle_request(function):
        """
        Runs before/after lettuce’s built-in HTTP server responds to a request.
        """
        pass


before = __When()
after = __When()

