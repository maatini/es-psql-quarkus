package space.maatini.eventsourcing;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that disables OIDC authentication for integration tests.
 * Security is simulated via @TestSecurity annotations on test classes.
 */
public class TestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.oidc.enabled", "false",
                "quarkus.oidc.tenant-enabled", "false",
                "quarkus.http.auth.permission.permit-all.paths", "/*",
                "quarkus.http.auth.permission.permit-all.policy", "permit",
                "quarkus.datasource.password", "${PGPASSWORD:devpassword}"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
