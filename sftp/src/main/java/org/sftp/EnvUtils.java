package org.sftp;

public class EnvUtils {
    public static boolean isProduction() {
        String env = System.getenv("APP_ENV");
        return "production".equalsIgnoreCase(env);
    }

    public static boolean isLocal() {
        String env = System.getenv("APP_ENV");
        return "local".equalsIgnoreCase(env);
    }
}

