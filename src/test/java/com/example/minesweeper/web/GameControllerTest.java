package com.example.minesweeper.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class GameControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void playThroughApi() throws Exception {
        String body = mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"INTERMEDIATE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rows").value(16))
                .andExpect(jsonPath("$.mines").value(40))
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(post("/api/games/{id}/reveal", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"row\":5,\"col\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAYING"));

        mvc.perform(get("/api/games/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.board.length()").value(16));
    }

    @Test
    void customGameAndValidation() throws Exception {
        mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":10,\"cols\":12,\"mines\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cols").value(12));

        mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":10,\"cols\":12,\"mines\":200}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownGameIs404() throws Exception {
        mvc.perform(get("/api/games/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
