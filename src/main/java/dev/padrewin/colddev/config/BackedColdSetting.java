package dev.padrewin.colddev.config;

import dev.padrewin.colddev.ColdPlugin;
import java.util.function.Supplier;

/* package */ class BackedColdSetting<T> extends BasicColdSetting<T> {

    private final ColdPlugin backing;

    public BackedColdSetting(ColdPlugin backing, String key, ColdSettingSerializer<T> serializer, T defaultValue, String... comments) {
        this(backing, key, serializer, () -> defaultValue, comments);
    }

    public BackedColdSetting(ColdPlugin backing, String key, ColdSettingSerializer<T> serializer, Supplier<T> defaultValueSupplier, String... comments) {
        super(key, serializer, defaultValueSupplier, comments);
        this.backing = backing;
    }

    @Override
    public T get() {
        return this.backing.getColdConfig().get(this);
    }

    @Override
    public boolean isBacked() {
        return true;
    }

    @Override
    public void set(T value) {
        this.backing.getColdConfig().set(this, value);
        this.backing.getColdConfig().save();
    }

}
