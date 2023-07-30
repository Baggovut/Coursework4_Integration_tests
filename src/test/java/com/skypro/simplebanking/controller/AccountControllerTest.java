package com.skypro.simplebanking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skypro.simplebanking.SimpleBankingApplication;
import com.skypro.simplebanking.dto.AccountDTO;
import com.skypro.simplebanking.dto.BalanceChangeRequest;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.AccountCurrency;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import com.skypro.simplebanking.utils.AccountUtils;
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
public class AccountControllerTest {
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
    private User fromUser;
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

        int randomUser = new Random().nextInt(0,TOTAL_NUMBER_OF_USERS);
        fromUser = users.get(randomUser);

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

    @DisplayName("Запрос информации об аккаунте пользователем с ролью USER.")
    @Test
    void getUserAccount_getRequest_withUserRole_thenJsonVariable() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        String expectedJson = objectMapper.writeValueAsString(AccountDTO.from(account));

        mockMvc.perform(get("/account/{id}",id))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson));
    }

    @DisplayName("Запрос информации об аккаунте пользователем с ролью ADMIN.")
    @Test
    void getUserAccount_getRequest_withAdminRole_thenAccessForbidden() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();

        boolean isAdmin = true;
        SecurityContextHolder.getContext().setAuthentication(UserUtils.createAuthenticationTokenForUser(fromUser,isAdmin));

        mockMvc.perform(get("/account/{id}",id))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @DisplayName("Пополнение собственного счёта пользователем с ролью USER.")
    @Test
    void depositToAccount_postRequest_withUserRole_thenDepositReplenished() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        long validDepositAmount = new Random().nextLong(1,Long.MAX_VALUE);
        AccountDTO expectedAccount = new AccountDTO(
                account.getId(),
                (account.getAmount() + validDepositAmount),
                account.getAccountCurrency()
        );
        String expectedJson = objectMapper.writeValueAsString(expectedAccount);

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(validDepositAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        mockMvc.perform(post("/account/deposit/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson));
    }

    @DisplayName("Заблокированное пополнение собственного счёта пользователем с ролью ADMIN.")
    @Test
    void depositToAccount_postRequest_withAdminRole_thenDepositNotReplenished() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        long validDepositAmount = new Random().nextLong(1,Long.MAX_VALUE);

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(validDepositAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        boolean isAdmin = true;
        SecurityContextHolder.getContext().setAuthentication(UserUtils.createAuthenticationTokenForUser(fromUser,isAdmin));

        mockMvc.perform(post("/account/deposit/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @DisplayName("Заблокированное пополнение депозита из-за отрицательной величины пополнения пользователем с ролью USER.")
    @Test
    void depositToAccount_postRequestWithNegativeAmount_withUserRole_thenDepositNotReplenished() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        long negativeDepositAmount = -1000;

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(negativeDepositAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        mockMvc.perform(post("/account/deposit/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Amount should be more than 0"));
    }

    @DisplayName("Заблокированное пополнение депозита из-за неправильного ID аккаунта пользователем с ролью USER.")
    @Test
    void depositToAccount_postRequestWithWrongAccountId_withUserRole_thenDepositNotReplenished() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long wrongId = account.getId()+(new Random().nextLong(AccountCurrency.values().length,Long.MAX_VALUE));
        long depositAmount = new Random().nextLong(1,Long.MAX_VALUE);

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(depositAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        mockMvc.perform(post("/account/deposit/{id}",wrongId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @DisplayName("Снятие средств с собственного счёта пользователем с ролью USER.")
    @Test
    void withdrawFromAccount_postRequest_withUserRole_thenDepositWithdrawn() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        long validWithdrawalAmount = account.getAmount()/100;
        AccountDTO expectedAccount = new AccountDTO(
                account.getId(),
                (account.getAmount() - validWithdrawalAmount),
                account.getAccountCurrency()
        );
        String expectedJson = objectMapper.writeValueAsString(expectedAccount);

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(validWithdrawalAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        mockMvc.perform(post("/account/withdraw/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson));
    }

    @DisplayName("Снятие средств с собственного счёта пользователем с ролью ADMIN.")
    @Test
    void withdrawFromAccount_postRequest_withAdminRole_thenDepositNotWithdrawn() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        long validWithdrawalAmount = account.getAmount()/100;

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(validWithdrawalAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        boolean isAdmin = true;
        SecurityContextHolder.getContext().setAuthentication(UserUtils.createAuthenticationTokenForUser(fromUser,isAdmin));

        mockMvc.perform(post("/account/withdraw/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @DisplayName("Заблокированное снятие средств со счёта из-за отрицательной величины пополнения для пользователя с ролью USER.")
    @Test
    void withdrawFromAccount_postRequestWithNegativeAmount_withUserRole_thenDepositNotWithdrawn() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        long negativeWithdrawalAmount = -1000;

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(negativeWithdrawalAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        mockMvc.perform(post("/account/withdraw/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Amount should be more than 0"));
    }

    @DisplayName("Заблокированное снятие средств со счёта из-за превышения лимита депозита для пользователя с ролью USER.")
    @Test
    void withdrawFromAccount_postRequestWithWrongAmount_withUserRole_thenDepositNotWithdrawn() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId();
        long invalidWithdrawalAmount = account.getAmount()*100;

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(invalidWithdrawalAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        mockMvc.perform(post("/account/withdraw/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$")
                        .value("Cannot withdraw "+invalidWithdrawalAmount+" "+account.getAccountCurrency())
                );
    }

    @DisplayName("Заблокированное снятие средств со счёта из-за неправильного ID аккаунта для пользователя с ролью USER.")
    @Test
    void withdrawFromAccount_postRequestWithWrongAccountId_withUserRole_thenDepositNotWithdrawn() throws Exception {
        Account account = AccountUtils.getAccount(fromUser,accounts);
        long id = account.getId()+(new Random().nextLong(AccountCurrency.values().length,Long.MAX_VALUE));
        long validWithdrawalAmount = account.getAmount()/100;

        BalanceChangeRequest balanceChangeRequest = new BalanceChangeRequest();
        balanceChangeRequest.setAmount(validWithdrawalAmount);

        String requestJson = objectMapper.writeValueAsString(balanceChangeRequest);

        mockMvc.perform(post("/account/withdraw/{id}",id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").doesNotExist());
    }
}
