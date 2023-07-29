package com.skypro.simplebanking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skypro.simplebanking.SimpleBankingApplication;
import com.skypro.simplebanking.dto.BankingUserDetails;
import com.skypro.simplebanking.dto.ListUserDTO;
import com.skypro.simplebanking.dto.UserDTO;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import com.skypro.simplebanking.utils.UserUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
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
public class UserControllerTest {
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
    private final static int TOTAL_NUMBER_OF_USERS = 50;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void beforeEach(){
        List<Account> accounts = new ArrayList<>();
        this.users = UserUtils.createUniqueUsers(TOTAL_NUMBER_OF_USERS,passwordEncoder);
        userRepository.saveAll(users);
        for (User currentUser : users){
            accounts.addAll(currentUser.getAccounts());
        }
        accountRepository.saveAll(accounts);
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

    @DisplayName("Неуспешная попытка создания нового пользователя пользователем с ролью USER.")
    @Test
    @WithMockUser(roles = "USER")
    void createUser_withUserRole_thenAccessForbidden() throws Exception {
        User newUser = UserUtils.createSingleUniqueUser(users);
        String json = objectMapper.writeValueAsString(newUser);
        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                )
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @DisplayName("Создание нового пользователя пользователем с ролью ADMIN.")
    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_withAdminRole_thenUserCreated() throws Exception {
        long usersBefore = userRepository.count();
        int numberOfCreatedUsers = 1;
        User newUser = UserUtils.createSingleUniqueUser(users);
        String json = objectMapper.writeValueAsString(newUser);

        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                )
                .andDo(print())
                .andExpect(status().isOk());

        long usersAfter = userRepository.count();
        assertEquals(usersBefore,usersAfter-numberOfCreatedUsers);
    }

    @DisplayName("Неуспешная попытка создания существующего пользователя пользователем с ролью ADMIN.")
    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_existedUser_withAdminRole_thenUserNotCreated() throws Exception {
        long usersBefore = userRepository.count();
        int randomUserNumber = new Random().nextInt(0,TOTAL_NUMBER_OF_USERS);
        User existedUser = users.get(randomUserNumber);

        String json = "{\"username\":\""+existedUser.getUsername()+"\",\"password\":\""+existedUser.getPassword()+"\"}";

        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                )
                .andDo(print())
                .andExpect(status().isBadRequest());

        long usersAfter = userRepository.count();

        assertEquals(usersBefore,usersAfter);
    }

    @DisplayName("Создание нового пользователя пользователем с ролью JEDI по неверному эндпоинту.")
    @Test
    @WithMockUser(roles = "JEDI")
    void createUser_withJediRole_thenUserCreated() throws Exception {
        //Как я понимаю, в приложении не совсем корректно настроены права.
        //В конфигурации устанавливаются права для "/user/",
        //а в контроллере у метода стоит эндпоинт "/user"
        //из-за аннотации для post метода: @PostMapping != @PostMapping("/")
        //По текущему эндпоинту с любой (при помощи @WithMockUser) ролью можно создавать пользователей,
        //либо с ролью USER, если использовать только зашитые роли из BankingUserDetails.
        long usersBefore = userRepository.count();
        int numberOfCreatedUsers = 1;
        User newUser = UserUtils.createSingleUniqueUser(users);
        String json = objectMapper.writeValueAsString(newUser);
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                )
                .andDo(print())
                .andExpect(status().isOk());
        long usersAfter = userRepository.count();
        assertEquals(usersBefore,usersAfter-numberOfCreatedUsers);
    }

    @DisplayName("Получение списка пользователей пользователем с ролью USER.")
    @Test
    @WithMockUser
    void getAllUsers_withUserRole_thenUsersList() throws Exception {
        List<ListUserDTO> listUserDTO = users.stream().map(ListUserDTO::from).toList();
        String expectedJson = objectMapper.writeValueAsString(listUserDTO);

        mockMvc.perform(get("/user/list"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(content().json(expectedJson));
    }

    @DisplayName("Неуспешная попытка получения списка пользователей пользователем с ролью ADMIN.")
    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_withAdminRole_thenAccessForbidden() throws Exception {
        mockMvc.perform(get("/user/list"))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @DisplayName("Получение пользователем информации о себе пользователем с ролью USER.")
    @Test
    void getMyProfile_withUserRole_thenUserDetails() throws Exception {
        boolean isAdmin = false;
        Authentication authentication = UserUtils.createAuthenticationTokenForRandomUser(users,TOTAL_NUMBER_OF_USERS,isAdmin);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User currentUser = users.stream().filter(u->u.getId()==((BankingUserDetails) authentication.getPrincipal()).getId()).findFirst().orElseThrow();

        String expectedJson = objectMapper.writeValueAsString(UserDTO.from(currentUser));

        mockMvc.perform(get("/user/me"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson));
    }

    @DisplayName("Неуспешная попытка получения пользователем информации о себе пользователем с ролью ADMIN.")
    @Test
    void getMyProfile_withAdminRole_thenAccessForbidden() throws Exception {
        boolean isAdmin = true;
        SecurityContextHolder.getContext().setAuthentication(UserUtils.createAuthenticationTokenForRandomUser(users,TOTAL_NUMBER_OF_USERS,isAdmin));

        mockMvc.perform(get("/user/me"))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$").doesNotExist());
    }
}
