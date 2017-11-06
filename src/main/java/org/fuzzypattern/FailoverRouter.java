package org.fuzzypattern;

import org.apache.camel.AsyncCallback;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.processor.loadbalancer.LoadBalancerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@SpringBootApplication
public class FailoverRouter extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(FailoverRouter.class);

    public static void main(String[] args) {
        SpringApplication.run(FailoverRouter.class, args);
    }

    @Value("${peers}")
    private String peers;

    @Value("${balancer.port}")
    private String serverPort;

    private final HealthEndpoint health;

    @EndpointInject
    private ProducerTemplate sender;

    @Autowired
    public FailoverRouter(HealthEndpoint health) {
        this.health = health;
    }

    @Override
    public void configure() {
        from("jetty:http://0.0.0.0:" + serverPort + "?matchOnUriPrefix=true&bridgeEndpoint=true&continuationTimeout=10000")
                .streamCaching()
                .to("log:org.fuzzypattern.Failover?level=INFO&groupInterval=10000&groupDelay=0&groupActiveOnly=true")
                .loadBalance(getLBSupportForEndpoints());

        from("timer:status?period=30s")
                .bean(health, "invoke")
                .log("Health is ${body}");
    }

    private LoadBalancerSupport getLBSupportForEndpoints() {
        return new LoadBalancerSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                Collection<String> endpoints = getEndpoints();
                for (String endpoint : endpoints) {
                    try {
                        Exchange ex = sender.send(endpoint, exchange);
                        if (ex.getException(HttpOperationFailedException.class) != null) {
                            HttpOperationFailedException opFailed = ex.getException(HttpOperationFailedException.class);
                            if (opFailed.getStatusCode() == 500) { // propagate possible internal server error
                                HttpOperationFailedException exception = ex.getException(HttpOperationFailedException.class);
                                exchange.getOut().setBody(exception.getResponseBody());
                                exchange.getOut().getHeaders().putAll(new HashMap<>(exception.getResponseHeaders()));

                                exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);

                                exchange.setException(null);
                                callback.done(true);
                                return true;
                            }
                        }
                        if (ex.getOut() != null && Arrays.asList(200, 304).contains(ex.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class))) {
                            exchange.setException(null);
                            break;
                        }
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
                callback.done(true);
                return true;
            }
        };
    }

    private Collection<String> getEndpoints() {
        return Arrays.stream(peers.split(","))
                .map(peer -> "http://" + peer + "/?bridgeEndpoint=true&throwExceptionOnFailure=true&urlRewrite=#rewriter")
                .collect(Collectors.toCollection(LinkedHashSet::new)); // preserve order of 'peers'
    }

}
