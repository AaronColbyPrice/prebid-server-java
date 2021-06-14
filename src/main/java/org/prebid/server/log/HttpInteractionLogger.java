package org.prebid.server.log;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.model.HttpLogSpec;
import org.prebid.server.settings.model.Account;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class HttpInteractionLogger {

    private static final String HTTP_INTERACTION_LOGGER_NAME = "http-interaction";
    private static final Logger logger = LoggerFactory.getLogger(HTTP_INTERACTION_LOGGER_NAME);

    private final JacksonMapper mapper;

    private final AtomicReference<SpecWithCounter> specWithCounter = new AtomicReference<>();

    public HttpInteractionLogger(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public void setSpec(HttpLogSpec spec) {
        specWithCounter.set(SpecWithCounter.of(spec));
    }

    public void maybeLogOpenrtb2Auction(AuctionContext auctionContext,
                                        RoutingContext routingContext,
                                        int statusCode,
                                        String responseBody) {

        if (interactionSatisfiesSpec(HttpLogSpec.Endpoint.auction, statusCode, auctionContext)) {
            logger.info(
                    "Requested URL: \"{0}\", request body: \"{1}\", response status: \"{2}\", response body: \"{3}\"",
                    routingContext.request().uri(),
                    // This creates string without new line and space symbols;
                    bufferAsString(routingContext.getBody()),
                    statusCode,
                    responseBody);

            incLoggedInteractions();
        }
    }

    private String bufferAsString(Buffer buffer) {
        try {
            return mapper.encode(mapper.mapper().convertValue(buffer, JsonNode.class));
        } catch (EncodeException | IllegalArgumentException e) {
            return buffer.toString();
        }
    }

    public void maybeLogOpenrtb2Amp(AuctionContext auctionContext,
                                    RoutingContext routingContext,
                                    int statusCode,
                                    String responseBody) {

        if (interactionSatisfiesSpec(HttpLogSpec.Endpoint.amp, statusCode, auctionContext)) {
            logger.info(
                    "Requested URL: \"{0}\", response status: \"{1}\", response body: \"{2}\"",
                    routingContext.request().uri(),
                    statusCode,
                    responseBody);

            incLoggedInteractions();
        }
    }

    public boolean interactionSatisfiesSpec(HttpLogSpec.Endpoint requestEndpoint,
                                            int requestStatusCode,
                                            AuctionContext auctionContext) {

        final SpecWithCounter specWithCounter = this.specWithCounter.get();
        if (specWithCounter == null) {
            return false;
        }

        final Account requestAccount = auctionContext != null ? auctionContext.getAccount() : null;
        final String requestAccountId = requestAccount != null ? requestAccount.getId() : null;

        final HttpLogSpec spec = specWithCounter.getSpec();
        final HttpLogSpec.Endpoint endpoint = spec.getEndpoint();
        final Integer statusCode = spec.getStatusCode();
        final String account = spec.getAccount();

        return (endpoint == null || endpoint == requestEndpoint)
                && (statusCode == null || statusCode == requestStatusCode)
                && (account == null || account.equals(requestAccountId));
    }

    private void incLoggedInteractions() {
        final SpecWithCounter specWithCounter = this.specWithCounter.get();
        if (specWithCounter != null
                && specWithCounter.getLoggedInteractions().incrementAndGet() >= specWithCounter.getSpec().getLimit()) {
            this.specWithCounter.set(null);
        }
    }

    @Value(staticConstructor = "of")
    private static class SpecWithCounter {

        HttpLogSpec spec;

        AtomicLong loggedInteractions = new AtomicLong(0);
    }
}
