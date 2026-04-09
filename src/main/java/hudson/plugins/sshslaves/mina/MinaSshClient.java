/*
 * The MIT License
 *
 * Copyright (c) 2004-, all the contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.sshslaves.mina;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.DelegatingServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.channel.RequestHandler;
import org.apache.sshd.common.global.KeepAliveHandler;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;

/**
 * Singleton manager for the shared Apache Mina SSHD {@link SshClient}.
 *
 * <p>Per Apache Mina best practices, a single {@link SshClient} instance is reused across all
 * connections to avoid excessive resource allocation (thread pools, NIO selectors, etc.).
 *
 * <p>A {@link SessionListener} is used to configure per-session host key algorithm preferences
 * based on attributes passed through the connection context via {@link #PREFERRED_ALGORITHMS_KEY}.
 */
final class MinaSshClient {

    private static final Logger LOGGER = Logger.getLogger(MinaSshClient.class.getName());

    /**
     * Attribute key for passing preferred host key algorithms to a session.
     * The value should be a {@code List<String>} of algorithm names (e.g., "ssh-rsa", "ssh-ed25519").
     */
    static final AttributeRepository.AttributeKey<List<String>> PREFERRED_ALGORITHMS_KEY =
            new AttributeRepository.AttributeKey<>();

    private static SshClient client;

    /**
     * Returns the shared SSH client, creating it on first use.
     *
     * <p>The client uses a {@link SessionListener} to apply per-session algorithm preferences
     * based on attributes passed through the connection context.
     */
    static synchronized SshClient getClient() {
        if (client != null) {
            return client;
        }
        client = ClientBuilder.builder()
                .serverKeyVerifier(new DelegatingServerKeyVerifier())
                .build(true);

        // Add session listener for per-session algorithm configuration
        client.addSessionListener(new SessionListener() {
            @Override
            public void sessionNegotiationOptionsCreated(Session session, Map<KexProposalOption, String> proposal) {
                List<String> preferredAlgorithms = getPreferredAlgorithms(session);

                if (!preferredAlgorithms.isEmpty()) {
                    String currentAlgorithms = proposal.get(KexProposalOption.SERVERKEYS);
                    if (currentAlgorithms != null && !currentAlgorithms.isEmpty()) {
                        String reordered = reorderAlgorithms(currentAlgorithms, preferredAlgorithms);
                        proposal.put(KexProposalOption.SERVERKEYS, reordered);
                        LOGGER.log(Level.FINE, () -> "Reordered host key algorithms for session: " + reordered);
                    }
                }
            }

            private List<String> getPreferredAlgorithms(Session session) {
                // Try direct session attribute first
                List<String> result = session.getAttribute(PREFERRED_ALGORITHMS_KEY);
                if (result != null) {
                    return result;
                }

                // Try via connection context (for attributes passed through connect())
                if (session instanceof ClientSession clientSession) {
                    AttributeRepository context = clientSession.getConnectionContext();
                    if (context != null) {
                        result = context.getAttribute(PREFERRED_ALGORITHMS_KEY);
                        if (result != null) {
                            return result;
                        }
                    }
                }
                return Collections.emptyList();
            }
        });

        // Add keep-alive handler
        List<RequestHandler<ConnectionService>> requestHandlers = client.getGlobalRequestHandlers();
        requestHandlers = (requestHandlers == null) ? new ArrayList<>() : new ArrayList<>(requestHandlers);
        boolean found = false;
        for (RequestHandler<ConnectionService> handler : requestHandlers) {
            if (handler instanceof KeepAliveHandler) {
                found = true;
                break;
            }
        }
        if (!found) {
            requestHandlers.add(new KeepAliveHandler());
            client.setGlobalRequestHandlers(requestHandlers);
        }

        client.start();
        LOGGER.fine("Mina SshClient started");
        return client;
    }

    /**
     * Reorders the algorithm list to prioritize the preferred algorithms.
     *
     * @param currentAlgorithms comma-separated list of current algorithms
     * @param preferredAlgorithms list of algorithms to prioritize
     * @return reordered comma-separated algorithm list
     */
    static String reorderAlgorithms(String currentAlgorithms, List<String> preferredAlgorithms) {
        List<String> algorithms = new ArrayList<>(Arrays.asList(currentAlgorithms.split(",")));
        List<String> preferred = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for (String algo : algorithms) {
            String trimmed = algo.trim();
            boolean isPreferred = preferredAlgorithms.stream().anyMatch(pref -> trimmed.toLowerCase()
                    .contains(pref.toLowerCase().replace("ssh-", "")));
            if (isPreferred) {
                preferred.add(trimmed);
            } else {
                others.add(trimmed);
            }
        }
        preferred.addAll(others);
        LOGGER.log(
                Level.FINE,
                () -> "Preferred host key algorithms: " + preferred + ", others: " + others + ", current: "
                        + currentAlgorithms);
        return String.join(",", preferred);
    }

    /**
     * Stops the shared SSH client. Called during plugin shutdown.
     */
    static synchronized void stop() {
        if (client != null) {
            try {
                client.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping Mina SshClient", e);
            }
            client = null;
        }
    }

    private MinaSshClient() {}
}
