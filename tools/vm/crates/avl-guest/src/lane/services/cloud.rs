//! The JetBrains Account and the JetBrains Cloud Platform (JCP) of a lane IDE, emulated on loopback.
//!
//! The emulated account is one user. The login approves at once: the authorization answers a redirect to the IDE's
//! callback with a fixed code. The id token is unsigned and names this server as its issuer, so a login survives an
//! IDE restart. The JCP routes answer the AI access discovery and the provisioning that Air reads before it offers a
//! cloud run. A request to any other path answers 501, and the emulator records it.

use std::sync::{Mutex, MutexGuard};

use serde::Serialize;

use super::control;
use super::http::{self, Request, Response};
use super::{Handler, Service};
use crate::reply::AgentRefusal;

#[cfg(test)]
mod tests;

/// The name of the service in its control route and its threads.
pub(crate) const NAME: &str = "cloud";

/// The id of the emulated user, which the bridge spells `AIR_LANE_JETBRAINS_ACCOUNT_USER_ID`.
pub(crate) const USER_ID: &str = "air-lane-user";

const CONFIGURATION_PATH: &str = "/config/JetBrainsAccount.xml";
const RESOURCE_MAPPING_PATH: &str = "/config/JetBrainsResourceMapping.json";
const AUTHORIZATION_PATH: &str = "/oauth2/auth";
const TOKEN_PATH: &str = "/oauth2/token";
const ORGS_USER_INFO_PATH: &str = "/oauth2/orgs-user-info";
const JCP_PATH: &str = "/jcp";
const CLIENT_ID: &str = "ide";
const AUTHORIZATION_CODE: &str = "air-lane-authorization-code";
const REFRESH_TOKEN: &str = "air-lane-refresh-token";

const XML: &str = "application/xml; charset=UTF-8";
const JSON: &str = "application/json; charset=UTF-8";
const TEXT: &str = "text/plain; charset=UTF-8";

const AI_ACCESS_OPTIONS_PATH: &str = "/user-management/api/ai-access-options";
const ORG_FEATURE_FLAGS_PATH: &str = "/user-management/api/org-feature-flags";
const TOOLS_PROVISIONING_PATH: &str = "/ai-management/api/provisioning/v1/tools";
const GATEWAYS_PROVISIONING_PATH: &str = "/ai-management/api/provisioning/v1/gateways";
const AGENTS_PROVISIONING_PATH: &str = "/ai-management/api/provisioning/v1/agents";
const ACP_PROVISIONING_PATH: &str = "/ai-management/api/provisioning/v1/acp";

const NO_AI_ACCESS_OPTIONS_BODY: &str = r#"{"aiAccessOptions":[]}"#;
const CLOUD_WORKSPACE_BODY: &str = concat!(
    r#"{"aiAccessOptions":[{"type":"workspace","workspaceId":"air-lane-workspace","workspaceName":"Air Lane Workspace","#,
    r#""orgId":"air-lane-org","orgName":"Air Lane Organization","aiAccessEnabled":true}]}"#
);
const ORG_FEATURE_FLAGS_BODY: &str = r#"{"flags":{"agentic-core/cloud-tasks":true,"ai-seats":true}}"#;
const TOOLS_PROVISIONING_BODY: &str = r#"{"air":{"status":"allowed","allowCloudAgentsRun":true},"aiAssistant":{"status":"disallowed"}}"#;
const GATEWAYS_PROVISIONING_BODY: &str = r#"{"gateways":[]}"#;
const AGENTS_PROVISIONING_BODY: &str = r#"{"items":[]}"#;

/// What the emulated account belongs to, as the AI access discovery states it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Organization {
    /// No organization workspace and no AI license, so the IDE offers no cloud run.
    None,
    /// One organization workspace with AI access that permits cloud runs.
    CloudWorkspace,
}

/// What the emulator saw since the last clear, as the control route states it.
#[derive(Clone, Debug, Default, Serialize)]
pub(crate) struct Record {
    /// How many AI access discoveries the IDE made.
    pub discoveries: usize,
    /// Every request that a JCP route answered, as `METHOD target`, in arrival order.
    pub served: Vec<String>,
    /// Every request that the emulator could not answer, as `METHOD target`, in arrival order.
    pub unexpected: Vec<String>,
}

