package ai.anomalousvectors.tools.burp.bridge;

import ai.anomalousvectors.tools.burp.bridge.collaborator.CollaboratorBridgeService;
import ai.anomalousvectors.tools.burp.bridge.http.HttpJsonWriter;
import ai.anomalousvectors.tools.burp.bridge.http.HttpRequest;
import ai.anomalousvectors.tools.burp.bridge.http.HttpRequestParser;
import ai.anomalousvectors.tools.burp.bridge.json.JsonSupport;
import ai.anomalousvectors.tools.burp.utils.Logger;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny HTTP/1.1 bridge exposing Burp Collaborator via JSON.
 *
 * <p><strong>Endpoints</strong></p>
 * <ul>
 *   <li>{@code GET /health}</li>
 *   <li>{@code GET|POST /payload} — create a payload; supports {@code custom} (alnum ≤16) and
 *       {@code without_server=1}</li>
 *   <li>{@code GET /interactions} — return all interactions seen by this bridge instance
 *       (append-only retention). Each item includes {@code "new":true|false} to indicate whether
 *       it arrived in the current request.</li>
 * </ul>
 *
 * <p><strong>Threading</strong></p>
 * Single instance guarded by {@code stateLock}. Per-connection handling uses a worker thread.
 *
 * <p><strong>Failure model</strong></p>
 * I/O/parse errors return 4xx/5xx. If Collaborator is disabled, endpoints return 503 with
 * {@code error:'collaborator_disabled'}. JSON bodies always end with a single LF.
 */
public final class HttpBridgeServer {

    private static final String ERR_DISABLED = "collaborator_disabled";
    private static final int ACCEPT_SO_TIMEOUT_MS = 0;
    private static final int SOCKET_SO_TIMEOUT_MS = 15_000;

    private final MontoyaApi api;
    private final String bindHost;
    private final int bindPort;
    private final CollaboratorBridgeService collaboratorService = new CollaboratorBridgeService();

    private final Object stateLock = new Object();
    private volatile boolean running;
    private CollaboratorClient client;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Thread acceptThread;

    /**
     * Creates a bridge bound to the provided host/port.
     *
     * @param api Montoya API
     * @param bindHost bind host
     * @param bindPort bind port
     */
    public HttpBridgeServer(MontoyaApi api, String bindHost, int bindPort) {
        this.api = api;
        this.bindHost = bindHost;
        this.bindPort = bindPort;
    }

    /**
     * Starts the server: create a Collaborator client, bind the socket, and spawn the accept loop.
     *
     * @throws IOException if the listen socket cannot be bound
     */
    public void start() throws IOException {
        synchronized (stateLock) {
            if (running) {
                return;
            }

            Logger.logInfo("Runtime: java.version=" + System.getProperty("java.version")
                    + " java.vendor=" + System.getProperty("java.vendor")
                    + " os.name=" + System.getProperty("os.name")
                    + " os.arch=" + System.getProperty("os.arch"));

            Logger.logInfo("Creating Collaborator client ...");
            this.client = api.collaborator().createClient();
            Logger.logInfo("Collaborator client created.");

            preflightBind(bindHost, bindPort);

            Logger.logInfo("Opening ServerSocket on " + httpUrlForLog(bindHost, bindPort) + " ...");
            this.serverSocket = new ServerSocket();
            this.serverSocket.setReuseAddress(true);
            this.serverSocket.bind(new InetSocketAddress(InetAddress.getByName(bindHost), bindPort));
            this.serverSocket.setSoTimeout(ACCEPT_SO_TIMEOUT_MS);

            this.executor = Executors.newFixedThreadPool(8);
            this.running = true;

            this.acceptThread = new Thread(this::acceptLoop, "collab-bridge-accept");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();

            Logger.logInfo("Listening on " + httpUrlForLog(bindHost, bindPort));
        }
    }

