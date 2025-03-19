from _typeshed import Incomplete

from hvac.api.auth_methods.jwt import JWT

class OIDC(JWT):
    DEFAULT_PATH: str
    def create_role(
        self,
        name,
        user_claim,
        allowed_redirect_uris,
        role_type: str = "oidc",
        bound_audiences: Incomplete | None = None,
        clock_skew_leeway: Incomplete | None = None,
        expiration_leeway: Incomplete | None = None,
        not_before_leeway: Incomplete | None = None,
        bound_subject: Incomplete | None = None,
        bound_claims: Incomplete | None = None,
        groups_claim: Incomplete | None = None,
        claim_mappings: Incomplete | None = None,
        oidc_scopes: Incomplete | None = None,
        bound_claims_type: str = "string",
        verbose_oidc_logging: bool = False,
        token_ttl: Incomplete | None = None,
        token_max_ttl: Incomplete | None = None,
        token_policies: Incomplete | None = None,
        token_bound_cidrs: Incomplete | None = None,
        token_explicit_max_ttl: Incomplete | None = None,
        token_no_default_policy: Incomplete | None = None,
        token_num_uses: Incomplete | None = None,
        token_period: Incomplete | None = None,
        token_type: Incomplete | None = None,
        path: Incomplete | None = None,
        user_claim_json_pointer: Incomplete | None = None,
    ) -> None: ...
