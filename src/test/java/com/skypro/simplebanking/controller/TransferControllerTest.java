package com.skypro.simplebanking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skypro.simplebanking.SimpleBankingApplication;
import com.skypro.simplebanking.dto.TransferRequest;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import com.skypro.simplebanking.utils.TransferRequestUtils;
import com.skypro.simplebanking.utils.UserUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@SpringBootTest(classes = SimpleBankingApplication.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
public class TransferControllerTest {
    @Autowired
    private DataSource dataSource;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:alpine");
    private List<User> users;
    private User fromUser, toUser;
    private List<Account> accounts;
    private final static int TOTAL_NUMBER_OF_USERS = 50;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void beforeEach(){
        this.accounts = new ArrayList<>();
        this.users = UserUtils.createUniqueUsers(TOTAL_NUMBER_OF_USERS,passwordEncoder);
        userRepository.saveAll(users);

        for (User currentUser : users){
            accounts.addAll(currentUser.getAccounts());
        }
        accountRepository.saveAll(accounts);

        int[] twoUniqueExistedIndexes = TransferRequestUtils.selectTwoUniqueExistedIndexesFromUsersCollection(TOTAL_NUMBER_OF_USERS);
        fromUser = users.get(twoUniqueExistedIndexes[0]);
        toUser = users.get(twoUniqueExistedIndexes[1]);

        boolean isAdmin = false;
        SecurityContextHolder.getContext().setAuthentication(UserUtils.createAuthenticationTokenForUser(fromUser,isAdmin));
    }

    @AfterEach
    void afterEach(){
        userRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @DisplayName("Проверка работоспособности соединения с БД PostgreSQL.")
    @Test
    void testPostgresql() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn).isNotNull();
        }
    }

    @DisplayName("Успешный перевод средств пользователем с ролью USER.")
    @Test
    void transfer_postValidateRequest_withUserRole_thenTransferCreated() throws Exception {
        String transferRequestJson = objectMapper.writeValueAsString(TransferRequestUtils.createValidateRequest(accounts,fromUser,toUser));

        mockMvc.perform(post("/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferRequestJson))
                .andDo(print())
                .andExpect(status().isOk());

    }

    @DisplayName("Заблокированный перевод средств пользователем с ролью ADMIN.")
    @Test
    void transfer_postValidateRequest_withAdminRole_thenTransferNotCreated() throws Exception {
        String transferRequestJson = objectMapper.writeValueAsString(TransferRequestUtils.createValidateRequest(accounts,fromUser,toUser));

        boolean isAdmin = true;
        SecurityContextHolder.getContext().setAuthentication(UserUtils.createAuthenticationTokenForUser(fromUser,isAdmin));

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson))
                .andDo(print())
                .andExpect(status().isForbidden());

    }

    @DisplayName("Заблокированный перевод средств из-за неправильного типа валюты для пользователя с ролью USER.")
    @Test
    void transfer_postRequestWithWrongCurrencyType_withUserRole_thenTransferNotCreated() throws Exception {
        TransferRequest transferRequestWithWrongCurrencyType = TransferRequestUtils.createRequestWithWrongCurrencyType(fromUser,toUser);
        String transferRequestJson = objectMapper.writeValueAsString(transferRequestWithWrongCurrencyType);

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Заблокированный перевод средств из-за отрицательной величины перевода для пользователя с ролью USER.")
    @Test
    void transfer_postRequestWithNegativeAmount_withUserRole_thenTransferNotCreated() throws Exception {
        TransferRequest transferRequestWithNegativeAmount = TransferRequestUtils.createRequestWithNegativeAmount(fromUser,toUser);
        String transferRequestJson = objectMapper.writeValueAsString(transferRequestWithNegativeAmount);

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Заблокированный перевод средств из-за превышения лимита перевода для пользователя с ролью USER.")
    @Test
    void transfer_postRequestWithWrongAmount_withUserRole_thenTransferNotCreated() throws Exception {
        TransferRequest transferRequestWithNegativeAmount = TransferRequestUtils.createRequestWithWrongAmount(accounts,fromUser,toUser);
        String transferRequestJson = objectMapper.writeValueAsString(transferRequestWithNegativeAmount);

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Заблокированный перевод средств из-за неправильного ID аккаунта для пользователя с ролью USER.")
    @Test
    void transfer_postRequestWithWrongAccountId_withUserRole_thenTransferNotCreated() throws Exception {
        TransferRequest transferRequestWithWrongAccountId = TransferRequestUtils.createRequestWithWrongAccountId(accounts,fromUser,toUser);
        String transferRequestJson = objectMapper.writeValueAsString(transferRequestWithWrongAccountId);

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}
