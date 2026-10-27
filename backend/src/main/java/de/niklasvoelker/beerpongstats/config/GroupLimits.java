package de.niklasvoelker.beerpongstats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Obergrenzen gegen Missbrauch, einstellbar über beerpong.limits.* in application.properties. */
@ConfigurationProperties(prefix = "beerpong.limits")
public record GroupLimits(
        @DefaultValue("20") int maxGroupsPerUser,
        @DefaultValue("100") int maxMembersPerGroup
) {
}
