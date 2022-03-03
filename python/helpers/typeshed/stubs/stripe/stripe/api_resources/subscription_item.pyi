from stripe.api_resources.abstract import (
    CreateableAPIResource as CreateableAPIResource,
    DeletableAPIResource as DeletableAPIResource,
    ListableAPIResource as ListableAPIResource,
    UpdateableAPIResource as UpdateableAPIResource,
    nested_resource_class_methods as nested_resource_class_methods,
)

class SubscriptionItem(CreateableAPIResource, DeletableAPIResource, ListableAPIResource, UpdateableAPIResource):
    OBJECT_NAME: str
    def usage_record_summaries(self, **params): ...
    @classmethod
    def usage_records_url(cls, id, nested_id=...): ...
    @classmethod
    def usage_records_request(
        cls, method, url, api_key=..., idempotency_key=..., stripe_version=..., stripe_account=..., **params
    ): ...
    @classmethod
    def create_usage_record(cls, id, **params): ...
    @classmethod
    def usage_record_summarys_url(cls, id, nested_id=...): ...
    @classmethod
    def usage_record_summarys_request(
        cls, method, url, api_key=..., idempotency_key=..., stripe_version=..., stripe_account=..., **params
    ): ...
    @classmethod
    def list_usage_record_summaries(cls, id, **params): ...
