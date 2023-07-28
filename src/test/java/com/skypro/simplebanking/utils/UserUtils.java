package com.skypro.simplebanking.utils;

import com.github.javafaker.Faker;
import com.skypro.simplebanking.dto.BankingUserDetails;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.AccountCurrency;
import com.skypro.simplebanking.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

public class UserUtils {

    public static List<User> createUniqueUsers(int maxUsers, PasswordEncoder passwordEncoder){
        List<User> users = new ArrayList<>();
        final Faker faker = new Faker(new Locale("ru-RU"));

        List<String> uniqueUsernames = Stream.generate(()->faker.name().username())
                .distinct()
                .limit(maxUsers)
                .toList();

        for (int userNumber = 0;userNumber < maxUsers;userNumber++){
            User currentUser = new User();
            long randomAmount = new Random().nextLong(1, 1_000_000L);

            currentUser.setUsername(uniqueUsernames.get(userNumber));
            currentUser.setPassword(passwordEncoder.encode(faker.internet().password()));
            currentUser.setAccounts(new ArrayList<>());

            for (AccountCurrency currency : AccountCurrency.values()) {
                Account account = new Account();
                account.setUser(currentUser);
                account.setAccountCurrency(currency);
                account.setAmount(randomAmount);
                currentUser.getAccounts().add(account);
            }

            users.add(currentUser);
        }
        return users;
    }

    public static User createSingleUniqueUser(List<User> existedUsers){
        User uniqueUser = new User();
        final Faker faker = new Faker(new Locale("ru-RU"));
        List<String> existedUserNames = existedUsers.stream().map(User::getUsername).toList();
        String uniqueUsername;

        do {
            uniqueUsername = faker.name().username();
        } while (existedUserNames.contains(uniqueUsername));

        uniqueUser.setUsername(uniqueUsername);
        uniqueUser.setPassword(faker.internet().password());

        return uniqueUser;
    }

    public static Authentication createAuthenticationTokenForRandomUser(List<User> users, int maxUsers, boolean isAdmin){
        int randomUserNumber = new Random().nextInt(0,maxUsers);
        return createAuthenticationTokenForUser(users.get(randomUserNumber),isAdmin);
    }

    public static Authentication createAuthenticationTokenForUser(User user, boolean isAdmin){
        BankingUserDetails bankingUserDetails = new BankingUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                isAdmin
        );
        return new UsernamePasswordAuthenticationToken(bankingUserDetails,null,bankingUserDetails.getAuthorities());
    }
}
