package com.skypro.simplebanking.utils;

import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.AccountCurrency;
import com.skypro.simplebanking.entity.User;

import java.util.List;
import java.util.Random;

public class AccountUtils {
    public static Account getAccount(User fromUser, List<Account> accounts){
        int randomAccountCurrency = new Random().nextInt(0, AccountCurrency.values().length);
        long fromAccountId = fromUser.getAccounts().stream().map(Account::getId).toList().get(randomAccountCurrency);

        return accounts.stream().filter(a-> a.getId() == fromAccountId).findFirst().orElseThrow();
    }
}
