package dev.padrewin.colddev.utils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable class that holds a map of placeholders and their values.
 * Compatible with both old (1.8–1.12) and new (1.17+) Minecraft servers.
 */
public final class StringPlaceholders {

    private static final StringPlaceholders EMPTY = new StringPlaceholders(Collections.emptyMap(), "%", "%");
    private static final LoadingCache<String, Pattern> PATTERN_CACHE;

    static {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .concurrencyLevel(2);

        try {
            // Check if new Guava method exists (Java 17+)
            builder.getClass().getMethod("expireAfterAccess", Duration.class);
            builder = builder.expireAfterAccess(Duration.ofMinutes(1));
        } catch (NoSuchMethodException e) {
            // Fallback for old Spigot versions (1.8–1.12)
            builder = builder.expireAfterAccess(1, TimeUnit.MINUTES);
        }

        PATTERN_CACHE = builder.build(new CacheLoader<String, Pattern>() {
            @Override
            public Pattern load(String key) {
                return Pattern.compile(key);
            }
        });
    }

    private final String startDelimiter;
    private final String endDelimiter;
    private final Map<String, String> placeholders;

    private StringPlaceholders(Map<String, String> placeholders, String startDelimiter, String endDelimiter) {
        this.placeholders = Collections.unmodifiableMap(placeholders);
        this.startDelimiter = startDelimiter;
        this.endDelimiter = endDelimiter;
    }

    /**
     * Applies the placeholders to the given string.
     *
     * @param string the string to apply the placeholders to
     * @return the string with the placeholders replaced
     */
    public String apply(String string) {
        for (String key : this.placeholders.keySet()) {
            String patternKey = Pattern.quote(this.startDelimiter + key + this.endDelimiter);
            Pattern pattern = PATTERN_CACHE.getUnchecked(patternKey);
            string = pattern.matcher(string).replaceAll(Matcher.quoteReplacement(this.placeholders.get(key)));
        }
        return string;
    }

    public Map<String, String> getPlaceholders() {
        return this.placeholders;
    }

    public String getStartDelimiter() {
        return this.startDelimiter;
    }

    public String getEndDelimiter() {
        return this.endDelimiter;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String placeholder, Object value) {
        return new Builder().add(placeholder, value);
    }

    public static StringPlaceholders empty() {
        return EMPTY;
    }

    public static StringPlaceholders of(String placeholder, Object value) {
        return builder(placeholder, value).build();
    }

    public static StringPlaceholders of(String p1, Object v1,
                                        String p2, Object v2) {
        return builder(p1, v1).add(p2, v2).build();
    }

    public static StringPlaceholders of(String p1, Object v1,
                                        String p2, Object v2,
                                        String p3, Object v3) {
        return builder(p1, v1).add(p2, v2).add(p3, v3).build();
    }

    public static StringPlaceholders of(String p1, Object v1,
                                        String p2, Object v2,
                                        String p3, Object v3,
                                        String p4, Object v4) {
        return builder(p1, v1).add(p2, v2).add(p3, v3).add(p4, v4).build();
    }

    public static StringPlaceholders of(String p1, Object v1,
                                        String p2, Object v2,
                                        String p3, Object v3,
                                        String p4, Object v4,
                                        String p5, Object v5) {
        return builder(p1, v1)
                .add(p2, v2)
                .add(p3, v3)
                .add(p4, v4)
                .add(p5, v5)
                .build();
    }

    public static class Builder {
        private String startDelimiter = "%";
        private String endDelimiter = "%";
        private final Map<String, String> placeholders = new HashMap<>();

        public Builder add(String placeholder, Object value) {
            this.placeholders.put(placeholder, Objects.toString(value, "null"));
            return this;
        }

        public Builder addSanitized(String placeholder, Object value) {
            this.placeholders.put(placeholder, Objects.toString(value, "null").replace("%", ""));
            return this;
        }

        public Builder delimiters(String startDelimiter, String endDelimiter) {
            this.startDelimiter = startDelimiter;
            this.endDelimiter = endDelimiter;
            return this;
        }

        public Builder addAll(StringPlaceholders placeholders) {
            return this.addAll(placeholders.getPlaceholders());
        }

        public Builder addAll(Map<String, String> placeholders) {
            this.placeholders.putAll(placeholders);
            return this;
        }

        public StringPlaceholders build() {
            return new StringPlaceholders(this.placeholders, this.startDelimiter, this.endDelimiter);
        }
    }
}
