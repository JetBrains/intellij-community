use expect_test::expect;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::lane::services::testing::{self, TOKEN};

fn start_cloud(organization: Organization) -> Service<Cloud> {
    start(0, TOKEN, organization).unwrap()
}

#[test]
fn the_configuration_names_this_emulator_for_every_url() {
    let service = start_cloud(Organization::None);
    let base = service.url();
    let answer = testing::get(service.address(), "/config/JetBrainsAccount.xml/");
    assert_eq!(
        (answer.status, answer.header("Content-Type")),
        (200, Some("application/xml; charset=UTF-8"))
    );
    expect![[r#"
        <config>
          <url type="auth" value="<base>/lservice/service-auth" enabled="true"/>
          <url type="license" value="<base>/lservice" enabled="true"/>
          <url type="reset_password" value="<base>/forgot-password" enabled="true"/>
          <url type="oauth2" value="<base>/oauth2" enabled="true"/>
          <url type="account_info" value="<base>/lservice/account/info" enabled="true"/>
          <url type="signup" value="<base>/signup" enabled="true"/>
          <url type="manage_account" value="<base>/" enabled="true"/>
          <url type="account_payment_methods" value="<base>/licenses/tokens" enabled="true"/>
          <url type="jcp" value="<base>/jcp" enabled="true"/>
          <oauth>
            <clientId>ide</clientId>
            <scope>offline_access</scope>
            <authEndpoint><base>/oauth2/auth</authEndpoint>
            <tokenEndpoint><base>/oauth2/token</tokenEndpoint>
            <orgsUserInfoJwtEndpoint><base>/oauth2/orgs-user-info</orgsUserInfoJwtEndpoint>
            <providers/>
          </oauth>
        </config>"#]]
    .assert_eq(&answer.text().replace(&base, "<base>"));
    let mapping = testing::get(service.address(), "/config/JetBrainsResourceMapping.json");
    assert_eq!((mapping.status, mapping.text()), (200, "[]".to_owned()));
    assert_eq!(
        service.handler().launch_properties(),
        [
            ("jb.service.configuration.url", format!("{base}/config/JetBrainsAccount.xml")),
            (
                "jb.service.configuration.url.not_set",
                format!("{base}/config/JetBrainsAccount.xml")
            ),
            (
                "jb.mapper.configuration.url",
                format!("{base}/config/JetBrainsResourceMapping.json")
            ),
        ]
    );
}

#[test]
fn the_login_redirects_to_the_callback_with_the_code_and_the_encoded_state() {
    let service = start_cloud(Organization::None);
    let callback = "http://127.0.0.1:41234/callback";
    let answer = testing::get(
        service.address(),
        &format!("/oauth2/auth?redirect_uri={callback}&state=a%20b&client_id=ide"),
    );
    assert_eq!(answer.status, 302);
    assert_eq!(
        answer.header("Location"),
        Some(format!("{callback}?code=air-lane-authorization-code&state=a+b").as_str())
    );
    assert_eq!(testing::get(service.address(), "/oauth2/auth?state=x").status, 500);
}

#[test]
fn the_token_route_answers_an_unsigned_id_token_whose_issuer_is_this_emulator() {
    let service = start_cloud(Organization::None);
    let form = b"grant_type=authorization_code&code=air-lane-authorization-code";
    let answer = testing::call(
        service.address(),
        "POST",
        "/oauth2/token",
        &[("Content-Type", "application/x-www-form-urlencoded")],
        form,
    );
    let token = answer.json();
    assert_eq!(token["access_token"], "air-lane-login");
    assert_eq!(token["refresh_token"], "air-lane-refresh-token");
    assert_eq!(token["expires_in"], 3600);
    let parts: Vec<&str> = token["id_token"].as_str().unwrap().split('.').collect();
    assert_eq!(parts[0], "e30");
    assert_eq!(parts[2], "signature");
    let payload = String::from_utf8(Base64::Url.decode(parts[1]).unwrap()).unwrap();
    assert_eq!(
        payload,
        format!(
            r#"{{"user_id":"air-lane-user","email":"air-lane@example.com","jba_login":"air-lane","full_name":"Air Lane","iss":"{}","sub":"air-lane-user"}}"#,
            service.url()
        )
    );

    let form = b"grant_type=switch_audience&audience=jcp-agent-spawner";
    let answer = testing::call(service.address(), "POST", "/oauth2/token", &[], form);
    assert_eq!(answer.text(), r#"{"access_token":"air-lane-jcp-agent-spawner","expires_in":3600}"#);
    let answer = testing::call(service.address(), "POST", "/oauth2/token", &[], b"grant_type=refresh_token");
    assert_eq!(answer.text(), r#"{"access_token":"air-lane-token","expires_in":3600}"#);
    let info = testing::get(service.address(), "/oauth2/orgs-user-info");
    assert_eq!(info.text(), r#"{"jwt":"air-lane-orgs-user-info","expires_in":3600}"#);
}

#[test]
fn the_jcp_routes_answer_the_organization_and_the_record_counts_the_discoveries() {
    let service = start_cloud(Organization::CloudWorkspace);
    let address = service.address();
    let options = testing::get(address, "/jcp/user-management/api/ai-access-options");
    assert_eq!(
        (options.status, options.header("Content-Type")),
        (200, Some("application/json; charset=UTF-8"))
    );
    assert_eq!(options.json()["aiAccessOptions"][0]["workspaceId"], "air-lane-workspace");
    let flags = testing::get(address, "/jcp/user-management/api/org-feature-flags?flags=ai-seats");
    let expected_flags = concat!(
        r#"{"flags":{"agentic-core/cloud-tasks":true,"agentic-core/cloud-tasks-personal":true,"#,
        r#""ai-seats":true,"ai-seats-personal":true}}"#
    );
    assert_eq!(flags.text(), expected_flags);
    for (path, body) in [
        (
            "tools",
            r#"{"air":{"status":"allowed","allowCloudAgentsRun":true},"aiAssistant":{"status":"disallowed"}}"#,
        ),
        ("gateways", r#"{"gateways":[]}"#),
        ("agents", r#"{"items":[]}"#),
    ] {
        assert_eq!(
            testing::get(address, &format!("/jcp/ai-management/api/provisioning/v1/{path}")).text(),
            body
        );
    }
    for path in ["acp", "mcp"] {
        let answer = testing::get(address, &format!("/jcp/ai-management/api/provisioning/v1/{path}"));
        assert_eq!((answer.status, answer.raw_body.len()), (404, 0), "{path}");
    }
    let access = testing::get(
        address,
        "/jcp/repo-connections/tokens/state/meta?repositoryUrl=https%3A%2F%2Fgithub.com%2Fo%2Fr.git",
    );
    assert_eq!(
        (access.status, access.text()),
        (
            200,
            r#"{"type":"VALID","provider":"github","username":"air-lane","repositoryPermissions":{"read":true,"write":true}}"#.to_owned()
        )
    );

    let grant = testing::get(address, "/jcp/agent-spawner/hosts/v0/remote-control/availability");
    assert_eq!((grant.status, grant.text()), (200, r#"{"available":false}"#.to_owned()));

    let record = testing::control(address, "GET", "/_lane/cloud/record", "").json();
    assert_eq!(record["discoveries"], 1);
    assert_eq!(record["served"][1], "GET /jcp/user-management/api/org-feature-flags?flags=ai-seats");
    assert_eq!(record["served"].as_array().unwrap().len(), 9);
    assert_eq!(record["served"][8], "GET /jcp/agent-spawner/hosts/v0/remote-control/availability");
    assert_eq!(record["served"][6], "GET /jcp/ai-management/api/provisioning/v1/mcp");
    assert_eq!(
        record["served"][7],
        "GET /jcp/repo-connections/tokens/state/meta?repositoryUrl=https%3A%2F%2Fgithub.com%2Fo%2Fr.git"
    );
    assert_eq!(record["unexpected"], json!([]));

    let none = start_cloud(Organization::None);
    let options = testing::get(none.address(), "/jcp/user-management/api/ai-access-options");
    assert_eq!(options.text(), r#"{"aiAccessOptions":[]}"#);
}

#[test]
fn the_agreement_routes_answer_404_and_the_record_lists_them_as_served() {
    let service = start_cloud(Organization::CloudWorkspace);
    let address = service.address();
    let targets = [
        "/lservice/rpc/obtainAgreement.action?productCode=AI&fileName=aiEuaPlatform&existingVersion=unknown",
        "/lservice/rpc/obtainAgreement.action?productCode=II&fileName=privacy&existingVersion=999.999",
        "/lservice/rpc/fetchData.action?productCode=II",
    ];
    for target in targets {
        let answer = testing::get(address, target);
        assert_eq!((answer.status, answer.raw_body.len()), (404, 0), "{target}");
    }
    let record = testing::control(address, "GET", "/_lane/cloud/record", "").json();
    assert_eq!(
        record,
        json!({ "discoveries": 0, "served": targets.map(|target| format!("GET {target}")), "unexpected": [] })
    );
}

#[test]
fn an_unknown_path_answers_501_and_the_clear_answers_the_record_it_cleared() {
    let service = start_cloud(Organization::None);
    let address = service.address();
    let answer = testing::get(address, "/jcp/ai-management/api/provisioning/v1/unknown?x=1");
    assert_eq!(answer.status, 501);
    assert_eq!(
        answer.text(),
        "the Air lane emulates no /jcp/ai-management/api/provisioning/v1/unknown?x=1"
    );
    testing::call(address, "DELETE", "/jcp-other", &[], b"");
    let cleared = testing::control(address, "DELETE", "/_lane/cloud/record", "").json();
    assert_eq!(
        cleared,
        json!({ "discoveries": 0, "served": [], "unexpected": ["GET /jcp/ai-management/api/provisioning/v1/unknown?x=1", "DELETE /jcp-other"] })
    );
    let record = testing::control(address, "GET", "/_lane/cloud/record", "").json();
    assert_eq!(record, json!({ "discoveries": 0, "served": [], "unexpected": [] }));
}
