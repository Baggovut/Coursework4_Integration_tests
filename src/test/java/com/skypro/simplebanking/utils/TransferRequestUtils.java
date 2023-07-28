package com.skypro.simplebanking.utils;

import com.skypro.simplebanking.dto.TransferRequest;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.AccountCurrency;
import com.skypro.simplebanking.entity.User;

import java.util.List;
import java.util.Random;

public class TransferRequestUtils {
    public static int[] selectTwoUniqueExistedIndexesFromUsersCollection(int maxUsers){
        int[] twoUniqueIndexes = new int[2];
        int randomFromUserNumber = new Random().nextInt(0,maxUsers);
        int randomToUserNumber;
        do {
            randomToUserNumber = new Random().nextInt(0,maxUsers);
        } while (randomFromUserNumber == randomToUserNumber);

        twoUniqueIndexes[0] = randomFromUserNumber;
        twoUniqueIndexes[1] = randomToUserNumber;

        return twoUniqueIndexes;
    }
    public static TransferRequest createValidateRequest(List<Account> accounts, User fromUser, User toUser){
        int randomAccountCurrency = new Random().nextInt(0, AccountCurrency.values().length);

        long fromAccountId = fromUser.getAccounts().stream().map(Account::getId).toList().get(randomAccountCurrency);
        Account fromAccount = accounts.stream().filter(a->a.getId() == fromAccountId).findFirst().orElseThrow();
        long validTransferAmount = fromAccount.getAmount()/100;

        return createRequest(fromUser,toUser,randomAccountCurrency,randomAccountCurrency,validTransferAmount);
    }

    public static TransferRequest createRequestWithWrongCurrencyType(User fromUser, User toUser){
        int fromUserCurrency = new Random().nextInt(0,AccountCurrency.values().length);
        int toUserCurrency;

        do {
            toUserCurrency = new Random().nextInt(0,AccountCurrency.values().length);
        } while (fromUserCurrency == toUserCurrency);

        long validTransferAmount = 1L;

        return createRequest(fromUser,toUser,fromUserCurrency,toUserCurrency,validTransferAmount);
    }

    public static TransferRequest createRequestWithNegativeAmount(User fromUser, User toUser){
        int randomAccountCurrency = new Random().nextInt(0, AccountCurrency.values().length);
        long negativeAmount = -1000;

        return createRequest(fromUser,toUser,randomAccountCurrency,randomAccountCurrency,negativeAmount);
    }

    public static TransferRequest createRequestWithWrongAmount(List<Account> accounts, User fromUser, User toUser){
        int randomAccountCurrency = new Random().nextInt(0, AccountCurrency.values().length);

        long fromAccountId = fromUser.getAccounts().stream().map(Account::getId).toList().get(randomAccountCurrency);
        Account fromAccount = accounts.stream().filter(a->a.getId() == fromAccountId).findFirst().orElseThrow();
        long invalidTransferAmount = fromAccount.getAmount()*100;

        return createRequest(fromUser,toUser,randomAccountCurrency,randomAccountCurrency,invalidTransferAmount);
    }

    public static TransferRequest createRequestWithWrongAccountId(List<Account> accounts, User fromUser, User toUser){
        int randomAccountCurrency = new Random().nextInt(0, AccountCurrency.values().length);

        long fromAccountId = fromUser.getAccounts().stream().map(Account::getId).toList().get(randomAccountCurrency);
        Account fromAccount = accounts.stream().filter(a->a.getId() == fromAccountId).findFirst().orElseThrow();
        fromAccount.setId(fromAccountId+(new Random().nextLong(AccountCurrency.values().length,Long.MAX_VALUE)));
        long validTransferAmount = fromAccount.getAmount()/100;

        return createRequest(fromUser,toUser,randomAccountCurrency,randomAccountCurrency,validTransferAmount);
    }

    private static TransferRequest createRequest(User fromUser,
                                                 User toUser,
                                                 int fromUserCurrency,
                                                 int toUserCurrency,
                                                 long transferAmount
    ){
        long fromAccountId = fromUser.getAccounts().stream().map(Account::getId).toList().get(fromUserCurrency);
        long toAccountId = toUser.getAccounts().stream().map(Account::getId).toList().get(toUserCurrency);

        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setFromAccountId(fromAccountId);
        transferRequest.setAmount(transferAmount);
        transferRequest.setToUserId(toUser.getId());
        transferRequest.setToAccountId(toAccountId);

        return transferRequest;
    }
}
