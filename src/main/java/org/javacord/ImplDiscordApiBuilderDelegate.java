package org.javacord;

import com.fasterxml.jackson.databind.JsonNode;
import org.javacord.util.gateway.DiscordWebSocketAdapter;
import org.javacord.util.logging.LoggerUtil;
import org.javacord.util.rest.RestEndpoint;
import org.javacord.util.rest.RestMethod;
import org.javacord.util.rest.RestRequest;
import org.javacord.util.rest.RestRequestResult;
import org.slf4j.Logger;
import org.slf4j.MDC.MDCCloseable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The implementation of {@link DiscordApiBuilderDelegate}.
 */
public class ImplDiscordApiBuilderDelegate implements DiscordApiBuilderDelegate {

    /**
     * The logger of this class.
     */
    private static final Logger logger = LoggerUtil.getLogger(ImplDiscordApiBuilderDelegate.class);

    /**
     * The token which is used to login. Must be present in order to login!
     */
    private String token = null;

    /**
     * The account type of the account with the given token.
     */
    private AccountType accountType = AccountType.BOT;

    /**
     * The current shard starting with <code>0</code>.
     */
    private int currentShard = 0;

    /**
     * The total amount of shards.
     * If the total amount is <code>1</code>, sharding will be disabled.
     */
    private int totalShards = 1;

    /**
     * Whether Javacord should wait for all servers to become available on startup or not.
     */
    private boolean waitForServersOnStartup = true;

    @Override
    public CompletableFuture<DiscordApi> login() {
        logger.debug("Creating shard {} of {}", currentShard + 1, totalShards);
        CompletableFuture<DiscordApi> future = new CompletableFuture<>();
        if (token == null) {
            future.completeExceptionally(new IllegalArgumentException("You cannot login without a token!"));
            return future;
        }
        try (MDCCloseable mdcCloseable = LoggerUtil.putCloseableToMdc("shard", Integer.toString(currentShard))){
            new ImplDiscordApi(accountType, token, currentShard, totalShards, waitForServersOnStartup, future);
        }
        return future;
    }

    @Override
    public Collection<CompletableFuture<DiscordApi>> loginShards(int... shards) {
        Objects.requireNonNull(shards);
        if (shards.length == 0) {
            return Collections.emptyList();
        }
        if (Arrays.stream(shards).distinct().count() != shards.length) {
            throw new IllegalArgumentException("shards cannot be started multiple times!");
        }
        if (Arrays.stream(shards).max().orElseThrow(AssertionError::new) >= getTotalShards()) {
            throw new IllegalArgumentException("shard cannot be greater or equal than totalShards!");
        }
        if (Arrays.stream(shards).min().orElseThrow(AssertionError::new) < 0) {
            throw new IllegalArgumentException("shard cannot be less than 0!");
        }

        if (shards.length == getTotalShards()) {
            logger.info("Creating {} {}", getTotalShards(), (getTotalShards() == 1) ? "shard" : "shards");
        } else {
            logger.info("Creating {} out of {} shards ({})", shards.length, getTotalShards(), shards);
        }

        Collection<CompletableFuture<DiscordApi>> result = new ArrayList<>(shards.length);
        int currentShard = getCurrentShard();
        for (int shard : shards) {
            if (currentShard != 0) {
                CompletableFuture<DiscordApi> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalArgumentException(
                        "You cannot use loginShards or loginAllShards after setting the current shard!"));
                result.add(future);
                continue;
            }
            setCurrentShard(shard);
            result.add(login());
        }
        setCurrentShard(currentShard);
        return result;
    }

    @Override
    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public void setAccountType(AccountType type) {
        this.accountType = type;
    }

    @Override
    public void setTotalShards(int totalShards) {
        if (currentShard >= totalShards) {
            throw new IllegalArgumentException("currentShard cannot be greater or equal than totalShards!");
        }
        if (totalShards < 1) {
            throw new IllegalArgumentException("totalShards cannot be less than 1!");
        }
        this.totalShards = totalShards;
    }

    @Override
    public void setCurrentShard(int currentShard) {
        if (currentShard >= totalShards) {
            throw new IllegalArgumentException("currentShard cannot be greater or equal than totalShards!");
        }
        if (currentShard < 0) {
            throw new IllegalArgumentException("currentShard cannot be less than 0!");
        }
        this.currentShard = currentShard;
    }

    @Override
    public void setWaitForServersOnStartup(boolean waitForServersOnStartup) {
        this.waitForServersOnStartup = waitForServersOnStartup;
    }

    @Override
    public CompletableFuture<Void> setRecommendedTotalShards() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (token == null) {
            future.completeExceptionally(new IllegalArgumentException("You cannot request the recommended total shards without a token!"));
            return future;
        }

        RestRequest<JsonNode> botGatewayRequest = new RestRequest<>(new ImplDiscordApi(token), RestMethod.GET, RestEndpoint.GATEWAY_BOT);
        botGatewayRequest
                .execute(RestRequestResult::getJsonBody)
                .thenAccept(resultJson -> {
                    DiscordWebSocketAdapter.setGateway(resultJson.get("url").asText());
                    setTotalShards(resultJson.get("shards").asInt());
                    future.complete(null);
                })
                .exceptionally(t -> {
                    future.completeExceptionally(t);
                    return null;
                })
                .whenComplete((nothing, throwable) -> botGatewayRequest.getApi().disconnect());

        return future;
    }

    @Override
    public int getTotalShards() {
        return totalShards;
    }

    @Override
    public int getCurrentShard() {
        return currentShard;
    }
}