/// The handler of the cloud service.
pub(crate) struct Cloud {
    base_url: String,
    token: String,
    organization: Organization,
    record: Mutex<Record>,
}

/// Starts the emulator on `127.0.0.1:<port>`. The AI access discovery answers `organization`.
pub(crate) fn start(port: u16, token: &str, organization: Organization) -> Result<Service<Cloud>, AgentRefusal> {
    Service::start(NAME, port, |address| Cloud {
        base_url: format!("http://{address}"),
        token: token.to_owned(),
        organization,
        record: Mutex::new(Record::default()),
    })
}

impl Handler for Cloud {
    fn handle(&self, request: &Request) -> Response {
        if let Some(route) = control::route(request, NAME, &self.token) {
            return route.map_or_else(|refusal| refusal, |route| self.control(request, route));
        }
        let path = if request.path.len() > 1 {
            request.path.trim_end_matches('/')
        } else {
            &request.path
        };
        let answer = match path {
            CONFIGURATION_PATH => Some(Response::with_body(200, XML, self.configuration())),
            RESOURCE_MAPPING_PATH => Some(Response::with_body(200, JSON, "[]")),
            AUTHORIZATION_PATH => Some(Self::approve_login(request)),
            TOKEN_PATH => Some(Response::with_body(200, JSON, self.token_answer(request))),
            ORGS_USER_INFO_PATH => Some(Response::with_body(
                200,
                JSON,
                r#"{"jwt":"air-lane-orgs-user-info","expires_in":3600}"#,
            )),
            _ => path
                .strip_prefix(JCP_PATH)
                .filter(|jcp| jcp.starts_with('/'))
                .and_then(|jcp| self.jcp(request, jcp)),
        };
        answer.unwrap_or_else(|| {
            let target = request.target();
            self.lock().unexpected.push(format!("{} {target}", request.method));
            Response::with_body(501, TEXT, format!("the Air lane emulates no {target}"))
        })
    }
}

impl Cloud {
    fn lock(&self) -> MutexGuard<'_, Record> {
        self.record.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    /// The properties that point the IDE at this emulator. The regional property wins over the plain one for the
    /// default region, so both name the configuration.
    pub(crate) fn launch_properties(&self) -> Vec<(&'static str, String)> {
        let configuration = format!("{}{CONFIGURATION_PATH}", self.base_url);
        vec![
            ("jb.service.configuration.url", configuration.clone()),
            ("jb.service.configuration.url.not_set", configuration),
            ("jb.mapper.configuration.url", format!("{}{RESOURCE_MAPPING_PATH}", self.base_url)),
        ]
    }

    /// The service configuration in the shape of the production `JetBrainsAccount.xml`. Every URL names this server.
    fn configuration(&self) -> String {
        let base = &self.base_url;
        [
            "<config>".to_owned(),
            format!("  <url type=\"auth\" value=\"{base}/lservice/service-auth\" enabled=\"true\"/>"),
            format!("  <url type=\"license\" value=\"{base}/lservice\" enabled=\"true\"/>"),
            format!("  <url type=\"reset_password\" value=\"{base}/forgot-password\" enabled=\"true\"/>"),
            format!("  <url type=\"oauth2\" value=\"{base}/oauth2\" enabled=\"true\"/>"),
            format!("  <url type=\"account_info\" value=\"{base}/lservice/account/info\" enabled=\"true\"/>"),
            format!("  <url type=\"signup\" value=\"{base}/signup\" enabled=\"true\"/>"),
            format!("  <url type=\"manage_account\" value=\"{base}/\" enabled=\"true\"/>"),
            format!("  <url type=\"account_payment_methods\" value=\"{base}/licenses/tokens\" enabled=\"true\"/>"),
            format!("  <url type=\"jcp\" value=\"{base}{JCP_PATH}\" enabled=\"true\"/>"),
            "  <oauth>".to_owned(),
            format!("    <clientId>{CLIENT_ID}</clientId>"),
            "    <scope>offline_access</scope>".to_owned(),
            format!("    <authEndpoint>{base}{AUTHORIZATION_PATH}</authEndpoint>"),
            format!("    <tokenEndpoint>{base}{TOKEN_PATH}</tokenEndpoint>"),
            format!("    <orgsUserInfoJwtEndpoint>{base}{ORGS_USER_INFO_PATH}</orgsUserInfoJwtEndpoint>"),
            "    <providers/>".to_owned(),
            "  </oauth>".to_owned(),
            "</config>".to_owned(),
        ]
        .join("\n")
    }

