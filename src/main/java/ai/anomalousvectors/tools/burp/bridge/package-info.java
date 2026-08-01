/**
 * Local HTTP bridge that exposes Burp Collaborator to external tools.
 *
 * <p>Layers: {@code http} (transport), {@code json} (codec), {@code collaborator} (domain service),
 * and {@link ai.anomalousvectors.tools.burp.bridge.HttpBridgeServer} (socket orchestration).</p>
 */
package ai.anomalousvectors.tools.burp.bridge;
