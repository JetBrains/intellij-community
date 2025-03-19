from _typeshed import Incomplete

from hvac.api.vault_api_base import VaultApiBase

class JWT(VaultApiBase):
    DEFAULT_PATH: str
    def resolve_path(self, path): ...
    def configure(
        self,
        oidc_discovery_url: Incomplete | None = None,
        oidc_discovery_ca_pem: Incomplete | None = None,
        oidc_client_id: Incomplete | None = None,
        oidc_client_secret: Incomplete | None = None,
        oidc_response_mode: Incomplete | None = None,
        oidc_response_types: Incomplete | None = None,
        jwks_url: Incomplete | None = None,
        jwks_ca_pem: Incomplete | None = None,
        jwt_validation_pubkeys: Incomplete | None = None,
        bound_issuer: Incomplete | None = None,
        jwt_supported_algs: Incomplete | None = None,
        default_role: Incomplete | None = None,
        provider_config: Incomplete | None = None,
        path: str | None = None,
        namespace_in_state: bool | None = None,
    ): ...
    def read_config(self, path: Incomplete | None = None): ...
    def create_role(
        self,
        name,
        user_claim,
        allowed_redirect_uris,
        role_type: str = "jwt",
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
    ): ...
    def read_role(self, name, path: Incomplete | None = None): ...
    def list_roles(self, path: Incomplete | None = None): ...
    def delete_role(self, name, path: Incomplete | None = None): ...
    def oidc_authorization_url_request(self, role, redirect_uri, path: Incomplete | None = None): ...
    def oidc_callback(self, state, nonce, code, path: Incomplete | None = None): ...
    def jwt_login(self, role, jwt, use_token: bool = True, path: Incomplete | None = None): ...
