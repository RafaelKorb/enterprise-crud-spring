package com.enterprise.crud.infrastructure.entrypoints.rest;

import com.enterprise.crud.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** End to end through HTTP, use cases and PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountApiIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM account");
    }

    @Test
    void accountLifecycle() throws Exception {
        String id = open("52998224725");

        assertThat(post("/api/v1/accounts/" + id + "/credits", "{\"amount\": 100.00}")).hasStatusOk();
        assertThat(post("/api/v1/accounts/" + id + "/debits", "{\"amount\": 30.25}"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.balance", balance -> balance.assertThat().isEqualTo(69.75))
                .hasPathSatisfying("$.version", version -> version.assertThat().isEqualTo(2));

        assertThat(post("/api/v1/accounts/" + id + "/debits", "{\"amount\": 100.00}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(post("/api/v1/accounts/" + id + "/debits", "{\"amount\": 0.001}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        assertThat(put("/api/v1/accounts/" + id + "/status", "{\"status\": \"BLOCKED\"}"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo("BLOCKED"));
        assertThat(post("/api/v1/accounts/" + id + "/credits", "{\"amount\": 1.00}")).hasStatus(HttpStatus.CONFLICT);

        assertThat(mvc.get().uri("/api/v1/accounts/{id}", id))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.balance", balance -> balance.assertThat().isEqualTo(69.75))
                .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo("BLOCKED"));
    }

    @Test
    void duplicateDocumentIsConflict() throws Exception {
        open("52998224725");

        assertThat(post("/api/v1/accounts", "{\"documentNumber\": \"52998224725\"}")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void invalidDocumentIsUnprocessable() {
        assertThat(post("/api/v1/accounts", "{\"documentNumber\": \"123\"}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void unknownAccountIsNotFound() {
        assertThat(mvc.get().uri("/api/v1/accounts/0198a3c0-0000-7000-8000-000000000099"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void cursorWalksEveryAccountExactlyOnce() throws Exception {
        List<String> opened = List.of(open("52998224725"), open("11222333000181"), open("39053344705"));

        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            String uri = cursor == null ? "/api/v1/accounts?limit=2" : "/api/v1/accounts?limit=2&cursor=" + cursor;
            String body = mvc.get().uri(uri).exchange().getResponse().getContentAsString();
            seen.addAll(JsonPath.read(body, "$.items[*].id"));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);

        assertThat(seen).hasSize(3).containsExactlyInAnyOrderElementsOf(opened);
    }

    private String open(String documentNumber) throws Exception {
        MvcTestResult result = post("/api/v1/accounts", "{\"documentNumber\": \"%s\"}".formatted(documentNumber)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockMvcTester.MockMvcRequestBuilder put(String uri, String json) {
        return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
