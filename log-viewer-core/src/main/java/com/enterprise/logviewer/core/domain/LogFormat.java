package com.enterprise.logviewer.core.domain;

/**
 * Enumeration of all supported log formats.
 * Used for format detection and parser selection.
 */
public enum LogFormat {
    // Structured formats
    JSON("JSON", "application/json"),
    XML("XML", "application/xml"),
    YAML("YAML", "application/yaml"),
    CSV("CSV", "text/csv"),
    AVRO("Apache Avro", "application/avro"),

    // Traditional Java logging
    LOG4J("Log4j", "text/plain"),
    LOG4J2("Log4j2", "text/plain"),
    LOGBACK("Logback", "text/plain"),
    JUL("Java Util Logging", "text/plain"),
    SLF4J("SLF4J", "text/plain"),

    // Application servers
    APACHE_ACCESS("Apache Access Log", "text/plain"),
    APACHE_ERROR("Apache Error Log", "text/plain"),
    NGINX_ACCESS("Nginx Access Log", "text/plain"),
    NGINX_ERROR("Nginx Error Log", "text/plain"),
    TOMCAT("Tomcat", "text/plain"),
    JBOSS("JBoss", "text/plain"),
    WEBLOGIC("WebLogic", "text/plain"),
    SPRING_BOOT("Spring Boot", "text/plain"),

    // System logs
    SYSLOG("Syslog (RFC 5424)", "text/plain"),
    SYSLOG_RFC3164("Syslog (RFC 3164)", "text/plain"),
    WINDOWS_EVENT("Windows Event Log", "text/plain"),
    LINUX_KERNEL("Linux Kernel Log", "text/plain"),
    SYSTEMD_JOURNAL("systemd Journal", "text/plain"),

    // Cloud platforms
    AWS_CLOUDWATCH("AWS CloudWatch", "application/json"),
    AZURE_MONITOR("Azure Monitor", "application/json"),
    GCP_CLOUD_LOGGING("GCP Cloud Logging", "application/json"),
    KUBERNETES_POD("Kubernetes Pod Log", "text/plain"),

    // Application-specific
    DOCKER("Docker Container", "application/json"),
    POSTGRESQL("PostgreSQL", "text/plain"),
    MYSQL("MySQL", "text/plain"),
    MONGODB("MongoDB", "application/json"),
    ELASTICSEARCH("Elasticsearch", "application/json"),
    KAFKA("Apache Kafka", "text/plain"),
    PROMETHEUS("Prometheus", "text/plain"),

    // Catch-all
    LOGSTASH_JSON("Logstash JSON", "application/json"),
    CUSTOM_REGEX("Custom (Regex)", "text/plain"),
    UNKNOWN("Unknown", "text/plain");

    private final String displayName;
    private final String mimeType;

    LogFormat(String displayName, String mimeType) {
        this.displayName = displayName;
        this.mimeType = mimeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isStructured() {
        return switch (this) {
            case JSON, XML, YAML, CSV, AVRO, LOGSTASH_JSON,
                 AWS_CLOUDWATCH, AZURE_MONITOR, GCP_CLOUD_LOGGING,
                 DOCKER, MONGODB, ELASTICSEARCH -> true;
            default -> false;
        };
    }

    public boolean isCloudFormat() {
        return switch (this) {
            case AWS_CLOUDWATCH, AZURE_MONITOR, GCP_CLOUD_LOGGING, KUBERNETES_POD -> true;
            default -> false;
        };
    }
}
