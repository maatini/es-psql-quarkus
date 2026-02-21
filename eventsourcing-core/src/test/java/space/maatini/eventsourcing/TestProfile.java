package space.maatini.eventsourcing;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
import java.util.Map;

/**
 * Test profile.
 */
public class TestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
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
