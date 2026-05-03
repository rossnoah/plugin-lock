package dev.noah.pluginlock.core.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginCoordinateTest {
    @Test
    void parsesProviderAndVersionShorthand() {
        PluginCoordinate coordinate = PluginCoordinate.parse("hangar:PlaceholderAPI@2.11.6", "auto", "latest");

        assertEquals("hangar", coordinate.provider());
        assertEquals("PlaceholderAPI", coordinate.id());
        assertEquals("2.11.6", coordinate.version());
    }

    @Test
    void usesDefaultsWhenProviderAndVersionAreOmitted() {
        PluginCoordinate coordinate = PluginCoordinate.parse("luckperms", "auto", "latest");

        assertEquals("auto", coordinate.provider());
        assertEquals("luckperms", coordinate.id());
        assertEquals("latest", coordinate.version());
    }
}