    /** Stops the server and worker pool; safe to call multiple times. */
    public void stop() {
        synchronized (stateLock) {
            running = false;
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Logger.logError("ServerSocket close error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            try {
                if (executor != null) {
                    executor.shutdownNow();
                }
            } catch (RuntimeException e) {
                Logger.logError("Executor shutdown error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            serverSocket = null;
            executor = null;
            client = null;
            acceptThread = null;
            Logger.logInfo("HTTP server stopped.");
        }
    }

    /**
     * @return whether the accept loop is running
     */
    public boolean isRunning() {
        synchronized (stateLock) {
            return running;
        }
    }

    /**
     * @return configured bind host
     */
    public String bindHost() {
        return bindHost;
    }

    /**
     * @return configured bind port
     */
    public int bindPort() {
        return bindPort;
    }

    private void acceptLoop() {
        Logger.logInfo("Accept loop started.");
        while (running) {
            try {
                final Socket s = serverSocket.accept();
                s.setSoTimeout(SOCKET_SO_TIMEOUT_MS);
                executor.submit(() -> handleClient(s));
            } catch (SocketException se) {
                if (running) {
                    Logger.logError("Accept SocketException: " + se.getMessage());
                }
                break;
            } catch (IOException ioe) {
                if (running) {
                    Logger.logError("Accept IOException: " + ioe.getMessage());
                }
            } catch (RuntimeException e) {
                if (running) {
                    Logger.logError("Accept unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
        Logger.logInfo("Accept loop exiting.");
    }

    private void handleClient(Socket socket) {
        try (Socket s = socket;
                InputStream rawIn = new BufferedInputStream(s.getInputStream());
                OutputStream rawOut = new BufferedOutputStream(s.getOutputStream())) {

            HttpRequest req = HttpRequestParser.parse(rawIn);
            if (req == null) {
                return;
            }

            switch (req.path()) {
                case "/health" -> HttpJsonWriter.writeJson(rawOut, 200, "{\"status\":\"ok\"}");
                case "/payload" -> handlePayload(req, rawOut);
                case "/interactions" -> handleInteractions(req, rawOut);
                default -> HttpJsonWriter.writeJson(rawOut, 404, JsonSupport.errorJson("not_found"));
            }
        } catch (IOException | RuntimeException e) {
            Logger.logError("Client handler error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void handlePayload(HttpRequest req, OutputStream out) throws IOException {
        final CollaboratorClient c;
        synchronized (stateLock) {
            c = client;
        }
        if (c == null) {
            HttpJsonWriter.writeJson(out, 503, JsonSupport.errorJson(ERR_DISABLED));
            return;
        }

        try {
            Map<String, String> q = new HashMap<>(req.query());
            if ("POST".equals(req.method())) {
                q.putAll(JsonSupport.parseJsonObjectFlat(req.body()));
            }

            String custom = JsonSupport.trimToEmpty(q.get("custom"));
            boolean withoutServer = "1".equals(q.get("without_server"));

            CollaboratorPayload payload = collaboratorService.createPayload(c, custom, withoutServer);
            HttpJsonWriter.writeJson(out, 200, collaboratorService.toPayloadJson(payload));
        } catch (IllegalStateException _) {
            HttpJsonWriter.writeJson(out, 503, JsonSupport.errorJson(ERR_DISABLED));
        } catch (IllegalArgumentException bad) {
            HttpJsonWriter.writeJson(
                    out,
                    400,
                    JsonSupport.errorJson("invalid_custom".equals(bad.getMessage()) ? "invalid_custom" : "bad_request"));
        } catch (RuntimeException e) {
            Logger.logError("payload handler error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            HttpJsonWriter.writeJson(out, 500, JsonSupport.errorJson("server_error"));
        }
    }

    private void handleInteractions(HttpRequest req, OutputStream out) throws IOException {
        final CollaboratorClient c;
        synchronized (stateLock) {
            c = client;
        }
        if (c == null) {
            HttpJsonWriter.writeJson(out, 503, JsonSupport.errorJson(ERR_DISABLED));
            return;
        }

        try {
            CollaboratorBridgeService.InteractionQuery query =
                    collaboratorService.parseInteractionQuery(req.query());
            if (query.invalidSince()) {
                HttpJsonWriter.writeJson(out, 400, JsonSupport.errorJson("invalid_since"));
                return;
            }

            long currentSeq = collaboratorService.pollAndRetain(c);
            HttpJsonWriter.writeJson(out, 200, collaboratorService.listInteractionsJson(currentSeq));
        } catch (IllegalStateException _) {
            HttpJsonWriter.writeJson(out, 503, JsonSupport.errorJson(ERR_DISABLED));
        } catch (RuntimeException e) {
            Logger.logError("interactions handler error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            HttpJsonWriter.writeJson(out, 500, JsonSupport.errorJson("server_error"));
        }
    }

    private static void preflightBind(String host, int port) {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName(host), port));
            Logger.logInfo("Preflight bind via ServerSocket SUCCEEDED for " + host + ":" + port + " (closing socket).");
        } catch (IOException | RuntimeException e) {
            Logger.logError("Preflight bind via ServerSocket FAILED (" + e.getClass().getSimpleName() + "): "
                    + e.getMessage());
        }
    }

    private static String httpUrlForLog(String host, int port) {
        return "http://" + host + ":" + port;
    }
}