    fn approve_login(request: &Request) -> Response {
        let Some(redirect) = request.query_param("redirect_uri") else {
            return Response::with_body(500, TEXT, format!("the login request names no redirect_uri: {}", request.target()));
        };
        let state = request.query_param("state").unwrap_or_default();
        Response::empty(302).header(
            "Location",
            &format!("{redirect}?code={AUTHORIZATION_CODE}&state={}", http::form_encode(&state)),
        )
    }

    fn token_answer(&self, request: &Request) -> String {
        let form = http::form(&request.body_text());
        let field = |name: &str| form.iter().find(|(key, _)| key == name).map(|(_, value)| value.as_str());
        if field("grant_type") == Some("authorization_code") {
            format!(
                r#"{{"access_token":"air-lane-login","id_token":"{}","refresh_token":"{REFRESH_TOKEN}","expires_in":3600}}"#,
                self.id_token()
            )
        } else {
            let access = format!("air-lane-{}", field("audience").unwrap_or("token"));
            format!(r#"{{"access_token":{},"expires_in":3600}}"#, serde_json::Value::from(access))
        }
    }

    /// An unsigned id token of the emulated user. The platform reads its claims and checks no signature.
    fn id_token(&self) -> String {
        let payload = format!(
            r#"{{"user_id":"{USER_ID}","email":"air-lane@example.com","jba_login":"air-lane","full_name":"Air Lane","iss":"{}","sub":"{USER_ID}"}}"#,
            self.base_url
        );
        format!("{}.{}.signature", base64_url(b"{}"), base64_url(payload.as_bytes()))
    }

    /// Answers a request below the JCP base URL, or `None` when no JCP route owns `path`.
    fn jcp(&self, request: &Request, path: &str) -> Option<Response> {
        let body = match path {
            AI_ACCESS_OPTIONS_PATH => Some(match self.organization {
                Organization::None => NO_AI_ACCESS_OPTIONS_BODY,
                Organization::CloudWorkspace => CLOUD_WORKSPACE_BODY,
            }),
            ORG_FEATURE_FLAGS_PATH => Some(ORG_FEATURE_FLAGS_BODY),
            TOOLS_PROVISIONING_PATH => Some(TOOLS_PROVISIONING_BODY),
            GATEWAYS_PROVISIONING_PATH => Some(GATEWAYS_PROVISIONING_BODY),
            AGENTS_PROVISIONING_PATH => Some(AGENTS_PROVISIONING_BODY),
            // The organization has no ACP registry. Air reads 404 as "not configured".
            ACP_PROVISIONING_PATH => None,
            _ => return None,
        };
        let mut record = self.lock();
        if path == AI_ACCESS_OPTIONS_PATH {
            record.discoveries += 1;
        }
        record.served.push(format!("{} {}", request.method, request.target()));
        Some(body.map_or_else(|| Response::empty(404), |body| Response::with_body(200, JSON, body)))
    }

    fn control(&self, request: &Request, route: &str) -> Response {
        match (route, request.method.as_str()) {
            ("record", "GET") => control::answer(&*self.lock()),
            ("record", "DELETE") => control::answer(&std::mem::take(&mut *self.lock())),
            ("record", _) => control::method_not_allowed(request, "GET or DELETE"),
            _ => control::unknown_route(request),
        }
    }
}

/// The unpadded base64url text of `bytes`. It is written by hand, because no base64 crate is in the closure of the
/// guest agent.
fn base64_url(bytes: &[u8]) -> String {
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    let mut text = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
        let triple = chunk
            .iter()
            .enumerate()
            .fold(0_u32, |triple, (index, &byte)| triple | u32::from(byte) << (16 - 8 * index));
        for index in 0..=chunk.len() {
            let sextet = (triple >> (18 - 6 * index)) & 0x3f;
            text.push(char::from(ALPHABET[usize::try_from(sextet).unwrap_or_default()]));
        }
    }
    text
}
