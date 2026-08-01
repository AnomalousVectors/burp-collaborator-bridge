package ai.anomalousvectors.tools.burp.utils;

/**
 * Centralized version accessor.
 *
 * <p>Production reads {@code Implementation-Version} from the JAR manifest. Tests or unpackaged
 * runs may set {@code -Dburp.collaborator.bridge.version=...}.</p>
 */
public final class Version {

    private static final String OVERRIDE_PROPERTY = "burp.collaborator.bridge.version";

    private Version() {
    }

    /**
     * Returns the extension version string.
     *
     * @return version from system property, JAR manifest, or {@code "dev"} as a last resort
     */
    public static String get() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        Package p = Version.class.getPackage();
        String mv = (p != null) ? p.getImplementationVersion() : null;
        if (mv != null && !mv.isBlank()) {
            return mv;
        }
        return "dev";
    }
}
