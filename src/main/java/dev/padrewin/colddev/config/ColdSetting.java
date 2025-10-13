package dev.padrewin.colddev.config;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.utils.ColdDevUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public interface ColdSetting<T> {

    /**
     * @return the configuration key of this setting
     */
    String getKey();

    /**
     * @return the serializer for reading/writing values to/from the config
     */
    ColdSettingSerializer<T> getSerializer();

    /**
     * @return the default value of this setting
     */
    T getDefaultValue();

    /**
     * @return the comments detailing this setting
     */
    String[] getComments();

    /**
     * @return the value of this setting from the config.yml
     * @throws UnsupportedOperationException if this setting is not backed by a config
     */
    default T get() {
        throw new UnsupportedOperationException("get() is not supported for this setting, missing backing config");
    }

    /**
     * @return true if this setting is backed by a config and {@link #get()} can be called.
     */
    default boolean isBacked() {
        return false;
    }

    /**
     * Sets a new value into the config for this setting.
     * Only supported for backed settings.
     *
     * @param value the new value to set
     * @throws UnsupportedOperationException if the setting is not backed
     */
    default void set(T value) {
        throw new UnsupportedOperationException("set() is not supported for this setting, missing backing config");
    }

    default void writeDefault(CommentedConfigurationSection config, boolean writeDefaultValueComment) {
        if (!writeDefaultValueComment) {
            this.getSerializer().write(config, this, this.getDefaultValue());
            return;
        }

        T defaultValue = this.getDefaultValue();
        List<String> comments = new ArrayList<>(Arrays.asList(this.getComments()));

        // Caută în comentarii o valoare specială pentru default
        String customDefaultComment = "";
        for (String comment : comments) {
            if (comment.startsWith("defaultCommentValue:")) {
                customDefaultComment = comment.substring("defaultCommentValue:".length()).trim();
                break;
            }
        }

        // Adaugă linia "Default: ..." doar dacă am găsit o valoare personalizată
        if (!customDefaultComment.isEmpty()) {
            comments.add("Default: " + customDefaultComment);
        }

        String[] commentsArray = comments.toArray(new String[0]);
        this.getSerializer().write(config, this.getKey(), this.getDefaultValue(), commentsArray);
    }


    static <T> ColdSetting<T> of(String key, ColdSettingSerializer<T> serializer, T defaultValue, String... comments) {
        return of(key, serializer, (Supplier<T>) () -> defaultValue, comments);
    }

    static <T> ColdSetting<T> of(String key, ColdSettingSerializer<T> serializer, Supplier<T> defaultValueSupplier, String... comments) {
        return new BasicColdSetting<>(key, serializer, defaultValueSupplier, comments);
    }

    static ColdSetting<CommentedConfigurationSection> ofSection(String key, String... comments) {
        return new BasicColdSetting<>(key, ColdSettingSerializers.SECTION, (CommentedConfigurationSection) null, comments);
    }

    static <T> ColdSetting<T> backed(ColdPlugin coldPlugin, String key, ColdSettingSerializer<T> serializer, T defaultValue, String... comments) {
        return backed(coldPlugin, key, serializer, (Supplier<T>) () -> defaultValue, comments);
    }

    static <T> ColdSetting<T> backed(ColdPlugin coldPlugin, String key, ColdSettingSerializer<T> serializer, Supplier<T> defaultValueSupplier, String... comments) {
        return new BackedColdSetting<>(coldPlugin, key, serializer, defaultValueSupplier, comments);
    }

    static ColdSetting<CommentedConfigurationSection> backedSection(ColdPlugin coldPlugin, String key, String... comments) {
        return new BackedColdSetting<>(coldPlugin, key, ColdSettingSerializers.SECTION, (CommentedConfigurationSection) null, comments);
    }

}
