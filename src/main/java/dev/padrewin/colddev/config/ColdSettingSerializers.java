package dev.padrewin.colddev.config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bukkit.Material;

public final class ColdSettingSerializers {

    private ColdSettingSerializers() { }

    //region Primitive Serializers
    public static final ColdSettingSerializer<Boolean> BOOLEAN = CommentedConfigurationSection::getBoolean;
    public static final ColdSettingSerializer<Integer> INTEGER = CommentedConfigurationSection::getInt;
    public static final ColdSettingSerializer<Long> LONG = CommentedConfigurationSection::getLong;
    public static final ColdSettingSerializer<Short> SHORT = (config, setting) -> (short) config.getInt(setting);
    public static final ColdSettingSerializer<Byte> BYTE = (config, setting) -> (byte) config.getInt(setting);
    public static final ColdSettingSerializer<Double> DOUBLE = CommentedConfigurationSection::getDouble;
    public static final ColdSettingSerializer<Float> FLOAT = (config, setting) -> (float) config.getDouble(setting);
    public static final ColdSettingSerializer<Character> CHAR = (config, setting) -> {
        String value = config.getString(setting);
        if (value == null || value.isEmpty())
            return ' ';
        return value.charAt(0);
    };
    //endregion

    //region Other Serializers
    public static final ColdSettingSerializer<CommentedConfigurationSection> SECTION = new ColdSettingSerializer<CommentedConfigurationSection>() {
        @Override
        public CommentedConfigurationSection read(CommentedConfigurationSection config, String setting) {
            return config.getConfigurationSection(setting);
        }

        @Override
        public void write(CommentedConfigurationSection config, String key, CommentedConfigurationSection value, String... comments) {
            config.addPathedComments(key, comments);
        }
    };
    public static final ColdSettingSerializer<String> STRING = CommentedConfigurationSection::getString;
    public static final ColdSettingSerializer<Material> MATERIAL = createMapped(STRING, Material::name, Material::matchMaterial);
    //endregion

    //region Primitive List Serializers
    public static final ColdSettingSerializer<List<Boolean>> BOOLEAN_LIST = CommentedConfigurationSection::getBooleanList;
    public static final ColdSettingSerializer<List<Long>> LONG_LIST = CommentedConfigurationSection::getLongList;
    public static final ColdSettingSerializer<List<Short>> SHORT_LIST = CommentedConfigurationSection::getShortList;
    public static final ColdSettingSerializer<List<Byte>> BYTE_LIST = CommentedConfigurationSection::getByteList;
    public static final ColdSettingSerializer<List<Double>> DOUBLE_LIST = CommentedConfigurationSection::getDoubleList;
    public static final ColdSettingSerializer<List<Float>> FLOAT_LIST = CommentedConfigurationSection::getFloatList;
    public static final ColdSettingSerializer<List<Character>> CHAR_LIST = CommentedConfigurationSection::getCharacterList;
    //endregion

    //region Other List Serializers
    public static final ColdSettingSerializer<List<String>> STRING_LIST = CommentedConfigurationSection::getStringList;
    public static final ColdSettingSerializer<List<Material>> MATERIAL_LIST = createMapped(STRING_LIST,
            x -> x.stream().map(Material::name).collect(Collectors.toList()),
            x -> x.stream().map(Material::matchMaterial).filter(Objects::nonNull).collect(Collectors.toList()));
    //endregion

    //region Helpers
    public static <T, M> ColdSettingSerializer<T> createMapped(ColdSettingSerializer<M> baseSerializer, Function<T, M> toMapped, Function<M, T> fromMapped) {
        return new ColdSettingSerializer<T>() {
            @Override
            public T read(CommentedConfigurationSection config, String setting) {
                M value = baseSerializer.read(config, setting);
                if (value == null)
                    return null;
                return fromMapped.apply(value);
            }

            @Override
            public void write(CommentedConfigurationSection config, String key, T value, String... comments) {
                M toWrite = toMapped.apply(value);
                if (toWrite != null) {
                    if (config instanceof CommentedConfigurationSection) {
                        ((CommentedConfigurationSection) config).set(key, toWrite, comments);
                    } else {
                        config.set(key, toWrite);
                    }
                }
            }

        };
    }

    public static <T> ColdSettingSerializer<List<T>> list(ColdSettingSerializer<T> elementSerializer) {
        return new ColdSettingSerializer<List<T>>() {
            @Override
            public List<T> read(CommentedConfigurationSection config, String setting) {
                List<?> rawList = config.getList(setting);
                if (rawList == null) {
                    return Collections.emptyList();
                }
                return rawList.stream()
                        .map(element -> elementSerializer.read(config, setting + "." + rawList.indexOf(element)))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            @Override
            public void write(CommentedConfigurationSection config, String key, List<T> value, String... comments) {
                config.addPathedComments(key, comments); // Adaugă comentarii pentru secțiune

                // Dacă lista este null sau goală, scrie-o explicit ca o listă YAML
                if (value == null || value.isEmpty()) {
                    config.set(key, Collections.emptyList());
                } else {
                    config.set(key, value);
                }
            }
        };
    }

    //endregion
}
