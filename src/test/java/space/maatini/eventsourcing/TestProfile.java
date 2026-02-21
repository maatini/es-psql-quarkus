package space.maatini.eventsourcing;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that disables OIDC (uses mock security instead)
 * and configures the outbox scheduler to not interfere with tests.
 */
public class TestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Disable real OIDC — we use @TestSecurity for mocking
            "quarkus.oidc.enabled", "false",
            // Disable outbox scheduler during tests to avoid side-effects
            "quarkus.scheduler.enabled", "false"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
