package space.maatini.eventsourcing.example.vertreter.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
@io.quarkus.test.security.TestSecurity(user = "test", roles = {"user", "admin"})
class VertreterCommandResourceTest {

    private static final String CMD_PATH = "/commands/vertreter";

    @BeforeEach
    void cleanup() {
        given().post("/test-support/wipe").then().statusCode(200);
    }

    @Test
    @DisplayName("Create command succeeds and emits event")
    void createSuccess() {
        String id = UUID.randomUUID().toString();
        
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "name": "Command Test",
                            "email": "cmd@test.com"
                        }
                        """.formatted(id))
                .when()
                .post(CMD_PATH)
                .then()
                .statusCode(201);
                
        // Test idempotency/invariant: executing it again throws exception
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "name": "Command Test",
                            "email": "cmd@test.com"
                        }
                        """.formatted(id))
                .when()
                .post(CMD_PATH)
                .then()
                .statusCode(400)
                .body(equalTo("Vertreter was already created"));
    }

    @Test
    @DisplayName("Update command succeeds")
    void updateSuccess() {
        String id = UUID.randomUUID().toString();
        
        // 1. Create
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "name": "Cmd Create"
                        }
                        """.formatted(id))
                .when()
                .post(CMD_PATH)
                .then()
                .statusCode(201);

        // 2. Update
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "name": "Cmd Update",
                            "email": "updated@test.com"
                        }
                        """.formatted(id))
                .when()
                .put(CMD_PATH + "/" + id)
                .then()
                .statusCode(200);
    }
    
    @Test
    @DisplayName("Update without create fails")
    void updateFailsWithoutCreate() {
        String id = UUID.randomUUID().toString();
        
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "name": "Cmd Update"
                        }
                        """.formatted(id))
                .when()
                .put(CMD_PATH + "/" + id)
                .then()
                .statusCode(400)
                .body(equalTo("Vertreter does not exist or was not created yet"));
    }
}
