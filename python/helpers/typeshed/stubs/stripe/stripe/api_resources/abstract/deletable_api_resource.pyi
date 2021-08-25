from stripe.api_resources.abstract.api_resource import APIResource as APIResource

class DeletableAPIResource(APIResource):
    def delete(self, **params): ...
