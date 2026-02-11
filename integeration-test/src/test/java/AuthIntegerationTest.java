import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;


//import static io.restassured.path.json.JsonPath.given;
import static org.hamcrest.Matchers.notNullValue;

public class AuthIntegerationTest {
    @BeforeAll
    static void setUp(){
        RestAssured.baseURI = "http://localhost:4004";
    }

    @Test
    public void shouldReturnOKWithValidToken(){
        //1. arrange
        //2. act
        //3. assert
        String loginPayload = """
                {
                    "email": "testuser@test.com",
                    "password" : "password123"
                }
                """;


        Response response = RestAssured.given()
                .contentType("application/json")
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract()
                .response();

        System.out.println("Generated token: " + response.jsonPath().getString("token"));
    }

    @Test
    public void shouldReturnUnauthorizedOnInvalidToken(){
        //1. arrange
        //2. act
        //3. assert
        String loginPayload = """
                {
                    "email": "testuser@test.com",
                    "password" : "wrongword123"
                }
                """;


            given()
                .contentType("application/json")
                .body(loginPayload)
                    .when()
                .post("/auth/login")
                .then()
                .statusCode(401);

        System.out.println("this will succeed");
    }


}
