package ai.anomalousvectors.tools.burp.utils;

/**
 * Centralizes public product labels and links shown in Burp UI.
 */
public final class ProductInfo {

    /** Burp extension name registered with Montoya. */
    public static final String EXTENSION_NAME = "Collaborator Bridge";

    /** Suite tab title shown in Burp. */
    public static final String SUITE_TAB_TITLE = "Collaborator Bridge";

    /** Label for the extension repository link. */
    public static final String REPOSITORY_LABEL = "Repository:";

    /** Public extension repository URL. */
    public static final String REPOSITORY_URL =
            "https://github.com/AnomalousVectors/burp-collaborator-bridge";

    private ProductInfo() {
    }
}
